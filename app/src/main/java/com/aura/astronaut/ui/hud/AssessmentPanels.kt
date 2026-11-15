package com.aura.astronaut.ui.hud

import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.aura.astronaut.hud.HudTiming
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.astronaut.assessment.AdaptationEngine
import com.aura.astronaut.assessment.AlertRules
import com.aura.astronaut.data.AckStatus
import com.aura.astronaut.data.LocalAlert
import com.aura.astronaut.data.ReviewStatus
import com.aura.astronaut.data.SendStatus
import com.aura.astronaut.assessment.Assessment
import com.aura.astronaut.assessment.CapacityState
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.hud.HudTexts
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.CatalogItem
import com.aura.astronaut.mission.Met
import com.aura.astronaut.sync.ManagerClient
import com.aura.astronaut.sync.SyncStatus
import com.aura.astronaut.ui.theme.HudButton
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudPanel
import com.aura.astronaut.ui.theme.HudText
import java.text.DateFormat
import java.util.Date

fun levelColor(l: CapacityLevel): Color = when (l) {
    CapacityLevel.GREEN -> HudColors.Green
    CapacityLevel.AMBER -> HudColors.Amber
    CapacityLevel.RED -> HudColors.Red
    CapacityLevel.GRAY -> HudColors.Gray
}

private val Small = HudText.Label.copy(fontSize = 13.sp, letterSpacing = 0.3.sp)

/**
 * Semáforo compacto: solo las capacidades que necesitan atención (rojo y ámbar), con su dato;
 * el resto en una línea de resumen. En rojo: qué hacer y si control de misión lo ha visto.
 */
@Composable
fun CapacityPanel(
    assessment: Assessment,
    dayLabel: String,
    scenario: com.aura.astronaut.assessment.DemoScenario?,
    alerts: List<LocalAlert>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flagged = assessment.capacities
        .filter { it.level == CapacityLevel.RED || it.level == CapacityLevel.AMBER }
        .sortedBy { if (it.level == CapacityLevel.RED) 0 else 1 }
    val red = flagged.filter { it.level == CapacityLevel.RED }
    val accent = when {
        red.isNotEmpty() -> HudColors.Red
        flagged.isNotEmpty() -> HudColors.Amber
        else -> HudColors.Cyan
    }
    HudPanel(modifier.clickable(onClick = onOpen), accent = accent) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.capacities_title, dayLabel), style = HudText.Label, color = HudColors.Cyan, modifier = Modifier.weight(1f))
                // Etiquetas obligatorias: umbrales de demostración y datos simulados.
                val tags = listOfNotNull(if (assessment.thresholdsAreDemo) str(R.string.tag_demo) else null, scenario?.let { str(R.string.tag_sim) }).joinToString(" · ")
                if (tags.isNotEmpty()) Text(tags, style = Small, color = HudColors.Amber)
            }
            // Solo las que requieren atención, de dos en dos y rotando; el recuento de abajo nunca se oculta.
            val pages = flagged.chunked(HudTiming.CAPACITY_ROWS_VISIBLE)
            val tick by produceState(0, pages.size) {
                while (true) {
                    delay(HudTiming.CAPACITY_ROTATION_MS)
                    value++
                }
            }
            if (pages.isNotEmpty()) {
                val page = tick % pages.size
                Crossfade(targetState = page, label = "capacidades") { p ->
                    Column(Modifier.height(if (pages.size > 1 || pages[0].size > 1) 52.dp else 26.dp)) {
                        pages[p].forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(26.dp)) {
                                Box(Modifier.size(10.dp).background(levelColor(s.level), CircleShape))
                                Text(HudTexts.levelWord(s.level), style = Small.copy(fontWeight = FontWeight.Bold), color = levelColor(s.level), modifier = Modifier.width(46.dp))
                                Text(s.capacity.name.text, style = Small, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(HudTexts.valueText(s.drivers.first()), style = Small.copy(fontWeight = FontWeight.Bold), color = levelColor(s.level))
                            }
                        }
                    }
                }
            }
            val counts = listOf(
                CapacityLevel.RED to R.string.count_red,
                CapacityLevel.AMBER to R.string.count_amber,
                CapacityLevel.GREEN to R.string.count_green,
                CapacityLevel.GRAY to R.string.count_gray,
            ).mapNotNull { (level, res) ->
                assessment.capacities.count { it.level == level }.takeIf { it > 0 }?.let { str(res, it) }
            }
            val allGray = assessment.capacities.all { it.level == CapacityLevel.GRAY }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (allGray) str(R.string.capacities_all_gray) else counts.joinToString(" · "),
                    style = Small,
                    color = HudColors.TextDim,
                    modifier = Modifier.weight(1f),
                )
                if (pages.size > 1) Text("${tick % pages.size + 1}/${pages.size}", style = Small, color = HudColors.TextDim)
            }
            if (red.isNotEmpty()) {
                val related = red.mapNotNull { s -> alerts.lastOrNull { it.kind == AlertRules.KIND_RED && it.capacityId == s.capacity.id } }
                // El estado menos avanzado de los avisos de hoy: no se da por leído lo que no lo está.
                val rank = { al: LocalAlert ->
                    when {
                        al.ackStatus == AckStatus.HANDLED -> 3
                        al.ackStatus == AckStatus.SEEN -> 2
                        al.sendStatus == SendStatus.SENT -> 1
                        else -> 0
                    }
                }
                val least = related.minByOrNull(rank)
                val status = when {
                    least == null -> HudTexts.alertStatusText(AckStatus.NEW, false, null)
                    else -> HudTexts.alertStatusText(least.ackStatus, least.sendStatus == SendStatus.SENT, related.firstNotNullOfOrNull { it.ackNote })
                }
                Text(str(R.string.follow_procedure), style = Small.copy(fontWeight = FontWeight.Bold), color = HudColors.Red)
                Text(
                    status,
                    style = Small,
                    color = if ((least?.let(rank) ?: 0) >= 2) HudColors.Green else HudColors.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun statusColor(a: LocalAdaptation) = when (a.reviewStatus) {
    ReviewStatus.VALIDATED -> HudColors.Green
    ReviewStatus.OBJECTED -> HudColors.Red
    else -> HudColors.TextDim
}

/** Detalle del semáforo: cada dato con valor, umbral y origen, y qué hace AURA en ámbar o rojo. */
@Composable
fun CapacityDetailPanel(assessment: Assessment, dayLabel: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    HudPanel(modifier.widthIn(max = 980.dp).fillMaxHeight(), modal = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.capacity_detail_title, dayLabel), style = HudText.Title, color = HudColors.Cyan, modifier = Modifier.weight(1f))
                HudButton(str(R.string.close), onClose)
            }
            Text(
                str(R.string.demo_thresholds_note),
                style = Small,
                color = HudColors.TextDim,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(assessment.capacities) { s ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(14.dp).background(levelColor(s.level), CircleShape))
                            Text(HudTexts.levelWord(s.level), style = HudText.Label, color = levelColor(s.level))
                            Text(s.capacity.name.text, style = HudText.Body.copy(fontWeight = FontWeight.Bold), color = HudColors.Text)
                        }
                        Text(s.capacity.description.text, style = Small, color = HudColors.TextDim)
                        if (s.causes.isEmpty()) {
                            Text(str(R.string.capacity_detail_no_data), style = Small, color = HudColors.TextDim)
                        }
                        s.causes.forEach { c ->
                            val tag = when {
                                c.isQuestion -> str(R.string.origin_self_report_normalized)
                                else -> if (c.origin == com.aura.astronaut.data.Origin.SIMULATED) str(R.string.simulated_upper) else AdaptationEngine.originWord(c.origin)
                            }
                            Text(
                                "· ${c.indicatorName}: ${HudTexts.valueText(c)} — ${HudTexts.levelWord(c.level)}" +
                                    (c.threshold?.let { " (" + str(R.string.threshold_word, it) + ")" } ?: "") + " · $tag · MET ${Met.format(c.met)}",
                                style = HudText.Body.copy(fontSize = 15.sp),
                                color = levelColor(c.level).takeIf { c.level != CapacityLevel.GREEN } ?: HudColors.Text,
                            )
                        }
                        assessment.consistency.filter { it.capacityId == s.capacity.id }.forEach {
                            Text(str(R.string.consistency_prefix, it.message), style = HudText.Body.copy(fontSize = 15.sp), color = HudColors.Amber)
                        }
                    }
                }
            }
        }
    }
}

