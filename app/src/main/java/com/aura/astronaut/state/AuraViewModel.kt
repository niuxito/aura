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

/** Estado de misión que necesita el HUD. */
class AuraViewModel(app: Application) : AndroidViewModel(app) {

    private val store = DemoSettingsStore(app)
    private val packageStorage = PackageStorage(app)

    private val _pkg = MutableStateFlow<PackageState>(PackageState.Loading)
    val pkg: StateFlow<PackageState> = _pkg.asStateFlow()

    private val _packageCheck = MutableStateFlow<PackageCheck>(PackageCheck.Idle)
    /** Resultado de la última comprobación de paquete nuevo en AURA Manager. */
    val packageCheck: StateFlow<PackageCheck> = _packageCheck.asStateFlow()
    private val packageLock = Mutex()

    private val _clock = MutableStateFlow(store.loadClock() ?: defaultClock())
    val clock: StateFlow<ClockSettings> = _clock.asStateFlow()

    private val _crewId = MutableStateFlow(store.loadCrewId())

    /** Astronauta elegido, solo si existe en el paquete cargado; null = hay que elegirlo. */
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

    /** MET actual en segundos. */
    val met: StateFlow<Long> = combine(ticker, _clock, _pkg) { now, settings, _ ->
        MissionClock.metAt(settings, now, launchUtcMs())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MissionClock.metAt(_clock.value, System.currentTimeMillis(), null),
    )

    // El MET de demostración se detiene al llegar al final de la misión (amerizaje): pasado ese punto
    // no hay día de vuelo y el HUD se quedaría sin capacidades ni transición.
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

