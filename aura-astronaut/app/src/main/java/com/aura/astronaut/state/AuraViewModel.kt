package com.aura.astronaut.state

import com.aura.astronaut.i18n.AppLanguage
import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import com.aura.astronaut.assessment.scenarioOf
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.astronaut.BuildConfig
import com.aura.astronaut.assessment.AdaptationEngine
import com.aura.astronaut.assessment.Assessment
import com.aura.astronaut.assessment.CapacityEvaluator
import com.aura.astronaut.assessment.DemoScenario
import com.aura.astronaut.assessment.QuestionnaireSelector
import com.aura.astronaut.assessment.TransitionReadiness
import com.aura.astronaut.assessment.AlertRules
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.data.LocalAlert
import com.aura.astronaut.mission.wire
import com.aura.astronaut.sync.AdaptationSync
import com.aura.astronaut.sync.EvidenceDto
import com.aura.astronaut.sync.ManagerClient
import com.aura.astronaut.sync.PackageCheck
import com.aura.astronaut.sync.PackageDecision
import com.aura.astronaut.sync.PackageOrigin
import com.aura.astronaut.sync.PackageUpdate
import com.aura.astronaut.sync.SyncStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import com.aura.astronaut.assessment.SelectedQuestion
import com.aura.astronaut.data.ActivityConfirmation
import com.aura.astronaut.data.AuraDatabase
import com.aura.astronaut.data.ConfirmationStatus
import com.aura.astronaut.data.Observation
import com.aura.astronaut.data.Origin
import com.aura.astronaut.heart.BleHeartRate
import com.aura.astronaut.heart.FoundWatch
import com.aura.astronaut.heart.HeartRules
import com.aura.astronaut.heart.HeartSource
import com.aura.astronaut.heart.HeartState
import com.aura.astronaut.heart.SimulatedHeartRate
import com.aura.astronaut.hud.Announcement
import com.aura.astronaut.motion.MotionSource
import com.aura.astronaut.mission.FlightDay
import com.aura.astronaut.mission.Lang
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.PlanActivity
import com.aura.astronaut.hud.MissionSnapshot
import com.aura.astronaut.voice.Speaker
import com.aura.astronaut.voice.VoiceStatus
import com.aura.astronaut.mission.ClockSettings
import com.aura.astronaut.mission.ClockSource
import com.aura.astronaut.mission.LoadedPackage
import com.aura.astronaut.mission.MissionClock
import com.aura.astronaut.mission.MissionPackageParser
import com.aura.astronaut.mission.PackageException
import com.aura.astronaut.mission.endMet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PackageState {
    data object Loading : PackageState
    data class Ready(val loaded: LoadedPackage, val origin: PackageOrigin) : PackageState
    data class Failed(val message: String) : PackageState
}

/** Mission state the HUD needs. */
class AuraViewModel(app: Application) : AndroidViewModel(app) {

    private val store = DemoSettingsStore(app)
    private val packageStorage = PackageStorage(app)

    private val _pkg = MutableStateFlow<PackageState>(PackageState.Loading)
    val pkg: StateFlow<PackageState> = _pkg.asStateFlow()

    private val _packageCheck = MutableStateFlow<PackageCheck>(PackageCheck.Idle)
    /** Result of the last check for a new package in AURA Manager. */
    val packageCheck: StateFlow<PackageCheck> = _packageCheck.asStateFlow()
    private val packageLock = Mutex()

    private val _clock = MutableStateFlow(store.loadClock() ?: defaultClock())
    val clock: StateFlow<ClockSettings> = _clock.asStateFlow()

    private val _crewId = MutableStateFlow(store.loadCrewId())

