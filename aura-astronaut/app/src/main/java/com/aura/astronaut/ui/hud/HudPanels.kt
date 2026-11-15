package com.aura.astronaut.ui.hud

import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.aura.astronaut.assessment.TransitionPriorities
import com.aura.astronaut.hud.HudTiming
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.ui.shortLabel
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.astronaut.assessment.TransitionReadiness
import com.aura.astronaut.data.ConfirmationStatus
import com.aura.astronaut.hud.AdaptationTypes
import com.aura.astronaut.hud.HudTexts
import com.aura.astronaut.hud.MissionSnapshot
import com.aura.astronaut.hud.PlanItem
import com.aura.astronaut.hud.TimingState
import com.aura.astronaut.hud.TransitionAlert
import com.aura.astronaut.mission.ChecksumStatus
import com.aura.astronaut.mission.ClockSettings
import com.aura.astronaut.mission.ClockSource
import com.aura.astronaut.mission.CrewMember
import com.aura.astronaut.mission.EntryStatus
import com.aura.astronaut.mission.Gravity
import com.aura.astronaut.mission.LoadedPackage
import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionPhase
import com.aura.astronaut.ui.gravityText
import com.aura.astronaut.ui.collect.ActivityActionHandler
import com.aura.astronaut.ui.collect.ActivityActions
import com.aura.astronaut.ui.label
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudPanel
import com.aura.astronaut.ui.theme.HudText

/** Una página de la rotación del panel de misión: dos líneas de altura fija. */
private data class MissionPage(val first: String, val second: String, val warn: Boolean = false)

/**
 * Contexto de misión. Fijo: MET, modo demo y «SIM» (la misión simulada se indica siempre).
 * Rotativo cada [HudTiming.MISSION_ROTATION_MS]: día y entorno, hito del día, misión y tripulante.
 */
