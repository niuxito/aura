package com.aura.astronaut.ui.hud

import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.astronaut.assessment.CapacityTrend
import com.aura.astronaut.assessment.MeasureStatus
import com.aura.astronaut.assessment.PrepMeasure
import com.aura.astronaut.assessment.TransitionReadiness
import com.aura.astronaut.assessment.Trend
import com.aura.astronaut.hud.HudTexts
import com.aura.astronaut.hud.TransitionAlert
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.Gravity
import com.aura.astronaut.ui.theme.HudButton
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudPanel
import com.aura.astronaut.ui.theme.HudText

private val Small = HudText.Label.copy(fontSize = 13.sp, letterSpacing = 0.3.sp)

fun trendText(t: Trend): String = when (t) {
    Trend.WORSENING -> str(R.string.trend_worsening)
    Trend.STABLE -> str(R.string.trend_stable)
    Trend.IMPROVING -> str(R.string.trend_improving)
    Trend.NO_DATA -> str(R.string.trend_none)
}

fun measureText(s: MeasureStatus): String = when (s) {
    MeasureStatus.DONE -> str(R.string.measure_done)
    MeasureStatus.REPLACED_KEEPS -> str(R.string.measure_replaced_keeps)
    MeasureStatus.REPLACED_LOST -> str(R.string.measure_replaced_lost)
    MeasureStatus.OMITTED_BY_ASTRONAUT -> str(R.string.measure_omitted)
    MeasureStatus.OMITTED_BY_AURA -> str(R.string.measure_omitted_aura)
    MeasureStatus.MISSED -> str(R.string.measure_missed)
    MeasureStatus.NOW -> str(R.string.measure_now)
    MeasureStatus.UPCOMING -> str(R.string.measure_upcoming)
}

/** Preparation summary line: "Preparation: 2 done · 1 lost · 5 to do". */
fun prepSummary(r: TransitionReadiness): String =
    str(R.string.prep_summary, r.done, r.lost, r.ahead)

/** Transition preparation detail: critical capacities day by day, measures and exercise. */
@Composable
fun TransitionReadinessPanel(
    t: TransitionAlert,
    r: TransitionReadiness,
    reference: Map<Gravity, Double>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HudPanel(modifier.widthIn(max = 1000.dp).fillMaxHeight(), modal = true, accent = HudColors.Amber) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.readiness_title), style = HudText.Title, color = HudColors.Amber)
                    Text(
                        "${HudTexts.transitionTitle(t)} · ${HudTexts.transitionChange(t, reference)} · ${HudTexts.transitionWhen(t)}",
                        style = Small,
                        color = HudColors.Text,
                    )
                }
                HudButton(str(R.string.close), onClose)
            }
            Text(
                str(if (r.provisional) R.string.critical_provisional else R.string.critical_from_package),
                style = Small,
                color = HudColors.TextDim,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                item { Text(str(R.string.readiness_indicators), style = HudText.Label, color = HudColors.Cyan) }
                items(r.critical) { TrendRow(it) }
                item {
                    Text(
                        str(R.string.readiness_measures, prepSummary(r)),
                        style = HudText.Label,
                        color = HudColors.Cyan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                items(r.measures) { MeasureRow(it) }
                item {
                    val e = r.exercise
                    Text(str(R.string.readiness_exercise), style = HudText.Label, color = HudColors.Cyan)
                    Text(
                        str(R.string.readiness_exercise_counts, e.planned, e.done, e.omitted, e.missed) +
                            if (e.replaced > 0) " " + str(R.string.readiness_exercise_replaced, e.replaced) else "",
                        style = HudText.Body.copy(fontSize = 16.sp),
                        color = HudColors.Text,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendRow(c: CapacityTrend) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(c.capacity.name.text, style = HudText.Body.copy(fontSize = 16.sp), color = HudColors.Text, modifier = Modifier.width(330.dp))
        c.byDay.forEach { (d, level) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(14.dp).background(levelColor(level), CircleShape))
                Text(d.label.removePrefix("FD"), style = Small.copy(fontSize = 10.sp), color = HudColors.TextDim)
            }
        }
        Text(trendText(c.trend), style = HudText.Label, color = if (c.trend == Trend.WORSENING) HudColors.Amber else HudColors.Text)
    }
}

@Composable
private fun MeasureRow(m: PrepMeasure) {
    val color = when {
        m.status.lost -> HudColors.Amber
        m.status == MeasureStatus.DONE || m.status == MeasureStatus.REPLACED_KEEPS -> HudColors.Green
        else -> HudColors.Text
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${m.day.label} ${m.activity.metLabel}", style = Small, color = HudColors.TextDim, modifier = Modifier.width(130.dp))
        Column(Modifier.weight(1f)) {
            Text("${m.item.code} · ${m.item.name}", style = HudText.Body.copy(fontSize = 16.sp), color = HudColors.Text)
            Text(
                m.item.capabilities.joinToString() +
                    (m.replacement?.let { " · " + str(R.string.measure_replaced_by, it.code, it.name.text) } ?: "") +
                    (m.note?.let { " · " + str(R.string.measure_reason, it) } ?: ""),
                style = Small,
                color = HudColors.TextDim,
            )
        }
        Text(measureText(m.status), style = HudText.Label.copy(fontSize = 13.sp), color = color)
    }
}