    /** Chosen astronaut, only if they exist in the loaded package; null = must be chosen. */
    val crewId: StateFlow<String?> = combine(_crewId, _pkg) { id, p ->
        val crew = (p as? PackageState.Ready)?.loaded?.pkg?.crew ?: return@combine null
        id?.takeIf { wanted -> crew.any { it.id == wanted } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    /** Current MET in seconds. */
    val met: StateFlow<Long> = combine(ticker, _clock, _pkg) { now, settings, _ ->
        MissionClock.metAt(settings, now, launchUtcMs())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MissionClock.metAt(_clock.value, System.currentTimeMillis(), null),
    )

    // The demonstration MET stops when reaching the end of the mission (splashdown): past that point
    // there is no flight day and the HUD would be left without capacities or transition.
    init {
        viewModelScope.launch {
            met.collect { m ->
                val end = (_pkg.value as? PackageState.Ready)?.loaded?.pkg?.endMet ?: return@collect
                val c = _clock.value
                if (c.source == ClockSource.MANUAL && c.running && m > end) {
                    updateClock { s, now -> s.copy(anchorMet = end, anchorWallMs = now, running = false) }
                }
            }
        }
    }

    private val dao = AuraDatabase.get(app).dao()

    /** Confirmations of the chosen crew member, by entryId. They persist in the local database. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val confirmations: StateFlow<Map<String, ActivityConfirmation>> = crewId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.confirmations(id) }
        .map { list -> list.associateBy { it.entryId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Adaptations of the chosen crew member on the package in force (applied on board), with their sending and review.
     * Mission control changes the plan by publishing a new package: those decided on another version are no longer
     * applied (they remain in the database and in the Manager's record).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val adaptations: StateFlow<List<LocalAdaptation>> = combine(
        crewId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.adaptations(id) },
        _pkg,
    ) { list, p -> inForce(list, (p as? PackageState.Ready)?.loaded?.pkg) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun inForce(list: List<LocalAdaptation>, pkg: MissionPackage?): List<LocalAdaptation> =
        if (pkg == null) emptyList() else list.filter { it.packageVersion == pkg.packageVersion }

    /** Alerts of the chosen crew member for mission control, with their sending and acknowledgement. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val alerts: StateFlow<List<LocalAlert>> = crewId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.alerts(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** All observations of the chosen crew member (questionnaire and signals). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val observations: StateFlow<List<Observation>> = crewId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.allObservations(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Mission situation for the chosen crew member at the current MET; null until there is a package and crew member. */
    val snapshot: StateFlow<MissionSnapshot?> = combine(met, _pkg, crewId, confirmations, adaptations) { m, p, crew, conf, adapt ->
        val pkg = (p as? PackageState.Ready)?.loaded?.pkg
        if (pkg == null || crew == null) null else MissionSnapshot.build(pkg, crew, m, conf, adapt.associateBy { it.entryId })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Today's traffic light per capacity, with the data of the current flight day. */
    val assessment: StateFlow<Assessment?> = combine(
        snapshot.map { it?.day?.day }.distinctUntilChanged(),
        _pkg,
        observations,
    ) { day, p, obs ->
        val pkg = (p as? PackageState.Ready)?.loaded?.pkg
        if (pkg == null || day == null) null else CapacityEvaluator.evaluate(pkg, day, obs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Name of the simulated scenario with data today (to label the traffic light); null if none. */
    val simulatedScenario: StateFlow<DemoScenario?> = combine(snapshot.map { it?.day?.day }.distinctUntilChanged(), observations) { day, obs ->
        // Only the signals of a demo scenario (not the simulated HR samples).
        obs.lastOrNull { it.flightDay == day && it.origin == Origin.SIMULATED && scenarioName(it.quality) != null }
            ?.quality?.let(::scenarioName)?.let(::scenarioOf)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Preparation for the next transition into gravity (step 5b); null if none is announced. */
    val readiness: StateFlow<TransitionReadiness?> = combine(
        snapshot.map { s -> s?.transition?.let { Triple(s.met / 60, s.day, it.day) } }.distinctUntilChanged(),
        observations,
        adaptations,
        confirmations,
    ) { key, obs, adapt, conf ->
        val pkg = readyPackage()
        val crew = crewId.value
        if (key == null || pkg == null || crew == null) return@combine null
        val (minute, day, transitionDay) = key
        TransitionReadiness.build(pkg, crew, minute * 60, day, transitionDay, conf, adapt.associateBy { it.entryId }, obs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val sync = AdaptationSync(dao, store.loadManagerUrl() ?: BuildConfig.MANAGER_URL)
    val syncStatus: StateFlow<SyncStatus> = sync.status
    private val engineLock = Mutex()

    /** Interface language (the activity's): it decides the language of package texts and of the voice. */
    @Volatile private var uiLanguage = AppLanguage.current(app)
    private val speaker = Speaker(app, uiLanguage)
    val voiceStatus: StateFlow<VoiceStatus> = speaker.status
    val voiceMuted: StateFlow<Boolean> = speaker.muted

    init {
        // The default clock is stored right away: this way the MET does not go back to zero when reopening the app.
        store.saveClock(_clock.value)
        viewModelScope.launch {
            _pkg.value = loadInitialPackage()
            // At startup, check whether mission control has published another package; offline, keep this one.
            checkPackageNow()
        }
        // Rules engine: decides when the day, the traffic light, the pending plan or the MET minute change.
        viewModelScope.launch {
            combine(snapshot, assessment) { s, a -> s to a }
                .map { (s, a) -> EngineKey(s, a) }
                .distinctUntilChanged { old, new -> old.key == new.key }
                .collect { runEngine(it.snapshot, it.assessment) }
        }
        // Send queue to mission control: blocks nothing and retries on its own.
        viewModelScope.launch {
            while (true) {
                sync.runOnce(crewId.value)
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private class EngineKey(val snapshot: MissionSnapshot?, val assessment: Assessment?) {
        val key: List<Any?> = listOf(
            snapshot?.day?.day,
            snapshot?.met?.div(60),
            assessment?.capacities?.map { it.level },
            snapshot?.todayPlan?.map { it.activity.entryId to (it.pending && it.adaptation == null) },
        )
    }

    private suspend fun runEngine(s: MissionSnapshot?, a: Assessment?) {
        val pkg = readyPackage() ?: return
        val crew = crewId.value ?: return
        val day = s?.day ?: return
        engineLock.withLock {
            val handled = confirmations.value.keys + inForce(dao.adaptationsNow(crew), pkg).map { it.entryId }
            val decisions = AdaptationEngine.decide(pkg, crew, s.met, day, a, handled)
            decisions.forEach { d ->
                val evidence = d.evidence.map { e -> EvidenceDto(e.indicator, e.value, e.unit, e.source.wire, e.met) }
                val local = LocalAdaptation(
                    id = UUID.randomUUID().toString(),
                    crewId = crew,
                    entryId = d.proposal.entryId,
                    originalItemId = d.originalItemId,
                    packageVersion = pkg.packageVersion,
                    type = d.proposal.type.wire,
                    replacementItemId = d.proposal.replacementItemId,
                    newMet = d.proposal.newMet,
                    decidedAtMet = s.met,
                    reason = d.proposal.reason,
                    evidenceJson = ManagerClient("").evidenceToJson(evidence),
                    rule = d.rule,
                    createdAtUtcMs = System.currentTimeMillis(),
                )
                dao.insertAdaptation(local)
                Log.i(TAG, "Adaptation applied: ${local.type} ${local.entryId} (${d.rule})")
            }
            // Alerts: what is not resolved by adapting. One per situation (id derived from the key).
            val catalog = pkg.catalog.associateBy { it.id }
            val newAlerts = AlertRules.candidates(a?.takeIf { it.flightDay == day.day }, inForce(dao.adaptationsNow(crew), pkg), catalog)
            // One alert per situation: compared by key (ids of old alerts remain valid).
            val known = dao.alertKeys(crew).toSet()
            newAlerts.filter { it.dedupeKey !in known }.forEach { c ->
                dao.insertAlert(
                    LocalAlert(
                        id = UUID.nameUUIDFromBytes("$crew|${c.dedupeKey}".toByteArray()).toString(),
                        crewId = crew,
                        dedupeKey = c.dedupeKey,
                        kind = c.kind,
                        level = c.level,
                        capacityId = c.capacityId,
                        entryId = c.entryId,
                        met = s.met,
                        flightDay = c.flightDay ?: day.day,
                        message = c.message,
                        evidenceJson = c.evidenceJson ?: ManagerClient("").evidenceToJson(
                            c.evidence.map { e -> EvidenceDto(e.indicator, e.value, e.unit, e.source.wire, e.met) },
                        ),
                        packageVersion = pkg.packageVersion,
                        createdAtUtcMs = System.currentTimeMillis(),
                    ),
                )
            }
            if (decisions.isNotEmpty() || newAlerts.isNotEmpty()) viewModelScope.launch { sync.runOnce(crew) }
        }
    }

    // ---------- Demo scenarios and server (step 5) ----------

    /** Loads the simulated signals of a scenario for the current flight day (replaces today's simulated ones). */
    fun applyScenario(scenario: DemoScenario) {
        val crew = crewId.value ?: return
        val pkg = readyPackage() ?: return
        val day = snapshot.value?.day ?: return
        val now = System.currentTimeMillis()
        val atMet = met.value
        val list = scenario.values.mapNotNull { (id, value) ->
            val signal = pkg.signals.firstOrNull { it.id == id } ?: return@mapNotNull null
            Observation(
                crewId = crew,
                kind = Observation.KIND_SIGNAL,
                indicator = id,
                value = value,
                rawValue = null,
                unit = signal.unit,
                variantId = null,
                entryId = null,
                capacityId = signal.capacityId,
                flightDay = day.day,
                met = atMet,
                recordedAtUtcMs = now,
                origin = Origin.SIMULATED,
                quality = SCENARIO_PREFIX + scenario.name,
                packageVersion = pkg.packageVersion,
            )
        }
        viewModelScope.launch {
            dao.deleteSimulated(crew, Observation.KIND_SIGNAL, day.day)
            dao.insertObservations(list)
        }
    }

    /** Deletes today's simulated signals: the traffic light depends only on the questionnaire again. */
    fun clearScenario() {
        val crew = crewId.value ?: return
        val day = snapshot.value?.day ?: return
        viewModelScope.launch { dao.deleteSimulated(crew, Observation.KIND_SIGNAL, day.day) }
    }

    fun setManagerUrl(url: String) {
        store.saveManagerUrl(url)
        sync.setUrl(url)
        viewModelScope.launch { sync.runOnce(crewId.value) }
        checkPackage()
    }

    /** Looks in AURA Manager for a published package different from the loaded one and, if valid, loads it. */
    fun checkPackage() {
        viewModelScope.launch { checkPackageNow() }
    }

    private suspend fun checkPackageNow() = packageLock.withLock {
        val url = store.loadManagerUrl() ?: BuildConfig.MANAGER_URL
        _packageCheck.value = PackageCheck.Checking
        val text = try {
            withContext(Dispatchers.IO) { ManagerClient(url).fetchPackage() }
        } catch (e: java.io.IOException) {
            Log.i(TAG, "No access to the Manager for the package: ${e.message}")
            _packageCheck.value = PackageCheck.Offline(e.message ?: e.javaClass.simpleName)
            return@withLock
        }
        if (text == null) {
            _packageCheck.value = PackageCheck.NonePublished
            return@withLock
        }
        _packageCheck.value = when (val d = withContext(Dispatchers.Default) { PackageUpdate.decide(readyPackage(), text) }) {
            is PackageDecision.Keep -> PackageCheck.UpToDate(d.version)
            is PackageDecision.Reject -> {
                Log.w(TAG, "Package from the Manager discarded: ${d.reason}")
                PackageCheck.Rejected(d.reason)
            }
            is PackageDecision.Replace -> {
                val origin = PackageOrigin.Downloaded(url, System.currentTimeMillis())
                withContext(Dispatchers.IO) { packageStorage.save(text, origin) }
                Lang.configure(d.loaded.pkg, uiLanguage)
                speaker.setLanguage(Lang.active)
                _pkg.value = PackageState.Ready(d.loaded, origin)
                Log.i(TAG, "Package v${d.loaded.pkg.packageVersion} downloaded from $url")
                PackageCheck.Updated(d.loaded.pkg.packageVersion)
            }
        }
    }

    /** The activity reports its language when created (also after choosing another one in demo mode). */
    fun setUiLanguage(language: String) {
        uiLanguage = language
        readyPackage()?.let { Lang.configure(it, language) }
        speaker.setLanguage(Lang.active)
    }

    /** Goes back to the package embedded in the app and forgets the downloaded one (demo mode). */
    fun useEmbeddedPackage() {
        viewModelScope.launch {
            packageLock.withLock {
                withContext(Dispatchers.IO) { packageStorage.clear() }
                _pkg.value = loadEmbeddedPackage()
                _packageCheck.value = PackageCheck.Idle
            }
        }
    }

    /** Forces an attempt to send and to read the review now. */
    fun syncNow() {
        viewModelScope.launch { sync.runOnce(crewId.value) }
    }

    /** Asks the voice for the current announcements; those already said are ignored. */
    fun announce(announcements: List<Announcement>) = speaker.announce(announcements)

    fun setVoiceMuted(muted: Boolean) = speaker.setMuted(muted)

    // ---------- Heart rate from the watch (step 7) ----------

    private val ble = BleHeartRate(app) { store.saveWatchAddress(it) }
    private val simulatedHeart = SimulatedHeartRate(viewModelScope)
    private val _heartSource = MutableStateFlow(store.loadHeartSource())
    val heartSource: StateFlow<HeartSource> = _heartSource.asStateFlow()
    val watchesFound: StateFlow<List<FoundWatch>> = ble.found

    /** Current heart rate according to the chosen source. */
    val heart: StateFlow<HeartState> = combine(_heartSource, ble.state, simulatedHeart.state) { src, b, s ->
        when (src) {
            HeartSource.WATCH -> b
            HeartSource.SIMULATED -> s
            HeartSource.OFF -> HeartState()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HeartState())

    private val heartSamples = ArrayDeque<Pair<Long, Int>>()
    @Volatile private var lastActiveMs = System.currentTimeMillis()

    // Started here, after declaring the heart rate properties (initialization order).
    init {
        // Heart rate: chosen source, in-memory readings and periodic aggregated sample.
        applyHeartSource()
        viewModelScope.launch {
            heart.collect { h ->
                val at = h.receivedAtMs ?: return@collect
                val bpm = h.bpm ?: return@collect
                synchronized(heartSamples) {
                    if (heartSamples.lastOrNull()?.first != at) heartSamples.addLast(at to bpm)
                    while (heartSamples.isNotEmpty() && at - heartSamples.first().first > HeartRules.REST_WINDOW_MS) heartSamples.removeFirst()
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(HeartRules.SAMPLE_INTERVAL_MS)
                sampleHeart()
            }
        }
    }

    fun setHeartSource(source: HeartSource) {
        _heartSource.value = source
        store.saveHeartSource(source)
        applyHeartSource()
    }

    private fun applyHeartSource() {
        when (_heartSource.value) {
            HeartSource.WATCH -> { simulatedHeart.stop(); ble.start(store.loadWatchAddress()) }
            HeartSource.SIMULATED -> { ble.stop(); simulatedHeart.start() }
            HeartSource.OFF -> { ble.stop(); simulatedHeart.stop() }
        }
    }

    fun searchWatches() = ble.startScan()
    fun stopWatchSearch() = ble.stopScan()
    fun chooseWatch(address: String) = ble.choose(address)
    fun forgetWatch() = ble.forget()
    /** When returning to the app (permissions or Bluetooth changed from settings). */
    fun refreshWatch() = ble.refresh()

    /** The silhouette reports the (simulated) movement: raises the simulated HR and flags rest. */
    fun reportMotion(walking: Boolean, squatting: Boolean) {
        simulatedHeart.setActivity(walking, squatting)
        if (walking || squatting) lastActiveMs = System.currentTimeMillis()
    }

    /**
     * Stores an aggregated sample every [HeartRules.SAMPLE_INTERVAL_MS] (not every notification) and, with the
     * real watch and at rest, the difference with the demonstration baseline as `resting_hr_delta`.
     */
    private suspend fun sampleHeart() {
        val crew = crewId.value ?: return
        val pkg = readyPackage() ?: return
        val now = System.currentTimeMillis()
        val window = synchronized(heartSamples) {
            heartSamples.filter { now - it.first <= HeartRules.SAMPLE_INTERVAL_MS }.map { it.second }
        }
        if (window.isEmpty()) return
        val h = heart.value
        val origin = if (h.source == HeartSource.WATCH) Origin.SENSOR else Origin.SIMULATED
        val mean = window.average()
        val day = snapshot.value?.day?.day
        val base = Observation(
            crewId = crew,
            kind = Observation.KIND_SIGNAL,
            indicator = HeartRules.HEART_RATE_ID,
            value = mean,
            rawValue = null,
            unit = "bpm",
            variantId = null,
            entryId = null,
            capacityId = null,
            flightDay = day,
            met = met.value,
            recordedAtUtcMs = now,
            origin = origin,
            quality = if (origin == Origin.SENSOR) "BLE watch · ${h.deviceName ?: "watch"} · mean of ${window.size} readings" else "simulated",
            packageVersion = pkg.packageVersion,
        )
        val list = mutableListOf(base)
        val restingSignal = pkg.signals.firstOrNull { it.id == HeartRules.RESTING_DELTA_SIGNAL_ID }
        if (origin == Origin.SENSOR && restingSignal != null && now - lastActiveMs > HeartRules.REST_WINDOW_MS) {
            // The crew member's own baseline if the package carries it (demo); otherwise the generic demonstration one.
            val baseline = pkg.crew.firstOrNull { it.id == crew }?.restingHrBpm?.toFloat() ?: HeartRules.DEMO_RESTING_BASELINE_BPM
            list += base.copy(
                indicator = restingSignal.id,
                value = mean - baseline,
                unit = restingSignal.unit,
                capacityId = restingSignal.capacityId,
                quality = "rest · demonstration baseline ${baseline.toInt()} bpm",
            )
        }
        dao.insertObservations(list)
    }

    /** Source of the silhouette's (simulated) movement: tablet sensor or forced from demo mode. */
    private val _motionSource = MutableStateFlow(MotionSource.SENSOR)
    val motionSource: StateFlow<MotionSource> = _motionSource.asStateFlow()

    fun setMotionSource(source: MotionSource) {
        _motionSource.value = source
    }

    /** Demo mode: forgets the notices already said to repeat the demonstration. */
    fun resetVoice() = speaker.forgetSpoken()

    // ---------- Data collection (step 4) ----------

    private fun readyPackage(): MissionPackage? = (pkg.value as? PackageState.Ready)?.loaded?.pkg

    /** Marks an activity as done or omitted. Omitting requires a reason. */
    fun confirm(activity: PlanActivity, status: ConfirmationStatus, reason: String? = null, itemId: String = activity.itemId) {
        val crew = crewId.value ?: return
        val pkg = readyPackage() ?: return
        require(status != ConfirmationStatus.OMITTED || !reason.isNullOrBlank()) { "Omitir exige motivo" }
        val c = ActivityConfirmation(
            crewId = crew,
            entryId = activity.entryId,
            itemId = itemId,
            packageVersion = pkg.packageVersion,
            status = status,
            reason = reason,
            confirmedAtMet = met.value,
            recordedAtUtcMs = System.currentTimeMillis(),
        )
        viewModelScope.launch { dao.upsertConfirmation(c) }
    }

    /** Undoes a confirmation made by mistake. */
    fun undoConfirmation(entryId: String) {
        val crew = crewId.value ?: return
        viewModelScope.launch { dao.deleteConfirmation(crew, entryId) }
    }

    /** Questions of a day's questionnaire, according to the package and the latest measurement of each signal. */
    fun questionsFor(day: FlightDay): List<SelectedQuestion> {
        val crew = crewId.value ?: return emptyList()
        val pkg = readyPackage() ?: return emptyList()
        val latest = observations.value.filter { it.kind == Observation.KIND_SIGNAL }
            .groupBy { it.indicator }.mapValues { (_, list) -> list.maxBy { it.met }.value }
        return QuestionnaireSelector.select(pkg, crew, day, latestSignals = latest)
    }

    /**
     * Stores the answers (original and normalized, variant and MET) and marks the questionnaire activity
     * as done, all on the device.
     */
    fun saveQuestionnaire(activity: PlanActivity, day: FlightDay, answers: List<Pair<SelectedQuestion, Int>>) {
        val crew = crewId.value ?: return
        val pkg = readyPackage() ?: return
        val now = System.currentTimeMillis()
        val atMet = met.value
        val observations = answers.map { (q, answer) ->
            Observation(
                crewId = crew,
                kind = Observation.KIND_QUESTIONNAIRE,
                indicator = q.item.id,
                value = QuestionnaireSelector.normalize(q.item, q.variant, answer),
                rawValue = answer.toDouble(),
                unit = "${q.item.scale.min}–${q.item.scale.max}",
                variantId = q.variant.id,
                entryId = activity.entryId,
                capacityId = q.item.capacityId,
                flightDay = day.day,
                met = atMet,
                recordedAtUtcMs = now,
                origin = Origin.MANUAL,
                quality = "autoinforme",
                packageVersion = pkg.packageVersion,
            )
        }
        viewModelScope.launch {
            dao.insertObservations(observations)
            confirm(activity, ConfirmationStatus.DONE)
        }
    }

    override fun onCleared() {
        ble.stop()
        simulatedHeart.stop()
        speaker.shutdown()
    }

    fun selectCrew(id: String) {
        store.saveCrewId(id)
        _crewId.value = id
    }

    /** Sets the MET by hand, within the mission [0, end]. */
    fun setMet(met: Long) = updateClock { s, now ->
        val end = (pkg.value as? PackageState.Ready)?.loaded?.pkg?.endMet ?: Long.MAX_VALUE
        MissionClock.setMet(s, met.coerceIn(0, end), now)
    }

    fun shiftMet(deltaSeconds: Long) = setMet(met.value + deltaSeconds)

    fun setRate(rate: Int) = updateClock { s, now -> MissionClock.setRate(s, rate, now, launchUtcMs()) }

    fun setRunning(running: Boolean) = updateClock { s, now -> MissionClock.setRunning(s, running, now, launchUtcMs()) }

    fun setSource(source: ClockSource) = updateClock { s, now -> MissionClock.setSource(s, source, now, launchUtcMs()) }

    fun launchUtcMs(): Long? =
        MissionClock.launchUtcMs((pkg.value as? PackageState.Ready)?.loaded?.pkg?.mission?.launchUtc)

    private fun updateClock(change: (ClockSettings, Long) -> ClockSettings) {
        _clock.update { change(it, System.currentTimeMillis()) }
        store.saveClock(_clock.value)
    }

    /** The last downloaded one if still valid; otherwise the embedded one. */
    private suspend fun loadInitialPackage(): PackageState = withContext(Dispatchers.IO) {
        val stored = packageStorage.load()
        if (stored != null) {
            try {
                val loaded = MissionPackageParser.parse(stored.first)
                Lang.configure(loaded.pkg, uiLanguage)
                return@withContext PackageState.Ready(loaded, stored.second)
            } catch (e: PackageException) {
                Log.w(TAG, "The downloaded package is no longer valid; using the embedded one", e)
                packageStorage.clear()
            }
        }
        loadEmbeddedPackage()
    }

    private suspend fun loadEmbeddedPackage(): PackageState = withContext(Dispatchers.IO) {
        try {
            val t0 = System.nanoTime()
            val text = getApplication<Application>().assets.open(EMBEDDED_PACKAGE).bufferedReader().use { it.readText() }
            val t1 = System.nanoTime()
            val loaded = MissionPackageParser.parse(text)
            // Language of package texts: the system's if the package carries it; otherwise its default language.
            Lang.configure(loaded.pkg, uiLanguage)
            Log.i(TAG, "Package v${loaded.pkg.packageVersion}: read ${(t1 - t0) / 1_000_000} ms, parse ${(System.nanoTime() - t1) / 1_000_000} ms")
            PackageState.Ready(loaded, origin = PackageOrigin.Embedded)
        } catch (e: PackageException) {
            Log.e(TAG, "Embedded package is not valid", e)
            PackageState.Failed(e.message ?: str(R.string.pkg_invalid))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Could not read the embedded package", e)
            PackageState.Failed(str(R.string.pkg_embedded_unreadable))
        }
    }

    private companion object {
        const val TAG = "AuraMission"
        const val EMBEDDED_PACKAGE = "mission-package.json"
        /** Clock refresh: enough to see the seconds pass without draining the battery. */
        const val TICK_MS = 250L
        /** How often sending to mission control is retried and its review read. */
        const val SYNC_INTERVAL_MS = 15_000L
        /** `quality` prefix of simulated signals: identifies the demo scenario. */
        const val SCENARIO_PREFIX = "simulated · scenario "
        /** Prefix stored by earlier versions; still recognized. */
        private const val LEGACY_SCENARIO_PREFIX = "simulado · escenario "

        /** Scenario name from a `quality` value, or null if it is not a demo scenario. */
        fun scenarioName(quality: String): String? = when {
            quality.startsWith(SCENARIO_PREFIX) -> quality.removePrefix(SCENARIO_PREFIX)
            quality.startsWith(LEGACY_SCENARIO_PREFIX) -> quality.removePrefix(LEGACY_SCENARIO_PREFIX)
            else -> null
        }

        /** First start: MET 00/00:00 running in real time, in manual mode. */
        fun defaultClock() = ClockSettings(anchorMet = 0, anchorWallMs = System.currentTimeMillis())
    }
}
