package com.aura.astronaut.hud

import com.aura.astronaut.R
import com.aura.astronaut.assessment.Cause
import com.aura.astronaut.i18n.str
import com.aura.astronaut.assessment.CapacityEvaluator
import com.aura.astronaut.assessment.CapacityState
import com.aura.astronaut.data.AckStatus
import com.aura.astronaut.data.AlertCodes
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.data.ReviewStatus
import com.aura.astronaut.data.LocalAlert
import com.aura.astronaut.data.SendStatus
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.CatalogItem
import com.aura.astronaut.mission.EntryStatus
import com.aura.astronaut.mission.Gravity
import com.aura.astronaut.mission.Met
import com.aura.astronaut.ui.gravityText
import com.aura.astronaut.ui.label

/**
 * Un mensaje que el HUD muestra y que la voz puede leer. La voz no tiene textos propios:
 * siempre lee el [text] de un anuncio que también está en pantalla.
 */
data class Announcement(val id: String, val text: String, val priority: Int)

/**
 * Único sitio donde se redactan los mensajes del HUD que también se dicen en voz alta.
 * Las prioridades deciden qué mensaje puede interrumpir a otro.
 */
object HudTexts {
    const val PRIORITY_RED = 35
    const val PRIORITY_TRANSITION = 30
    const val PRIORITY_ADAPTATION = 25
    const val PRIORITY_NEXT_ACTION = 20
    const val PRIORITY_REVIEW = 15

    // ---------- Transición gravitatoria ----------

    /** «Amerizaje en 1 d 6 h» o, si el paquete no da el instante, «Transición antes del fin de FD10, en 6 h». */
    fun transitionTitle(t: TransitionAlert): String =
        if (t.exact && t.eventName != null) {
            str(R.string.transition_title_exact, t.eventName, formatDuration(t.secondsLeft))
        } else {
            str(R.string.transition_title_day, t.day.label, formatDuration(t.secondsLeft))
        }

    /** «De microgravedad a Tierra · 1 g». */
    fun transitionChange(t: TransitionAlert, reference: Map<Gravity, Double>): String =
        str(R.string.transition_change, t.from.label.lowercase(), gravityText(reference, t.to))

    /** Cuándo, en MET: «MET 09/01:42:48 · T− 02/08:42:39». */
    fun transitionWhen(t: TransitionAlert): String =
        (if (t.exact) "MET ${Met.format(t.met)}" else str(R.string.transition_end_of_day, t.day.label, Met.format(t.met))) +
            " · T− ${Met.formatClock(t.secondsLeft)}"

    fun transitionAnnouncement(t: TransitionAlert, reference: Map<Gravity, Double>) = Announcement(
        id = "transition-${t.day.day}",
        text = str(R.string.transition_announcement, transitionTitle(t), transitionChange(t, reference)),
        priority = PRIORITY_TRANSITION,
    )

    // ---------- Siguiente acción ----------

    /** Cuándo: «Toca ahora» o «Siguiente acción, en 2 h 15 min». */
    fun nextActionWhen(n: NextAction): String =
        if (n.secondsUntil <= 0) str(R.string.next_now) else str(R.string.next_in, formatDuration(n.secondsUntil))

    /** Qué: «F · Cuestionario de fatiga, descanso y síntomas». */
    fun nextActionName(n: NextAction): String = "${n.plan.item.code} · ${n.plan.item.name}"

    /** Frase completa que se muestra (en dos líneas) y que dice la voz. */
    fun nextActionTitle(n: NextAction): String = "${nextActionWhen(n)}: ${nextActionName(n)}"

    /** Justificación: de dónde sale la recomendación y qué condiciones tiene. */
    fun nextActionReason(n: NextAction): String {
        val a = n.plan.activity
        val parts = mutableListOf<String>()
        if (n.laterDay) parts += str(R.string.next_none_left_today)
        parts += str(R.string.next_in_plan, n.plan.day.label, a.metLabel)
        if (a.status == EntryStatus.CONDITIONAL) parts += conditionalText(a.note?.text)
        return parts.joinToString(" ")
    }

