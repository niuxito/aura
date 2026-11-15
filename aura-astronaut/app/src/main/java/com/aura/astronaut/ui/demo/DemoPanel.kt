package com.aura.astronaut.ui.demo

import com.aura.astronaut.i18n.AppLanguage
import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aura.astronaut.BuildConfig
import com.aura.astronaut.assessment.DemoScenario
import com.aura.astronaut.mission.ClockSettings
import com.aura.astronaut.heart.HeartSource
import com.aura.astronaut.motion.MotionSource
import com.aura.astronaut.sync.SyncStatus
import com.aura.astronaut.mission.ClockSource
import com.aura.astronaut.mission.LoadedPackage
import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionClock
import com.aura.astronaut.mission.MissionEvent
import com.aura.astronaut.ui.label
import com.aura.astronaut.sync.PackageCheck
import com.aura.astronaut.sync.PackageOrigin
import com.aura.astronaut.ui.theme.DemoTouchTarget
import com.aura.astronaut.ui.theme.HudButton
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudPanel
import com.aura.astronaut.ui.theme.HudText

/** Acciones del modo demo sobre el reloj y la tripulación. */
interface DemoActions {
    fun selectCrew(id: String)
    fun setMet(met: Long)
    fun shiftMet(deltaSeconds: Long)
    fun setRate(rate: Int)
    fun setRunning(running: Boolean)
    fun setSource(source: ClockSource)
    fun applyScenario(scenario: DemoScenario)
    fun clearScenario()
    fun setManagerUrl(url: String)
    fun syncNow()
    fun resetVoice()
    fun setMotionSource(source: MotionSource)
    fun setHeartSource(source: HeartSource)
    fun checkPackage()
    fun useEmbeddedPackage()
}

/** Servidores de AURA Manager para la demo. El local va por USB con `adb reverse tcp:3000 tcp:3000`. */
private val ManagerPresets = listOf(
    null to BuildConfig.MANAGER_URL,
    R.string.preset_usb to "http://localhost:3000",
    R.string.preset_emulator to "http://10.0.2.2:3000",
)