    /** Confirmaciones del tripulante elegido, por entryId. Persisten en la base local. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val confirmations: StateFlow<Map<String, ActivityConfirmation>> = crewId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.confirmations(id) }
        .map { list -> list.associateBy { it.entryId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Adaptaciones del tripulante elegido (aplicadas a bordo), con su estado de envío y revisión. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val adaptations: StateFlow<List<LocalAdaptation>> = crewId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.adaptations(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Alertas del tripulante elegido para control de misión, con su envío y acuse. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val alerts: StateFlow<List<LocalAlert>> = crewId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.alerts(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Todas las observaciones del tripulante elegido (cuestionario y señales). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val observations: StateFlow<List<Observation>> = crewId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.allObservations(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Situación de la misión para el tripulante elegido en el MET actual; null hasta tener paquete y tripulante. */
    val snapshot: StateFlow<MissionSnapshot?> = combine(met, _pkg, crewId, confirmations, adaptations) { m, p, crew, conf, adapt ->
        val pkg = (p as? PackageState.Ready)?.loaded?.pkg
        if (pkg == null || crew == null) null else MissionSnapshot.build(pkg, crew, m, conf, adapt.associateBy { it.entryId })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Semáforo de hoy por capacidad, con los datos del día de vuelo actual. */
    val assessment: StateFlow<Assessment?> = combine(
        snapshot.map { it?.day?.day }.distinctUntilChanged(),
        _pkg,
        observations,
    ) { day, p, obs ->
        val pkg = (p as? PackageState.Ready)?.loaded?.pkg
        if (pkg == null || day == null) null else CapacityEvaluator.evaluate(pkg, day, obs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Nombre del escenario simulado con datos hoy (para etiquetar el semáforo); null si no hay. */
    val simulatedScenario: StateFlow<DemoScenario?> = combine(snapshot.map { it?.day?.day }.distinctUntilChanged(), observations) { day, obs ->
        // Solo las señales de un escenario de demo (no las muestras de FC simulada).
        obs.lastOrNull { it.flightDay == day && it.origin == Origin.SIMULATED && it.quality.startsWith(SCENARIO_PREFIX) }
            ?.quality?.removePrefix(SCENARIO_PREFIX)?.let(::scenarioOf)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Preparación para la próxima transición hacia gravedad (paso 5b); null si no hay ninguna anunciada. */
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

    /** Idioma de la interfaz (el de la actividad): decide el de los textos del paquete y el de la voz. */
    @Volatile private var uiLanguage = AppLanguage.current(app)
    private val speaker = Speaker(app, uiLanguage)
    val voiceStatus: StateFlow<VoiceStatus> = speaker.status
    val voiceMuted: StateFlow<Boolean> = speaker.muted

    init {
        // Se guarda ya el reloj por defecto: así el MET no vuelve a cero al reabrir la app.
        store.saveClock(_clock.value)
        viewModelScope.launch {
            _pkg.value = loadInitialPackage()
            // Al arrancar se mira si control de misión ha publicado otro paquete; sin red, se sigue con este.
            checkPackageNow()
        }
        // Motor de reglas: decide al cambiar el día, el semáforo, el plan pendiente o el minuto de MET.
        viewModelScope.launch {
            combine(snapshot, assessment) { s, a -> s to a }
                .map { (s, a) -> EngineKey(s, a) }
                .distinctUntilChanged { old, new -> old.key == new.key }
                .collect { runEngine(it.snapshot, it.assessment) }
        }
        // Cola de envío a control de misión: no bloquea nada y reintenta sola.
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
            val handled = confirmations.value.keys + dao.adaptationsNow(crew).map { it.entryId }
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
                Log.i(TAG, "Adaptación aplicada: ${local.type} ${local.entryId} (${d.rule})")
            }
            // Alertas: lo que no se resuelve adaptando. Una por situación (id derivado de la clave).
            val catalog = pkg.catalog.associateBy { it.id }
            val newAlerts = AlertRules.candidates(a?.takeIf { it.flightDay == day.day }, dao.adaptationsNow(crew), catalog)
            // Una alerta por situación: se compara por clave (los ids de alertas antiguas siguen siendo válidos).
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

    // ---------- Escenarios de demo y servidor (paso 5) ----------

    /** Carga las señales simuladas de un escenario para el día de vuelo actual (sustituye las simuladas de hoy). */
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

    /** Borra las señales simuladas de hoy: el semáforo vuelve a depender solo del cuestionario. */
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

    /** Busca en AURA Manager un paquete publicado distinto del cargado y, si es válido, lo carga. */
    fun checkPackage() {
        viewModelScope.launch { checkPackageNow() }
    }

    private suspend fun checkPackageNow() = packageLock.withLock {
        val url = store.loadManagerUrl() ?: BuildConfig.MANAGER_URL
        _packageCheck.value = PackageCheck.Checking
        val text = try {
            withContext(Dispatchers.IO) { ManagerClient(url).fetchPackage() }
        } catch (e: java.io.IOException) {
            Log.i(TAG, "Sin acceso al Manager para el paquete: ${e.message}")
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
                Log.w(TAG, "Paquete del Manager descartado: ${d.reason}")
                PackageCheck.Rejected(d.reason)
            }
            is PackageDecision.Replace -> {
                val origin = PackageOrigin.Downloaded(url, System.currentTimeMillis())
                withContext(Dispatchers.IO) { packageStorage.save(text, origin) }
                Lang.configure(d.loaded.pkg, uiLanguage)
                speaker.setLanguage(Lang.active)
                _pkg.value = PackageState.Ready(d.loaded, origin)
                Log.i(TAG, "Paquete v${d.loaded.pkg.packageVersion} descargado de $url")
                PackageCheck.Updated(d.loaded.pkg.packageVersion)
            }
        }
    }

    /** La actividad comunica su idioma al crearse (también tras elegir otro en el modo demo). */
    fun setUiLanguage(language: String) {
        uiLanguage = language
        readyPackage()?.let { Lang.configure(it, language) }
        speaker.setLanguage(Lang.active)
    }

    /** Vuelve al paquete embebido en la app y olvida el descargado (modo demo). */
    fun useEmbeddedPackage() {
        viewModelScope.launch {
            packageLock.withLock {
                withContext(Dispatchers.IO) { packageStorage.clear() }
                _pkg.value = loadEmbeddedPackage()
                _packageCheck.value = PackageCheck.Idle
            }
        }
    }

    /** Fuerza un intento de envío y de lectura de la revisión ahora. */
    fun syncNow() {
        viewModelScope.launch { sync.runOnce(crewId.value) }
    }

    /** Pide a la voz los anuncios vigentes; los ya dichos se ignoran. */
    fun announce(announcements: List<Announcement>) = speaker.announce(announcements)

    fun setVoiceMuted(muted: Boolean) = speaker.setMuted(muted)

    // ---------- Frecuencia cardiaca del reloj (paso 7) ----------

    private val ble = BleHeartRate(app) { store.saveWatchAddress(it) }
    private val simulatedHeart = SimulatedHeartRate(viewModelScope)
    private val _heartSource = MutableStateFlow(store.loadHeartSource())
    val heartSource: StateFlow<HeartSource> = _heartSource.asStateFlow()
    val watchesFound: StateFlow<List<FoundWatch>> = ble.found

    /** Frecuencia cardiaca vigente según la fuente elegida. */
    val heart: StateFlow<HeartState> = combine(_heartSource, ble.state, simulatedHeart.state) { src, b, s ->
        when (src) {
            HeartSource.WATCH -> b
            HeartSource.SIMULATED -> s
            HeartSource.OFF -> HeartState()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HeartState())

    private val heartSamples = ArrayDeque<Pair<Long, Int>>()
    @Volatile private var lastActiveMs = System.currentTimeMillis()

    // Se inicia aquí, después de declarar las propiedades de la frecuencia cardiaca (orden de inicialización).
    init {
        // Frecuencia cardiaca: fuente elegida, lecturas en memoria y muestra agregada periódica.
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
    /** Al volver a la app (permisos o Bluetooth cambiados desde los ajustes). */
    fun refreshWatch() = ble.refresh()

    /** La silueta informa del movimiento (simulado): sube la FC simulada y marca si hay reposo. */
    fun reportMotion(walking: Boolean, squatting: Boolean) {
        simulatedHeart.setActivity(walking, squatting)
        if (walking || squatting) lastActiveMs = System.currentTimeMillis()
    }

    /**
     * Guarda una muestra agregada cada [HeartRules.SAMPLE_INTERVAL_MS] (no cada notificación) y, con el
     * reloj real y en reposo, la diferencia con la referencia de demostración como `resting_hr_delta`.
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
            quality = if (origin == Origin.SENSOR) "reloj BLE · ${h.deviceName ?: "reloj"} · media de ${window.size} lecturas" else "simulado",
            packageVersion = pkg.packageVersion,
        )
        val list = mutableListOf(base)
        val restingSignal = pkg.signals.firstOrNull { it.id == HeartRules.RESTING_DELTA_SIGNAL_ID }
        if (origin == Origin.SENSOR && restingSignal != null && now - lastActiveMs > HeartRules.REST_WINDOW_MS) {
            // Referencia propia del tripulante si el paquete la trae (demo); si no, la genérica de demostración.
            val baseline = pkg.crew.firstOrNull { it.id == crew }?.restingHrBpm?.toFloat() ?: HeartRules.DEMO_RESTING_BASELINE_BPM
            list += base.copy(
                indicator = restingSignal.id,
                value = mean - baseline,
                unit = restingSignal.unit,
                capacityId = restingSignal.capacityId,
                quality = "reposo · referencia de demostración ${baseline.toInt()} bpm",
            )
        }
        dao.insertObservations(list)
    }

    /** Origen del movimiento de la silueta (simulado): sensor de la tablet o forzado desde el modo demo. */
    private val _motionSource = MutableStateFlow(MotionSource.SENSOR)
    val motionSource: StateFlow<MotionSource> = _motionSource.asStateFlow()

    fun setMotionSource(source: MotionSource) {
        _motionSource.value = source
    }

    /** Modo demo: olvida los avisos ya dichos para repetir la demostración. */
    fun resetVoice() = speaker.forgetSpoken()

    // ---------- Recogida de datos (paso 4) ----------

    private fun readyPackage(): MissionPackage? = (pkg.value as? PackageState.Ready)?.loaded?.pkg

    /** Marca una actividad como hecha u omitida. Omitir exige motivo. */
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

    /** Deshace una confirmación hecha por error. */
    fun undoConfirmation(entryId: String) {
        val crew = crewId.value ?: return
        viewModelScope.launch { dao.deleteConfirmation(crew, entryId) }
    }

    /** Preguntas del cuestionario de un día, según el paquete y la última medida de cada señal. */
    fun questionsFor(day: FlightDay): List<SelectedQuestion> {
        val crew = crewId.value ?: return emptyList()
        val pkg = readyPackage() ?: return emptyList()
        val latest = observations.value.filter { it.kind == Observation.KIND_SIGNAL }
            .groupBy { it.indicator }.mapValues { (_, list) -> list.maxBy { it.met }.value }
        return QuestionnaireSelector.select(pkg, crew, day, latestSignals = latest)
    }

    /**
     * Guarda las respuestas (original y normalizada, variante y MET) y da por hecha la actividad
     * del cuestionario, todo en el dispositivo.
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

    /** Fija el MET a mano, dentro de la misión [0, fin]. */
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

    /** El último descargado si sigue siendo válido; si no, el embebido. */
    private suspend fun loadInitialPackage(): PackageState = withContext(Dispatchers.IO) {
        val stored = packageStorage.load()
        if (stored != null) {
            try {
                val loaded = MissionPackageParser.parse(stored.first)
                Lang.configure(loaded.pkg, uiLanguage)
                return@withContext PackageState.Ready(loaded, stored.second)
            } catch (e: PackageException) {
                Log.w(TAG, "El paquete descargado ya no es válido; se usa el embebido", e)
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
            // Idioma de los textos del paquete: el del sistema si el paquete lo trae; si no, su idioma por defecto.
            Lang.configure(loaded.pkg, uiLanguage)
            Log.i(TAG, "Paquete v${loaded.pkg.packageVersion}: lectura ${(t1 - t0) / 1_000_000} ms, análisis ${(System.nanoTime() - t1) / 1_000_000} ms")
            PackageState.Ready(loaded, origin = PackageOrigin.Embedded)
        } catch (e: PackageException) {
            Log.e(TAG, "Paquete embebido no válido", e)
            PackageState.Failed(e.message ?: str(R.string.pkg_invalid))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "No se pudo leer el paquete embebido", e)
            PackageState.Failed(str(R.string.pkg_embedded_unreadable))
        }
    }

    private companion object {
        const val TAG = "AuraMission"
        const val EMBEDDED_PACKAGE = "mission-package.json"
        /** Refresco del reloj: suficiente para ver pasar los segundos sin gastar batería. */
        const val TICK_MS = 250L
        /** Cada cuánto se reintenta el envío a control de misión y se lee su revisión. */
        const val SYNC_INTERVAL_MS = 15_000L
        /** Prefijo de `quality` en las señales simuladas: identifica el escenario de demo. */
        const val SCENARIO_PREFIX = "simulado · escenario "

        /** Primer arranque: MET 00/00:00 corriendo en tiempo real, en modo manual. */
        fun defaultClock() = ClockSettings(anchorMet = 0, anchorWallMs = System.currentTimeMillis())
    }
}