    fun conditionalText(note: String?): String =
        str(R.string.conditional_text, note ?: str(R.string.conditional_unknown))

    fun nextActionAnnouncement(n: NextAction): Announcement {
        val conditional = if (n.plan.activity.status == EntryStatus.CONDITIONAL) " " + str(R.string.conditional_short) else ""
        return Announcement(
            id = "next-${n.plan.activity.entryId}-${if (n.secondsUntil <= 0) "now" else "soon"}",
            text = nextActionTitle(n) + "." + conditional,
            priority = PRIORITY_NEXT_ACTION,
        )
    }

    /** Lo que la voz debe anunciar en este momento: la transición (si la hay) y la siguiente acción. */
    fun announcementsFor(s: MissionSnapshot, reference: Map<Gravity, Double>): List<Announcement> = buildList {
        s.transition?.let { add(transitionAnnouncement(it, reference)) }
        s.nextAction?.let { add(nextActionAnnouncement(it)) }
    }

    // ---------- Semáforo y adaptaciones (paso 5) ----------

    fun levelWord(l: CapacityLevel): String = when (l) {
        CapacityLevel.GREEN -> str(R.string.level_green)
        CapacityLevel.AMBER -> str(R.string.level_amber)
        CapacityLevel.RED -> str(R.string.level_red)
        CapacityLevel.GRAY -> str(R.string.level_gray)
    }

    /** Motivo corto de un estado, siempre en texto: el color nunca va solo. */
    fun capacityReason(s: CapacityState): String = when (s.level) {
        CapacityLevel.GRAY -> str(R.string.capacity_no_data)
        CapacityLevel.GREEN -> str(R.string.capacity_no_deviation)
        else -> s.drivers.first().let { d -> "${d.indicatorName}: ${valueText(d)} (${d.threshold})" }
    }

    /** Valor de un dato con su unidad: «−22 %», «11 lpm», «6/7» (cuestionario, normalizado), «sí» (booleana). */
    fun valueText(d: Cause): String {
        val v = CapacityEvaluator.fmt(d.value)
        return when {
            !d.isQuestion && d.unit.isBlank() -> if (d.value != 0.0) str(R.string.value_yes) else str(R.string.value_no)
            d.unit == "%" -> "$v %"
            d.unit.contains('–') -> "$v/${d.unit.substringAfter('–')}"
            d.unit.isBlank() -> v
            else -> "$v ${d.unit}"
        }
    }

    fun redAnnouncement(day: Int, s: CapacityState) = Announcement(
        id = "red-$day-${s.capacity.id}",
        text = str(R.string.red_announcement, s.capacity.name.text, s.capacity.onRed.text),
        priority = PRIORITY_RED,
    )

    /** «A · Acondicionamiento aeróbico → RB · Resistencia con bandas». */
    fun adaptationSummary(a: LocalAdaptation, catalog: Map<String, CatalogItem>): String {
        val original = catalog[a.originalItemId]?.let { "${it.code} · ${it.name}" } ?: a.originalItemId
        return when (a.type) {
            AdaptationTypes.SUBSTITUTION -> {
                val r = catalog[a.replacementItemId]?.let { "${it.code} · ${it.name}" } ?: a.replacementItemId
                "$original → $r"
            }
            AdaptationTypes.OMISSION -> str(R.string.adaptation_omitted, original)
            else -> str(R.string.adaptation_moved, original, a.newMet?.let { Met.format(it) }.orEmpty())
        }
    }

    /**
     * Motivo resumido: la primera frase de la razón, sin los datos (««Fatiga y recuperación» en ámbar»).
     * El motor redacta la razón como «indicadores. Qué se cambia…» en cualquier idioma.
     */
    fun adaptationShortReason(a: LocalAdaptation): String =
        a.reason.substringBefore(". ").split("; ").joinToString(", ") { it.substringBefore(":").trim() }