/** Todas las adaptaciones con motivo, evidencia, envío y revisión de control de misión. */
@Composable
fun AdaptationsDetailPanel(
    adaptations: List<LocalAdaptation>,
    catalog: Map<String, CatalogItem>,
    sync: SyncStatus,
    onSyncNow: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parser = ManagerClient("")
    HudPanel(modifier.widthIn(max = 980.dp).fillMaxHeight(), modal = true, accent = HudColors.Amber) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(str(R.string.adaptations_title), style = HudText.Title, color = HudColors.Amber, modifier = Modifier.weight(1f))
                HudButton(str(R.string.send_now), onSyncNow)
                HudButton(str(R.string.close), onClose)
            }
            val time = { ms: Long -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms)) }
            Text(
                str(
                    R.string.mission_control_status,
                    sync.url,
                    when {
                        sync.lastError != null -> str(R.string.sync_offline_retry, sync.lastError.take(60))
                        sync.lastOkUtcMs != null -> str(R.string.sync_connected_at, time(sync.lastOkUtcMs))
                        else -> str(R.string.sync_not_checked)
                    },
                ),
                style = Small,
                color = if (sync.lastError != null) HudColors.Amber else HudColors.TextDim,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(adaptations.reversed()) { a ->
                    Column {
                        Text(HudTexts.adaptationSummary(a, catalog), style = HudText.Body.copy(fontWeight = FontWeight.Bold), color = HudColors.Text)
                        Text(str(R.string.decided_at, Met.format(a.decidedAtMet), a.rule), style = Small, color = HudColors.TextDim)
                        Text(a.reason, style = HudText.Body.copy(fontSize = 15.sp), color = HudColors.Text)
                        runCatching { parser.evidenceFromJson(a.evidenceJson) }.getOrDefault(emptyList()).forEach { e ->
                            Text(
                                str(R.string.evidence_line, "${e.indicator} = ${e.value}${e.unit?.let { " $it" } ?: ""} · ${e.source}") +
                                    (e.met?.let { " · MET ${Met.format(it)}" } ?: ""),
                                style = Small,
                                color = HudColors.TextDim,
                            )
                        }
                        Text(HudTexts.adaptationStatus(a), style = HudText.Label, color = statusColor(a))
                        a.withinPolicy?.let { ok ->
                            Text(
                                if (ok) str(R.string.policy_ok)
                                else str(R.string.policy_bad, a.violations ?: str(R.string.policy_out)),
                                style = Small,
                                color = if (ok) HudColors.TextDim else HudColors.Amber,
                            )
                        }
                    }
                }
            }
        }
    }
}