/**
 * Panel lateral del modo demo. Lo usa quien presenta, no el astronauta:
 * fija el MET, su velocidad y el tripulante.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoPanel(
    loaded: LoadedPackage,
    origin: PackageOrigin,
    packageCheck: PackageCheck,
    met: Long,
    clock: ClockSettings,
    crewId: String?,
    launchUtcMs: Long?,
    dayLabel: String?,
    motionSource: MotionSource,
    motionSensorAvailable: Boolean,
    heartSource: HeartSource,
    onOpenWatch: () -> Unit,
    scenario: DemoScenario?,
    sync: SyncStatus,
    actions: DemoActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pkg = loaded.pkg
    HudPanel(modifier.width(600.dp).fillMaxHeight(), accent = HudColors.Amber, modal = true) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.demo_mode), style = HudText.Title, color = HudColors.Amber, modifier = Modifier.weight(1f))
                HudButton(str(R.string.close), onClose, accent = HudColors.Amber, minHeight = DemoTouchTarget)
            }

            Section(str(R.string.demo_crew))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pkg.crew.forEach { member ->
                    HudButton(
                        text = member.id.uppercase(),
                        onClick = { actions.selectCrew(member.id) },
                        selected = member.id == crewId,
                        minHeight = DemoTouchTarget,
                    )
                }
            }

            Section("MET · ${Met.formatClock(met)}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pkg.days.forEach { day ->
                    val current = met >= day.startMet && met < day.endMet
                    HudButton(
                        text = day.label,
                        onClick = { actions.setMet(day.startMet) },
                        selected = current,
                        minHeight = DemoTouchTarget,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton("−1 h", { actions.shiftMet(-Met.HOUR) }, minHeight = DemoTouchTarget)
                HudButton("−10 min", { actions.shiftMet(-10 * Met.MINUTE) }, minHeight = DemoTouchTarget)
                HudButton("+10 min", { actions.shiftMet(10 * Met.MINUTE) }, minHeight = DemoTouchTarget)
                HudButton("+1 h", { actions.shiftMet(Met.HOUR) }, minHeight = DemoTouchTarget)
            }
            val previous = pkg.events.lastOrNull { it.met < met }
            val next = pkg.events.firstOrNull { it.met > met }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton(
                    text = previous?.let { "◀ ${eventLabel(it)}" } ?: str(R.string.demo_no_previous_event),
                    onClick = { previous?.let { actions.setMet(it.met) } },
                    enabled = previous != null,
                    minHeight = DemoTouchTarget,
                    modifier = Modifier.fillMaxWidth(),
                )
                HudButton(
                    text = next?.let { "${eventLabel(it)} ▶" } ?: str(R.string.demo_no_next_event),
                    onClick = { next?.let { actions.setMet(it.met) } },
                    enabled = next != null,
                    minHeight = DemoTouchTarget,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MetInput(onSubmit = actions::setMet)

            Section(str(R.string.demo_scenario_section, dayLabel ?: str(R.string.demo_outside_mission)))
            Note(
                str(R.string.demo_scenario_note, scenario?.let { str(it.label) } ?: str(R.string.demo_no_signals)),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoScenario.entries.forEach { sc ->
                    HudButton(
                        str(sc.label).uppercase(),
                        { actions.applyScenario(sc) },
                        selected = scenario == sc,
                        enabled = dayLabel != null,
                        minHeight = DemoTouchTarget,
                    )
                }
                HudButton(str(R.string.demo_clear_signals), { actions.clearScenario() }, accent = HudColors.Gray, enabled = dayLabel != null, minHeight = DemoTouchTarget)
            }
            scenario?.let { Note(str(it.description)) }

            Section(str(R.string.demo_speed))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton(
                    text = if (clock.running) str(R.string.demo_pause) else str(R.string.demo_resume),
                    onClick = { actions.setRunning(!clock.running) },
                    accent = HudColors.Amber,
                    selected = !clock.running,
                    minHeight = DemoTouchTarget,
                )
                MissionClock.DEMO_RATES.forEach { rate ->
                    HudButton(
                        text = rateLabel(rate),
                        onClick = { actions.setRate(rate) },
                        selected = clock.rate == rate,
                        minHeight = DemoTouchTarget,
                    )
                }
            }

            Section(str(R.string.demo_met_source))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton(
                    str(R.string.demo_manual),
                    { actions.setSource(ClockSource.MANUAL) },
                    selected = clock.source == ClockSource.MANUAL || launchUtcMs == null,
                    minHeight = DemoTouchTarget,
                )
                HudButton(
                    str(R.string.demo_launch_clock),
                    { actions.setSource(ClockSource.LAUNCH_UTC) },
                    selected = clock.source == ClockSource.LAUNCH_UTC && launchUtcMs != null,
                    enabled = launchUtcMs != null,
                    minHeight = DemoTouchTarget,
                )
            }
            if (launchUtcMs == null) {
                Note(str(R.string.demo_no_launch))
            }

            Section(str(R.string.demo_silhouette))
            Note(str(R.string.demo_silhouette_note))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MotionSource.SENSOR to str(R.string.motion_sensor),
                    MotionSource.WALK to str(R.string.motion_walk_button),
                    MotionSource.SQUAT to str(R.string.motion_squat_button),
                    MotionSource.STILL to str(R.string.motion_still_button),
                ).forEach { (source, label) ->
                    HudButton(label, { actions.setMotionSource(source) }, selected = motionSource == source, minHeight = DemoTouchTarget)
                }
            }
            if (!motionSensorAvailable) Note(str(R.string.demo_no_motion_sensor))

            Section(str(R.string.demo_heart))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HeartSource.WATCH to str(R.string.heart_source_watch), HeartSource.SIMULATED to str(R.string.heart_source_simulated), HeartSource.OFF to str(R.string.heart_source_off)).forEach { (s, label) ->
                    HudButton(label, { actions.setHeartSource(s) }, selected = heartSource == s, minHeight = DemoTouchTarget)
                }
                HudButton(str(R.string.demo_configure_watch), onOpenWatch, accent = HudColors.Amber, minHeight = DemoTouchTarget)
            }

            Section(str(R.string.demo_voice))
            HudButton(str(R.string.demo_repeat_voice), { actions.resetVoice() }, minHeight = DemoTouchTarget)
            Note(str(R.string.demo_voice_note))

            Section(str(R.string.demo_language))
            LanguagePicker()

            Section(str(R.string.demo_mission_control))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ManagerPresets.forEach { (label, url) ->
                    HudButton(label?.let { str(it) } ?: "VERCEL", { actions.setManagerUrl(url) }, selected = sync.url == url, minHeight = DemoTouchTarget)
                }
                HudButton(str(R.string.send_now), { actions.syncNow() }, accent = HudColors.Amber, minHeight = DemoTouchTarget)
            }
            Note(
                sync.url + " · " + when {
                    sync.lastError != null -> str(R.string.demo_sync_offline, sync.lastError.take(80))
                    sync.lastOkUtcMs != null -> str(R.string.demo_sync_connected)
                    else -> str(R.string.demo_sync_checking)
                },
            )

            Section(str(R.string.demo_package))
            Note(pkg.mission.name.text)
            Note(str(R.string.demo_package_info, pkg.packageVersion, pkg.schema, pkg.publishedAt, loaded.checksum.label))
            Note(str(R.string.demo_package_origin, originText(origin)))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton(
                    str(R.string.demo_check_package),
                    { actions.checkPackage() },
                    accent = HudColors.Amber,
                    enabled = packageCheck != PackageCheck.Checking,
                    minHeight = DemoTouchTarget,
                )
                if (origin is PackageOrigin.Downloaded) {
                    HudButton(str(R.string.demo_use_embedded), { actions.useEmbeddedPackage() }, accent = HudColors.Gray, minHeight = DemoTouchTarget)
                }
            }
            checkText(packageCheck)?.let { Note(it) }
            Note(str(R.string.demo_timeline, pkg.mission.timelineSource))
            Note(
                str(R.string.demo_attribution),
            )
            if (pkg.simulated) {
                Text(
                    str(R.string.demo_simulated_mission),
                    style = HudText.Body,
                    color = HudColors.Amber,
                )
            }
        }
    }
}

@Composable
private fun MetInput(onSubmit: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val submit = {
        val parsed = Met.parse(text)
        if (parsed == null) {
            error = str(R.string.met_input_error)
        } else {
            error = null
            onSubmit(parsed)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = null },
            label = { Text(str(R.string.met_input_label)) },
            singleLine = true,
            isError = error != null,
            textStyle = HudText.Body,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = HudColors.Text,
                unfocusedTextColor = HudColors.Text,
                focusedBorderColor = HudColors.Cyan,
                unfocusedBorderColor = HudColors.Gray,
                focusedLabelColor = HudColors.Cyan,
                unfocusedLabelColor = HudColors.TextDim,
            ),
            modifier = Modifier.weight(1f),
        )
        HudButton(str(R.string.go), { submit() }, minHeight = DemoTouchTarget)
    }
    error?.let { Text(it, style = HudText.Label, color = HudColors.Amber) }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = HudText.Label, color = HudColors.Cyan)
}

@Composable
private fun Note(text: String) {
    Text(text, style = HudText.Body, color = HudColors.TextDim)
}

private fun eventLabel(e: MissionEvent): String = "${if (e.approx) "~" else ""}${e.metLabel} ${e.name}"

private fun rateLabel(rate: Int): String = when (rate) {
    1 -> str(R.string.rate_real_time)
    60 -> "×60 · 1 MIN/S"
    600 -> "×600 · 10 MIN/S"
    3_600 -> "×3600 · 1 H/S"
    else -> "×$rate"
}

private fun originText(o: PackageOrigin): String = when (o) {
    PackageOrigin.Embedded -> str(R.string.origin_embedded)
    is PackageOrigin.Downloaded -> {
        val at = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(o.atMs))
        str(R.string.origin_downloaded, o.url, at)
    }
}

private fun checkText(c: PackageCheck): String? = when (c) {
    PackageCheck.Idle -> null
    PackageCheck.Checking -> str(R.string.check_checking)
    is PackageCheck.UpToDate -> str(R.string.check_up_to_date, c.version)
    is PackageCheck.Updated -> str(R.string.check_updated, c.version)
    PackageCheck.NonePublished -> str(R.string.check_none)
    is PackageCheck.Rejected -> str(R.string.check_rejected, c.reason.take(120))
    is PackageCheck.Offline -> str(R.string.check_offline)
}

/** Idioma de la app: el del sistema o uno de los de la interfaz (Android 13+). Los nombres van en su propio idioma. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker() {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (!AppLanguage.canChoose) {
        Note(str(R.string.demo_language_unsupported))
        return
    }
    val chosen = AppLanguage.chosen(context)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to str(R.string.demo_language_system), "en" to "ENGLISH", "es" to "ESPAÑOL").forEach { (lang, label) ->
            HudButton(label, { AppLanguage.choose(context, lang) }, selected = chosen == lang, minHeight = DemoTouchTarget)
        }
    }
}
