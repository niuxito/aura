package com.aura.astronaut.ui.collect

import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.astronaut.assessment.SelectedQuestion
import com.aura.astronaut.data.ConfirmationStatus
import com.aura.astronaut.hud.PlanItem
import com.aura.astronaut.ui.theme.GloveTouchTarget
import com.aura.astronaut.ui.theme.HudButton
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudPanel
import com.aura.astronaut.ui.theme.HudText

/**
 * Motivos para omitir una actividad, de un toque (con guantes no se escribe).
 * Son opciones de interfaz, no criterios clínicos; el motivo viaja tal cual como evidencia.
 */
val OmitReasons = listOf(
    R.string.omit_reason_operations,
    R.string.omit_reason_fatigue,
    R.string.omit_reason_equipment,
    R.string.omit_reason_mission_control,
    R.string.omit_reason_other,
)

/** Pide el motivo antes de omitir una actividad: omitir siempre lleva motivo. */
@Composable
fun OmitReasonPanel(
    plan: PlanItem,
    onReason: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HudPanel(modifier.widthIn(max = 720.dp), accent = HudColors.Amber, modal = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(str(R.string.omit_title), style = HudText.Title, color = HudColors.Amber)
            Text(
                "${plan.item.code} · ${plan.item.name} (${plan.activity.metLabel})",
                style = HudText.Body,
                color = HudColors.Text,
            )
            Text(str(R.string.omit_why), style = HudText.Label, color = HudColors.TextDim)
            OmitReasons.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { res ->
                        val reason = str(res)
                        HudButton(reason.uppercase(), { onReason(reason) }, accent = HudColors.Amber, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            HudButton(str(R.string.cancel), onCancel, accent = HudColors.Gray, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Cuestionario diario, una pregunta cada vez y una respuesta con un toque.
 * La escala y sus rótulos salen del paquete; el texto de la variante no la incluye.
 */
@Composable
fun QuestionnairePanel(
    title: String,
    questions: List<SelectedQuestion>,
    thresholdsAreDemo: Boolean,
    onSave: (List<Pair<SelectedQuestion, Int>>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by remember { mutableIntStateOf(0) }
    val answers = remember(questions) { mutableStateListOf<Int?>().apply { repeat(questions.size) { add(null) } } }
    val q = questions.getOrNull(index) ?: return
    val allAnswered = answers.none { it == null }
    val last = index == questions.lastIndex

    HudPanel(modifier.widthIn(max = 960.dp), modal = true) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = HudText.Title, color = HudColors.Cyan, modifier = Modifier.weight(1f))
                Text("${index + 1} / ${questions.size}", style = HudText.Title, color = HudColors.Text)
            }
            Text(
                if (q.reason == null) str(R.string.q_fixed_block, q.item.domain.text.uppercase()) else str(R.string.q_why_today, q.reason.toString()),
                style = HudText.Label,
                color = if (q.reason == null) HudColors.TextDim else HudColors.Amber,
            )
            Text(q.variant.text.text, style = HudText.Title.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium), color = HudColors.Text)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (q.item.scale.min..q.item.scale.max).forEach { v ->
                    HudButton(
                        text = "$v",
                        onClick = {
                            answers[index] = v
                            if (!last) index++
                        },
                        selected = answers[index] == v,
                        minHeight = GloveTouchTarget + 8.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row {
                Text("${q.item.scale.min} = ${q.variant.minLabel}", style = HudText.Body, color = HudColors.TextDim, modifier = Modifier.weight(1f))
                Text("${q.item.scale.max} = ${q.variant.maxLabel}", style = HudText.Body, color = HudColors.TextDim)
            }
            Text(
                str(R.string.q_inspired, q.item.inspiredBy.text) +
                    if (thresholdsAreDemo) " " + str(R.string.q_demo_thresholds) else "",
                style = HudText.Label.copy(fontSize = 12.sp, letterSpacing = 0.3.sp),
                color = HudColors.TextDim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(min = GloveTouchTarget)) {
                HudButton(str(R.string.cancel), onCancel, accent = HudColors.Gray)
                HudButton(str(R.string.back), { index-- }, enabled = index > 0)
                Spacer(Modifier.weight(1f))
                if (!last) {
                    HudButton(str(R.string.next), { index++ }, enabled = answers[index] != null)
                } else {
                    HudButton(
                        str(R.string.q_save),
                        { onSave(questions.mapIndexed { i, sq -> sq to answers[i]!! }) },
                        selected = allAnswered,
                        enabled = allAnswered,
                    )
                }
            }
        }
    }
}

/** Qué hacer al confirmar una actividad desde cualquier panel. */
interface ActivityActionHandler {
    /** Actividad del catálogo que es el cuestionario diario: se «hace» respondiéndolo. */
    val questionnaireItemId: String?
    fun done(plan: PlanItem)
    fun omit(plan: PlanItem)
    fun answer(plan: PlanItem)
    fun undo(plan: PlanItem)
}

/**
 * Botones para confirmar una actividad: hecha (o responder, si es el cuestionario) y omitir.
 * Si ya está confirmada, muestra el estado y permite deshacerlo (salvo el cuestionario, cuyas respuestas ya se guardaron).
 */
@Composable
fun ActivityActions(plan: PlanItem, actions: ActivityActionHandler, modifier: Modifier = Modifier) {
    val isQuestionnaire = plan.item.id == actions.questionnaireItemId
    val c = plan.confirmation
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (c == null) {
            if (isQuestionnaire) {
                HudButton(str(R.string.answer), { actions.answer(plan) }, selected = true)
            } else {
                HudButton(str(R.string.mark_done), { actions.done(plan) }, selected = true)
            }
            HudButton(str(R.string.omit), { actions.omit(plan) }, accent = HudColors.Amber)
        } else {
            val text = when (c.status) {
                ConfirmationStatus.DONE -> if (isQuestionnaire) str(R.string.answered) else str(R.string.mark_done)
                ConfirmationStatus.OMITTED -> str(R.string.omitted_reason, c.reason.orEmpty())
            }
            Text(text, style = HudText.Label, color = HudColors.Cyan, modifier = Modifier.weight(1f, fill = false))
            if (!(isQuestionnaire && c.status == ConfirmationStatus.DONE)) {
                HudButton(str(R.string.undo), { actions.undo(plan) }, accent = HudColors.Gray, minHeight = 48.dp)
            }
        }
    }
}
