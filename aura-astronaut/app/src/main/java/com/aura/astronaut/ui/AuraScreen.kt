package com.aura.astronaut.ui

import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.astronaut.assessment.DemoScenario
import com.aura.astronaut.data.ConfirmationStatus
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.ui.hud.AdaptationsDetailPanel
import com.aura.astronaut.ui.hud.BodySilhouette
import com.aura.astronaut.ui.hud.CapacityDetailPanel
import com.aura.astronaut.ui.hud.CapacityPanel
import com.aura.astronaut.ui.hud.TransitionReadinessPanel
import com.aura.astronaut.ui.hud.WatchActions
import com.aura.astronaut.ui.hud.WatchPanel
import com.aura.astronaut.heart.BleHeartRate
import com.aura.astronaut.heart.HeartSource
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.aura.astronaut.hud.HudTexts
import com.aura.astronaut.hud.PlanItem
import com.aura.astronaut.ui.collect.ActivityActionHandler
import com.aura.astronaut.ui.collect.OmitReasonPanel
import com.aura.astronaut.ui.collect.QuestionnairePanel
import com.aura.astronaut.mission.ClockSource
import com.aura.astronaut.motion.BodyMotionSensor
import com.aura.astronaut.motion.MotionInput
import com.aura.astronaut.motion.MotionSource
import com.aura.astronaut.mission.Severity
import com.aura.astronaut.state.AuraViewModel
import com.aura.astronaut.state.PackageState
import com.aura.astronaut.ui.camera.CameraAccess
import com.aura.astronaut.ui.camera.CameraFeed
import com.aura.astronaut.ui.camera.CameraPermissionController
import com.aura.astronaut.ui.camera.CameraPreview
import com.aura.astronaut.ui.camera.NeutralBackground
import com.aura.astronaut.ui.camera.rememberCameraPermission
import com.aura.astronaut.ui.crew.CrewPicker
import com.aura.astronaut.ui.demo.DemoActions
import com.aura.astronaut.ui.demo.DemoPanel
import com.aura.astronaut.ui.hud.DayPlanPanel
import com.aura.astronaut.ui.hud.MissionContextPanel
import com.aura.astronaut.ui.hud.NowAndNextPanel
import com.aura.astronaut.ui.hud.TransitionBanner
import com.aura.astronaut.ui.theme.HudButton
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudPanel
import com.aura.astronaut.ui.theme.HudText
import com.aura.astronaut.voice.VoiceStatus

/* Landscape HUD layout: context at the top left, identity and transition at the top
 * right, and at the bottom a bar with "Now / Next" and the controls. The center is left for the camera. */
private val LeftColumnWidth = 340.dp
private val RightColumnWidth = 360.dp
private val ControlWidth = 112.dp
private val NowAndNextMaxWidth = 780.dp
private val ColumnGap = 12.dp
/** Height reserved at the bottom for the "Now / Next" bar and the controls. */
private val BottomBarReserve = 150.dp
/** The silhouette may go a bit lower: on the right there are only the buttons, lower than the bar. */
private val SilhouetteBottomReserve = 100.dp