    fun adaptationAnnouncement(a: LocalAdaptation, catalog: Map<String, CatalogItem>) = Announcement(
        id = "adapt-${a.id}",
        text = str(R.string.adaptation_announcement, adaptationSummary(a, catalog), adaptationShortReason(a)),
        priority = PRIORITY_ADAPTATION,
    )

    /** Estado de envío y revisión, en texto. */
    fun adaptationStatus(a: LocalAdaptation): String = when {
        a.reviewStatus == ReviewStatus.VALIDATED -> str(R.string.adaptation_validated)
        a.reviewStatus == ReviewStatus.OBJECTED ->
            a.reviewNote?.let { str(R.string.adaptation_objected_note, it) } ?: str(R.string.adaptation_objected)
        a.sendStatus == SendStatus.SENT -> str(R.string.adaptation_sent)
        else -> str(R.string.adaptation_queued)
    }

    fun reviewAnnouncement(a: LocalAdaptation, catalog: Map<String, CatalogItem>): Announcement? = when (a.reviewStatus) {
        ReviewStatus.VALIDATED, ReviewStatus.OBJECTED -> Announcement(
            id = "review-${a.id}-${a.reviewStatus}",
            text = str(
                if (a.reviewStatus == ReviewStatus.VALIDATED) R.string.review_validated else R.string.review_objected,
                adaptationSummary(a, catalog),
            ) + noteSuffix(a.reviewNote),
            priority = PRIORITY_REVIEW,
        )
        else -> null
    }

    const val PRIORITY_ALERT_ACK = 16

    /**
     * Estado de un aviso a control de misión, en frase completa:
     * pendiente de envío → recibido → leído (lo saben) → respondido (han actuado, con su nota).
     */
    fun alertStatus(a: LocalAlert): String = alertStatusText(a.ackStatus, a.sendStatus == SendStatus.SENT, a.ackNote)

    fun alertStatusText(ack: String, sent: Boolean, note: String?): String = when {
        ack == AckStatus.HANDLED -> note?.let { str(R.string.alert_replied_note, it) } ?: str(R.string.alert_replied)
        ack == AckStatus.SEEN -> note?.let { str(R.string.alert_read_note, it) } ?: str(R.string.alert_read)
        sent -> str(R.string.alert_received)
        else -> str(R.string.alert_pending)
    }

    /** De qué trata el aviso, para la voz: «de Fatiga y recuperación en rojo». */
    fun alertSubject(a: LocalAlert, capacityName: String?): String = when (a.kind) {
        AlertCodes.KIND_RED -> str(R.string.alert_subject_red, capacityName ?: str(R.string.alert_subject_capacity))
        AlertCodes.KIND_PREP -> str(R.string.alert_subject_prep)
        else -> str(R.string.alert_subject_inconsistency)
    }

    fun alertAckAnnouncement(a: LocalAlert, capacityName: String?): Announcement? = when (a.ackStatus) {
        AckStatus.SEEN, AckStatus.HANDLED -> Announcement(
            id = "alert-${a.id}-${a.ackStatus}",
            text = str(
                if (a.ackStatus == AckStatus.SEEN) R.string.alert_ack_read else R.string.alert_ack_replied,
                alertSubject(a, capacityName),
            ) + noteSuffix(a.ackNote),
            priority = PRIORITY_ALERT_ACK,
        )
        else -> null
    }

    // ---------- Utilidades ----------

    private fun noteSuffix(note: String?): String = note?.let { " " + str(R.string.note_suffix, it) }.orEmpty()

    /** Duración legible con las dos unidades más significativas: «1 d 6 h», «2 h 15 min», «menos de 1 min». */
    fun formatDuration(seconds: Long): String {
        if (seconds < 60) return str(R.string.duration_under_minute)
        val d = seconds / Met.DAY
        val h = (seconds % Met.DAY) / Met.HOUR
        val m = (seconds % Met.HOUR) / Met.MINUTE
        val parts = buildList {
            if (d > 0) add("$d d")
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
        }
        return parts.take(2).joinToString(" ")
    }
}