@Composable
fun MissionContextPanel(
    loaded: LoadedPackage,
    crew: CrewMember?,
    snapshot: MissionSnapshot,
    clock: ClockSettings,
    launchUtcKnown: Boolean,
    modifier: Modifier = Modifier,
) {
    val pkg = loaded.pkg
    val day = snapshot.day
    val pages = buildList {
        val phase = when (val p = snapshot.phase) {
            is MissionPhase.InFlight -> "${p.day.label} · ${p.day.phase}"
            MissionPhase.BeforeLaunch -> str(R.string.phase_before_launch)
            MissionPhase.AfterEnd -> str(R.string.phase_after_end)
            MissionPhase.Unscheduled -> str(R.string.phase_unscheduled)
        }
        val env = listOfNotNull(
            snapshot.gravityNow?.let { gravityText(pkg.gravityReference, it) },
            day?.let { str(R.string.op_load, it.opLoad.label) },
        ).joinToString(" · ")
        add(MissionPage(phase, env))
        day?.let { add(MissionPage(str(R.string.page_milestone), it.milestone.text)) }
        add(MissionPage(pkg.mission.name.text, listOfNotNull(crew?.name, str(R.string.package_version, pkg.packageVersion)).joinToString(" · ")))
        if (loaded.checksum == ChecksumStatus.MISMATCH) add(MissionPage(str(R.string.page_package_warning), ChecksumStatus.MISMATCH.label, warn = true))
    }
    val tick by produceState(0) {
        while (true) {
            delay(HudTiming.MISSION_ROTATION_MS)
            value++
        }
    }
    val index = tick % pages.size

    HudPanel(modifier) {
        Column {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MET ${Met.formatClock(snapshot.met)}", style = HudText.Title.copy(fontSize = 28.sp), color = HudColors.Cyan)
                val tags = listOfNotNull(
                    when {
                        clock.source == ClockSource.LAUNCH_UTC && launchUtcKnown -> null
                        !clock.running -> "DEMO ‖"
                        clock.rate == 1 -> "DEMO"
                        else -> "DEMO ×${clock.rate}"
                    },
                    if (pkg.simulated) str(R.string.tag_sim) else null,
                ).joinToString(" · ")
                if (tags.isNotEmpty()) {
                    Text(tags, style = HudText.Label.copy(fontSize = 13.sp), color = HudColors.Amber, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Crossfade(targetState = pages[index], label = "mision") { page ->
                Column(Modifier.height(54.dp)) {
                    Text(page.first, style = HudText.Body, color = if (page.warn) HudColors.Amber else HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(page.second, style = HudText.Body.copy(fontSize = 17.sp), color = HudColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                pages.indices.forEach { i ->
                    Box(Modifier.size(6.dp).background(if (i == index) HudColors.Cyan else HudColors.Gray.copy(alpha = 0.4f), CircleShape))
                }
            }
        }
    }
}

/** Aviso destacado de la próxima transición gravitatoria: cuenta atrás, indicadores para la salida y preparación. */
@Composable
fun TransitionBanner(
    t: TransitionAlert,
    reference: Map<Gravity, Double>,
    readiness: TransitionReadiness? = null,
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    HudPanel(modifier.clickable(enabled = readiness != null, onClick = onOpen), accent = HudColors.Amber) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val what = if (t.exact && t.eventName != null) t.eventName.uppercase() else str(R.string.banner_transition_day, t.day.label)
            Text(str(R.string.banner_in, what, HudTexts.formatDuration(t.secondsLeft)), style = HudText.Label.copy(fontSize = 18.sp), color = HudColors.Amber)
            Text(
                "${t.from.shortLabel} → ${gravityText(reference, t.to)} · T− ${Met.formatClock(t.secondsLeft)}",
                style = HudText.Body.copy(fontSize = 16.sp),
                color = HudColors.Text,
            )
            readiness?.let { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    r.critical.forEach { c ->
                        val today = c.byDay.lastOrNull()?.second ?: CapacityLevel.GRAY
                        Box(Modifier.size(9.dp).background(levelColor(today), CircleShape))
                        Text(
                            "${TransitionPriorities.shortName(c.capacity)} ${HudTexts.levelWord(today)}",
                            style = HudText.Label.copy(fontSize = 13.sp),
                            color = levelColor(today),
                        )
                    }
                }
                Text(
                    str(R.string.banner_prep, r.lost, r.ahead),
                    style = HudText.Label.copy(fontSize = 13.sp),
                    color = if (r.lost > 0) HudColors.Amber else HudColors.TextDim,
                )
            }
        }
    }
}

/** Etiquetas de una actividad del plan, siempre en texto. */
fun tagsOf(p: PlanItem): String = buildList {
    val c = p.confirmation
    when {
        p.omittedByInterface -> add(str(R.string.status_omitted_by_aura))
        c?.status == ConfirmationStatus.DONE -> add(str(R.string.status_done))
        c?.status == ConfirmationStatus.OMITTED -> add(str(R.string.status_omitted))
        p.state == TimingState.NOW -> add(str(R.string.status_now))
        p.state == TimingState.PAST -> add(str(R.string.status_unconfirmed))
    }
    when (p.adaptation?.type) {
        AdaptationTypes.SUBSTITUTION -> add(str(R.string.status_changed_by_aura))
        AdaptationTypes.RESCHEDULE -> add(str(R.string.status_moved_by_aura))
        else -> Unit
    }
    if (p.activity.status == EntryStatus.CONDITIONAL) add(str(R.string.status_conditional))
    add(if (p.activity.adaptable) str(R.string.status_adaptable) else str(R.string.status_fixed))
}.joinToString(" · ")

/**
 * Qué está pasando ahora en el cronograma y cuál es la siguiente acción recomendada, con su porqué.
 * Al tocarlo se abre el plan completo del día.
 */
@Composable
fun NowAndNextPanel(
    snapshot: MissionSnapshot,
    actions: ActivityActionHandler,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = snapshot.nextAction
    val accent = if (next != null && next.secondsUntil <= 0) HudColors.Cyan else HudColors.Amber
    HudPanel(modifier.clickable(onClick = onOpenPlan), accent = accent) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nowLine(snapshot), style = HudText.Label, color = HudColors.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.heightIn(min = 2.dp))
            if (next == null) {
                Text(str(R.string.no_more_activities), style = HudText.Body, color = HudColors.TextDim)
                return@Column
            }
            val day = if (next.laterDay) "${next.plan.day.label} " else ""
            Text(
                (if (next.secondsUntil <= 0) str(R.string.next_now_upper) else str(R.string.next_in_upper, HudTexts.formatDuration(next.secondsUntil))) +
                    " · $day${next.plan.activity.metLabel}",
                style = HudText.Label,
                color = if (next.secondsUntil <= 0) HudColors.Cyan else HudColors.Amber,
            )
            Text(
                HudTexts.nextActionName(next),
                style = HudText.Title.copy(fontSize = 21.sp),
                color = HudColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Solo lo que cambia lo que hay que hacer: cambiada, o condicional.
            val notes = listOfNotNull(
                next.plan.originalItem?.let { str(R.string.next_changed_from, it.code, it.name.text) },
                if (next.plan.activity.status == EntryStatus.CONDITIONAL) str(R.string.conditional_word) else null,
            )
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), style = HudText.Label.copy(fontSize = 14.sp), color = HudColors.Amber, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Cuando toca, se confirma aquí mismo, con un toque.
            if (next.secondsUntil <= 0) ActivityActions(next.plan, actions, Modifier.padding(top = 6.dp))
        }
    }
}

private fun nowLine(s: MissionSnapshot): String = when (s.phase) {
    MissionPhase.BeforeLaunch -> str(R.string.now_before_launch)
    MissionPhase.AfterEnd -> str(R.string.now_after_end)
    else -> {
        val blocks = s.currentBlocks
        if (blocks.isNotEmpty()) {
            val until = blocks.minOf { it.endMet }
            str(R.string.now_blocks, blocks.joinToString(" + ") { it.name.text }, Met.format(until))
        } else {
            val nb = s.nextBlock
            str(R.string.now_no_block) + (nb?.let { " · " + str(R.string.now_then, it.name.text, it.startMetLabel) } ?: "")
        }
    }
}