@Composable
fun AuraScreen(vm: AuraViewModel = viewModel()) {
    val permission = rememberCameraPermission()
    var feed by remember { mutableStateOf(CameraFeed.Starting) }
    var cameraPanelDismissed by rememberSaveable { mutableStateOf(false) }
    var demoOpen by rememberSaveable { mutableStateOf(false) }
    var dayPlanOpen by rememberSaveable { mutableStateOf(false) }
    var capacityDetailOpen by rememberSaveable { mutableStateOf(false) }
    var adaptationsOpen by rememberSaveable { mutableStateOf(false) }
    var readinessOpen by rememberSaveable { mutableStateOf(false) }
    val granted = permission.access == CameraAccess.Granted

    val pkgState by vm.pkg.collectAsStateWithLifecycle()
    val packageCheck by vm.packageCheck.collectAsStateWithLifecycle()
    val met by vm.met.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val crewId by vm.crewId.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val voiceStatus by vm.voiceStatus.collectAsStateWithLifecycle()
    val voiceMuted by vm.voiceMuted.collectAsStateWithLifecycle()
    val confirmations by vm.confirmations.collectAsStateWithLifecycle()
    val assessment by vm.assessment.collectAsStateWithLifecycle()
    val adaptations by vm.adaptations.collectAsStateWithLifecycle()
    val scenario by vm.simulatedScenario.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    val readiness by vm.readiness.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val motionSource by vm.motionSource.collectAsStateWithLifecycle()
    val motion = rememberBodyMotion(motionSource)
    val appContext = LocalContext.current.applicationContext
    val motionSensorAvailable = remember { BodyMotionSensor(appContext).available }
    LaunchedEffect(motion.walking, motion.squatting) { vm.reportMotion(motion.walking, motion.squatting) }

    // Heart rate (step 7)
    val heart by vm.heart.collectAsStateWithLifecycle()
    val heartSource by vm.heartSource.collectAsStateWithLifecycle()
    val watchesFound by vm.watchesFound.collectAsStateWithLifecycle()
    var watchOpen by rememberSaveable { mutableStateOf(false) }
    val blePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshWatch() }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.refreshWatch() }
    val watchActions = remember(vm) {
        object : WatchActions {
            override fun setSource(source: HeartSource) = vm.setHeartSource(source)
            override fun requestPermission() = blePermission.launch(BleHeartRate.requiredPermissions())
            override fun openBluetoothSettings() {
                appContext.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            override fun search() = vm.searchWatches()
            override fun stopSearch() = vm.stopWatchSearch()
            override fun choose(address: String) = vm.chooseWatch(address)
            override fun forget() = vm.forgetWatch()
        }
    }
    val ready = pkgState as? PackageState.Ready
    val catalog = remember(ready) { ready?.loaded?.pkg?.catalog.orEmpty().associateBy { it.id } }
    val redStates = assessment?.capacities.orEmpty().filter { it.level == CapacityLevel.RED }

    // Data collection: activity about to be omitted (asks for a reason) or whose questionnaire is being answered.
    var omitTarget by remember { mutableStateOf<PlanItem?>(null) }
    var questionnaireTarget by remember { mutableStateOf<PlanItem?>(null) }
    val questionnaireItemId = ready?.loaded?.pkg?.questionnaire?.catalogItemId
    val actions = remember(vm, questionnaireItemId) {
        object : ActivityActionHandler {
            override val questionnaireItemId = questionnaireItemId
            override fun done(plan: PlanItem) = vm.confirm(plan.activity, ConfirmationStatus.DONE, itemId = plan.item.id)
            override fun omit(plan: PlanItem) { omitTarget = plan }
            override fun answer(plan: PlanItem) { questionnaireTarget = plan }
            override fun undo(plan: PlanItem) = vm.undoConfirmation(plan.activity.entryId)
        }
    }

    // Voice notices: the same texts the HUD shows, each one only once.
    val reference = ready?.loaded?.pkg?.gravityReference.orEmpty()
    val announcements = buildList {
        snapshot?.day?.let { d -> redStates.forEach { add(HudTexts.redAnnouncement(d.day, it)) } }
        snapshot?.let { addAll(HudTexts.announcementsFor(it, reference)) }
        adaptations.forEach { a ->
            add(HudTexts.adaptationAnnouncement(a, catalog))
            HudTexts.reviewAnnouncement(a, catalog)?.let { add(it) }
        }
        val capacityNames = ready?.loaded?.pkg?.capacities.orEmpty().associate { it.id to it.name.text }
        alerts.forEach { al -> HudTexts.alertAckAnnouncement(al, capacityNames[al.capacityId])?.let { add(it) } }
    }
    LaunchedEffect(announcements.map { it.id }) { vm.announce(announcements) }

    Box(Modifier.fillMaxSize()) {
        // Layer 1: background. The neutral background stays underneath and shows if the camera gives no image.
        NeutralBackground()
        if (granted) CameraPreview(onFeedChange = { feed = it })

        // Layer 2: HUD.
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
        ) {
            when (val state = pkgState) {
                PackageState.Loading -> HudPanel(Modifier.align(Alignment.TopStart)) {
                    Text(str(R.string.loading_package), style = HudText.Body, color = HudColors.TextDim)
                }
                is PackageState.Failed -> PackageError(state.message, Modifier.align(Alignment.Center))
                is PackageState.Ready -> {
                    val pkg = state.loaded.pkg
                    snapshot?.let { s ->
                        Column(
                            Modifier
                                .align(Alignment.TopStart)
                                .width(LeftColumnWidth),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MissionContextPanel(
                                state.loaded,
                                pkg.crew.firstOrNull { it.id == crewId },
                                s,
                                clock,
                                vm.launchUtcMs() != null,
                                Modifier.fillMaxWidth(),
                            )
                            val a = assessment
                            val d = s.day
                            if (a != null && d != null) {
                                CapacityPanel(a, d.label, scenario, alerts, onOpen = { capacityDetailOpen = true }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    // Right column: transition notice and, in the free space, the silhouette. It never overlaps the bottom bar.
                    Column(
                        Modifier
                            .align(Alignment.TopEnd)
                            .width(RightColumnWidth)
                            .fillMaxHeight()
                            .padding(bottom = SilhouetteBottomReserve),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        snapshot?.transition?.let {
                            TransitionBanner(it, pkg.gravityReference, readiness, onOpen = { readinessOpen = true })
                        }
                        BodySilhouette(motion, heart, onHeartClick = { watchOpen = true }, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }

            // Bottom bar: "Now / Next" centered in the free space and the controls on the right.
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(ColumnGap),
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    snapshot?.let { s ->
                        NowAndNextPanel(
                            s,
                            actions = actions,
                            onOpenPlan = { dayPlanOpen = true },
                            modifier = Modifier
                                .widthIn(max = NowAndNextMaxWidth)
                                .fillMaxWidth(),
                        )
                    }
                }
                Controls(
                    planChanges = snapshot?.todayPlan?.count { it.adaptation != null } ?: 0,
                    planWarnings = snapshot?.notices?.count { it.severity == Severity.WARNING && it.day != null },
                    showDemo = ready != null,
                    cameraNote = cameraNote(permission.access, feed),
                    voiceStatus = voiceStatus,
                    voiceMuted = voiceMuted,
                    onPlan = { dayPlanOpen = true },
                    onDemo = { demoOpen = true },
                    onCamera = { cameraPanelDismissed = false },
                    onToggleVoice = { vm.setVoiceMuted(!voiceMuted) },
                )
            }

            val needsCameraPanel = permission.access == CameraAccess.Denied ||
                permission.access == CameraAccess.PermanentlyDenied
            val choosingCrew = ready != null && crewId == null
            if (needsCameraPanel && !cameraPanelDismissed && !choosingCrew && !demoOpen && !dayPlanOpen) {
                Scrim()
                CameraPermissionPanel(
                    permission = permission,
                    onDismiss = { cameraPanelDismissed = true },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Android "Back" closes the topmost open panel instead of leaving the app.
        val anyModal = omitTarget != null || questionnaireTarget != null || demoOpen || watchOpen ||
            readinessOpen || capacityDetailOpen || adaptationsOpen || dayPlanOpen
        BackHandler(enabled = anyModal) {
            when {
                omitTarget != null -> omitTarget = null
                questionnaireTarget != null -> questionnaireTarget = null
                demoOpen -> demoOpen = false
                watchOpen -> watchOpen = false
                adaptationsOpen -> adaptationsOpen = false
                readinessOpen -> readinessOpen = false
                capacityDetailOpen -> capacityDetailOpen = false
                dayPlanOpen -> dayPlanOpen = false
            }
        }

        // Layer 3: modal panels, over a veil that hides the HUD behind.
        if (ready != null && crewId == null && !demoOpen) {
            Scrim()
            CrewPicker(
                crew = ready.loaded.pkg.crew,
                simulated = ready.loaded.pkg.simulated,
                onSelect = vm::selectCrew,
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
            )
        }
        val crew = crewId
        if (dayPlanOpen && ready != null && crew != null && !demoOpen) {
            val pkg = ready.loaded.pkg
            Scrim(onClick = { dayPlanOpen = false })
            DayPlanPanel(
                pkg = pkg,
                crewId = crew,
                initialDay = snapshot?.day?.day ?: if (met < 0) pkg.days.first().day else pkg.days.last().day,
                met = met,
                confirmations = confirmations,
                adaptations = adaptations.associateBy { it.entryId },
                actions = actions,
                onOpenAdaptations = if (adaptations.isNotEmpty()) ({ adaptationsOpen = true }) else null,
                onClose = { dayPlanOpen = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            )
        }
        omitTarget?.let { target ->
            Scrim()
            OmitReasonPanel(
                plan = target,
                onReason = { reason ->
                    vm.confirm(target.activity, ConfirmationStatus.OMITTED, reason, itemId = target.item.id)
                    omitTarget = null
                },
                onCancel = { omitTarget = null },
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
            )
        }
        questionnaireTarget?.let { target ->
            val questions = remember(target.activity.entryId) { vm.questionsFor(target.day) }
            Scrim()
            QuestionnairePanel(
                title = str(R.string.questionnaire_title, target.day.label),
                questions = questions,
                thresholdsAreDemo = ready?.loaded?.pkg?.thresholdsAreDemo ?: true,
                onSave = { answers ->
                    vm.saveQuestionnaire(target.activity, target.day, answers)
                    questionnaireTarget = null
                },
                onCancel = { questionnaireTarget = null },
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            )
        }
        val t = snapshot?.transition
        val r = readiness
        if (readinessOpen && t != null && r != null && !demoOpen) {
            Scrim(onClick = { readinessOpen = false })
            TransitionReadinessPanel(
                t = t,
                r = r,
                reference = reference,
                onClose = { readinessOpen = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            )
        }
        val a = assessment
        val d = snapshot?.day
        if (capacityDetailOpen && a != null && d != null && !demoOpen) {
            Scrim(onClick = { capacityDetailOpen = false })
            CapacityDetailPanel(
                assessment = a,
                dayLabel = d.label,
                onClose = { capacityDetailOpen = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            )
        }
        if (adaptationsOpen && adaptations.isNotEmpty() && !demoOpen) {
            Scrim(onClick = { adaptationsOpen = false })
            AdaptationsDetailPanel(
                adaptations = adaptations,
                catalog = catalog,
                sync = syncStatus,
                onSyncNow = vm::syncNow,
                onClose = { adaptationsOpen = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            )
        }
        if (watchOpen && !demoOpen) {
            Scrim(onClick = { watchOpen = false })
            WatchPanel(
                heart = heart,
                source = heartSource,
                found = watchesFound,
                actions = watchActions,
                onClose = { watchOpen = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            )
        }
        if (demoOpen && ready != null) {
            Scrim(onClick = { demoOpen = false })
            DemoPanel(
                loaded = ready.loaded,
                origin = ready.origin,
                packageCheck = packageCheck,
                met = met,
                clock = clock,
                crewId = crewId,
                launchUtcMs = vm.launchUtcMs(),
                dayLabel = snapshot?.day?.label,
                motionSource = motionSource,
                heartSource = heartSource,
                onOpenWatch = { demoOpen = false; watchOpen = true },
                motionSensorAvailable = motionSensorAvailable,
                scenario = scenario,
                sync = syncStatus,
                actions = remember(vm) { vm.asDemoActions() },
                onClose = { demoOpen = false },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            )
        }
    }
}

/**
 * Always-visible controls, in a row: day plan (with today's mission control notices),
 * camera state (if there is no image), voice and demo mode.
 */
@Composable
private fun Controls(
    planChanges: Int,
    planWarnings: Int?,
    showDemo: Boolean,
    cameraNote: String?,
    voiceStatus: VoiceStatus,
    voiceMuted: Boolean,
    onPlan: () -> Unit,
    onDemo: () -> Unit,
    onCamera: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val button = Modifier.width(ControlWidth)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        if (planWarnings != null) {
            // The most relevant part of today's plan: changes made by AURA and, otherwise, mission control notices.
            HudButton(
                text = when {
                    planChanges == 1 -> str(R.string.control_plan_change)
                    planChanges > 1 -> str(R.string.control_plan_changes, planChanges)
                    planWarnings == 1 -> str(R.string.control_plan_warning)
                    planWarnings > 1 -> str(R.string.control_plan_warnings, planWarnings)
                    else -> str(R.string.control_plan_day)
                },
                onClick = onPlan,
                accent = if (planChanges + planWarnings > 0) HudColors.Amber else HudColors.Cyan,
                modifier = button,
            )
        }
        cameraNote?.let {
            HudButton(it, onCamera, accent = HudColors.Amber, modifier = button)
        }
        when (voiceStatus) {
            VoiceStatus.READY -> HudButton(
                text = if (voiceMuted) str(R.string.control_voice_off) else str(R.string.control_voice_on),
                onClick = onToggleVoice,
                accent = if (voiceMuted) HudColors.Gray else HudColors.Cyan,
                modifier = button,
            )
            VoiceStatus.STARTING -> HudButton(str(R.string.control_voice_starting), {}, enabled = false, modifier = button)
            VoiceStatus.NO_OFFLINE_VOICE -> HudButton(
                text = str(R.string.control_voice_install),
                onClick = {
                    try {
                        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        // No voice installer: the HUD keeps working without voice.
                    }
                },
                accent = HudColors.Amber,
                modifier = button,
            )
            VoiceStatus.UNAVAILABLE -> HudButton(str(R.string.control_no_voice), {}, enabled = false, modifier = button)
        }
        if (showDemo) HudButton(str(R.string.control_demo), onDemo, accent = HudColors.Amber, modifier = button)
    }
}

private fun cameraNote(access: CameraAccess, feed: CameraFeed): String? = when {
    access != CameraAccess.Granted -> str(R.string.control_no_camera)
    feed == CameraFeed.Live || feed == CameraFeed.Starting -> null
    else -> str(R.string.control_camera_unavailable)
}

/**
 * Silhouette movement: with [MotionSource.SENSOR], the tablet's accelerometer (registered only
 * while the screen is visible); with the others, the movement forced from demo mode.
 */
@Composable
private fun rememberBodyMotion(source: MotionSource): MotionInput {
    val context = LocalContext.current
    val sensor = remember { BodyMotionSensor(context.applicationContext) }
    val sensed by sensor.input.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(source, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (source == MotionSource.SENSOR) sensor.start()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> sensor.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (source == MotionSource.SENSOR && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) sensor.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensor.stop()
        }
    }
    return if (source == MotionSource.SENSOR) sensed.copy(source = MotionSource.SENSOR) else MotionInput.forced(source)
}

/** Dark veil under a modal panel: the HUD behind does not mix with the panel text. */
@Composable
private fun Scrim(onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            // Without onClick, it still absorbs touches so they do not reach the HUD behind.
            .clickable(interactionSource = interaction, indication = null, onClick = onClick ?: {}),
    )
}

private fun AuraViewModel.asDemoActions() = object : DemoActions {
    override fun selectCrew(id: String) = this@asDemoActions.selectCrew(id)
    override fun setMet(met: Long) = this@asDemoActions.setMet(met)
    override fun shiftMet(deltaSeconds: Long) = this@asDemoActions.shiftMet(deltaSeconds)
    override fun setRate(rate: Int) = this@asDemoActions.setRate(rate)
    override fun setRunning(running: Boolean) = this@asDemoActions.setRunning(running)
    override fun setSource(source: ClockSource) = this@asDemoActions.setSource(source)
    override fun applyScenario(scenario: DemoScenario) = this@asDemoActions.applyScenario(scenario)
    override fun clearScenario() = this@asDemoActions.clearScenario()
    override fun setManagerUrl(url: String) = this@asDemoActions.setManagerUrl(url)
    override fun syncNow() = this@asDemoActions.syncNow()
    override fun checkPackage() = this@asDemoActions.checkPackage()
    override fun useEmbeddedPackage() = this@asDemoActions.useEmbeddedPackage()
    override fun resetVoice() = this@asDemoActions.resetVoice()
    override fun setMotionSource(source: MotionSource) = this@asDemoActions.setMotionSource(source)
    override fun setHeartSource(source: HeartSource) = this@asDemoActions.setHeartSource(source)
}

@Composable
private fun PackageError(message: String, modifier: Modifier = Modifier) {
    HudPanel(modifier.widthIn(max = 720.dp), accent = HudColors.Red) {
        Column {
            Text(str(R.string.package_error_title), style = HudText.Title, color = HudColors.Red)
            Spacer(Modifier.height(6.dp))
            Text(message, style = HudText.Body, color = HudColors.Text)
            Text(
                str(R.string.package_error_body),
                style = HudText.Body,
                color = HudColors.TextDim,
            )
        }
    }
}

@Composable
private fun CameraPermissionPanel(
    permission: CameraPermissionController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permanent = permission.access == CameraAccess.PermanentlyDenied
    HudPanel(modifier = modifier.widthIn(max = 720.dp), accent = HudColors.Amber, modal = true) {
        Column {
            Text(str(R.string.camera_off_title), style = HudText.Title, color = HudColors.Amber)
            Spacer(Modifier.height(6.dp))
            Text(
                str(R.string.camera_off_body),
                style = HudText.Body,
                color = HudColors.Text,
            )
            if (permanent) {
                Text(
                    str(R.string.camera_blocked),
                    style = HudText.Body,
                    color = HudColors.TextDim,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HudButton(
                    text = if (permanent) str(R.string.camera_open_settings) else str(R.string.camera_allow),
                    onClick = { if (permanent) permission.openAppSettings() else permission.request() },
                    accent = HudColors.Amber,
                    selected = true,
                )
                HudButton(text = str(R.string.camera_continue_without), onClick = onDismiss, accent = HudColors.Gray)
            }
        }
    }
}
