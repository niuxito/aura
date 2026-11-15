package com.aura.astronaut.ui.hud

import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.astronaut.data.ActivityConfirmation
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.hud.DayEntry
import com.aura.astronaut.hud.HudTiming
import com.aura.astronaut.ui.collect.ActivityActionHandler
import com.aura.astronaut.ui.collect.ActivityActions
import com.aura.astronaut.hud.DayTimeline
import com.aura.astronaut.hud.HudTexts
import com.aura.astronaut.hud.TimingState
import com.aura.astronaut.mission.Category
import com.aura.astronaut.mission.EntryStatus
import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.Severity
import com.aura.astronaut.mission.ValidationIssue
import com.aura.astronaut.ui.label
import com.aura.astronaut.ui.theme.HudButton
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudPanel
import com.aura.astronaut.ui.theme.HudText

/**
 * Plan completo de un día de vuelo: las actividades del tripulante junto a lo que el
 * cronograma de misión tiene previsto alrededor (bloques y eventos), para entenderlas en contexto.
 */
@Composable
fun DayPlanPanel(
    pkg: MissionPackage,
    crewId: String,
    initialDay: Int,
    met: Long,
    confirmations: Map<String, ActivityConfirmation>,
    adaptations: Map<String, LocalAdaptation>,
    actions: ActivityActionHandler,
    onOpenAdaptations: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dayNumber by rememberSaveable { mutableIntStateOf(initialDay) }
    val day = pkg.days.firstOrNull { it.day == dayNumber } ?: pkg.days.first()
    val timeline = remember(day, met / 60, confirmations, adaptations) {
        DayTimeline.build(pkg, crewId, day, met, confirmations, adaptations)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(day.day) {
        val nowIndex = timeline.entries.indexOfFirst { it is DayEntry.Now }
        listState.scrollToItem((if (nowIndex >= 0) nowIndex - 2 else 0).coerceAtLeast(0))
    }

    HudPanel(modifier.widthIn(max = 820.dp).fillMaxHeight(), modal = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudButton("◀", { dayNumber = (day.day - 1).coerceAtLeast(pkg.days.first().day) }, enabled = day.day > pkg.days.first().day)
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.plan_title, day.label, crewId.uppercase()), style = HudText.Title, color = HudColors.Cyan)
                    Text(
                        "${day.phase} · MET ${day.startMetLabel} – ${day.endMetLabel}",
                        style = HudText.Label,
                        color = HudColors.TextDim,
                    )
                }
                HudButton("▶", { dayNumber = (day.day + 1).coerceAtMost(pkg.days.last().day) }, enabled = day.day < pkg.days.last().day)
                onOpenAdaptations?.let { HudButton(str(R.string.changes), it, accent = HudColors.Amber) }
                HudButton(str(R.string.close), onClose)
            }
            Text(
                str(R.string.plan_timeline_note, pkg.mission.timelineSource),
                style = HudText.Label.copy(fontSize = 12.sp, letterSpacing = 0.3.sp),
                color = HudColors.TextDim,
            )
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                items(timeline.entries) { entry ->
                    when (entry) {
                        is DayEntry.Block -> BlockRow(entry)
                        is DayEntry.Event -> EventRow(entry)
                        is DayEntry.Activity -> ActivityRow(entry, met, actions)
                        is DayEntry.Now -> NowRow(entry.met)
                    }
                }
                val notices = timeline.dayNotices + timeline.missionNotices
                if (notices.isNotEmpty()) {
                    item { Text(str(R.string.plan_mc_warnings), style = HudText.Label, color = HudColors.Amber, modifier = Modifier.padding(top = 8.dp)) }
                    items(notices) { NoticeRow(it) }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(e: DayEntry.Block) {
    val b = e.block
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${b.startMetLabel}–${b.endMetLabel}", style = HudText.Label.copy(fontSize = 14.sp), color = HudColors.Gray, modifier = Modifier.width(190.dp))
        Text(b.name.text, style = HudText.Body.copy(fontSize = 17.sp), color = HudColors.TextDim, modifier = Modifier.weight(1f))
        val tag = if (b.adaptable) str(R.string.block_adaptable, b.adaptableCategories.joinToString { it.label }) else b.kind.label.uppercase()
        Text(tag, style = HudText.Label.copy(fontSize = 12.sp), color = HudColors.Gray)
    }
}

@Composable
private fun EventRow(e: DayEntry.Event) {
    val ev = e.event
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text((if (ev.approx) "~" else "") + ev.metLabel, style = HudText.Label.copy(fontSize = 14.sp), color = HudColors.Cyan, modifier = Modifier.width(190.dp))
        Text("◆ ${ev.name}", style = HudText.Body.copy(fontSize = 17.sp), color = HudColors.Text, modifier = Modifier.weight(1f))
        Text(ev.kind.label.uppercase(), style = HudText.Label.copy(fontSize = 12.sp), color = HudColors.TextDim)
    }
}

@Composable
private fun ActivityRow(e: DayEntry.Activity, met: Long, actions: ActivityActionHandler) {
    val p = e.plan
    val item = p.item
    val past = p.state == TimingState.PAST
    val accent = when (p.state) {
        TimingState.NOW -> HudColors.Cyan
        TimingState.PAST -> HudColors.Gray
        TimingState.UPCOMING -> HudColors.Text
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.activity.metLabel, style = HudText.Body.copy(fontWeight = FontWeight.Bold), color = accent, modifier = Modifier.width(178.dp))
            Text("${item.code} · ${item.name}", style = HudText.Body.copy(fontWeight = FontWeight.Bold), color = accent, modifier = Modifier.weight(1f))
            Text(tagsOf(p), style = HudText.Label.copy(fontSize = 12.sp), color = if (past) HudColors.Gray else HudColors.Amber)
        }
        val details = buildList {
            add(item.category.label)
            add(str(R.string.item_record, item.record.text))
            add(str(R.string.item_limit, item.limit.text))
            if (item.category == Category.SUPPLEMENT) add(str(R.string.item_dose, item.dose?.text ?: str(R.string.item_no_dose)))
            if (item.experimental) add(str(R.string.item_experimental))
        }
        Text(details.joinToString(" · "), style = HudText.Label.copy(fontSize = 13.sp, letterSpacing = 0.2.sp), color = HudColors.TextDim)
        p.adaptation?.let { a ->
            val before = p.originalItem?.let { str(R.string.plan_original, it.code, it.name.text) + " " } ?: ""
            Text(
                str(R.string.plan_changed, "$before${HudTexts.adaptationShortReason(a)} · ${HudTexts.adaptationStatus(a)}"),
                style = HudText.Label.copy(fontSize = 13.sp),
                color = HudColors.Amber,
            )
        }
        if (p.activity.status == EntryStatus.CONDITIONAL) {
            Text(HudTexts.conditionalText(p.activity.note?.text), style = HudText.Label.copy(fontSize = 13.sp), color = HudColors.Amber)
        } else {
            p.activity.note?.let { Text(it.text, style = HudText.Label.copy(fontSize = 13.sp), color = HudColors.TextDim) }
        }
        // Se puede confirmar lo que ya ha empezado (o empieza dentro de la ventana de «ahora»), no lo futuro.
        if (!p.omittedByInterface && (p.confirmation != null || p.activity.met <= met + HudTiming.NOW_WINDOW_SECONDS)) {
            ActivityActions(p, actions, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun NowRow(met: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.width(24.dp), color = HudColors.Cyan, thickness = 2.dp)
        Text(str(R.string.now_met, Met.formatClock(met)), style = HudText.Label, color = HudColors.Cyan)
        HorizontalDivider(Modifier.weight(1f), color = HudColors.Cyan, thickness = 2.dp)
    }
}

@Composable
private fun NoticeRow(v: ValidationIssue) {
    val (tag, color) = when (v.severity) {
        Severity.WARNING -> str(R.string.severity_warning) to HudColors.Amber
        Severity.BLOCKING -> str(R.string.severity_blocker) to HudColors.Red
        Severity.INFO -> str(R.string.severity_info) to HudColors.TextDim
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        Text(tag, style = HudText.Label.copy(fontSize = 13.sp), color = color, modifier = Modifier.width(64.dp))
        Text(
            (v.day?.let { Met.formatFlightDay(it) } ?: str(R.string.mission_word)) + ": " + v.message,
            style = HudText.Body.copy(fontSize = 16.sp),
            color = HudColors.Text,
        )
    }
}
