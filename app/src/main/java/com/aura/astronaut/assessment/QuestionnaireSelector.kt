package com.aura.astronaut.assessment

import com.aura.astronaut.mission.ComparisonOp
import com.aura.astronaut.mission.FlightDay
import com.aura.astronaut.mission.Gravity
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.QuestionBlock
import com.aura.astronaut.mission.QuestionItem
import com.aura.astronaut.mission.QuestionTrigger
import com.aura.astronaut.mission.QuestionVariant
import com.aura.astronaut.mission.SignalKind

/** Una pregunta elegida para el cuestionario de hoy, con la redacción que toca y por qué se hace. */
data class SelectedQuestion(
    val item: QuestionItem,
    val variant: QuestionVariant,
    /** null en el bloque fijo; en el banco, el `reason` del disparador que la ha elegido. */
    val reason: String?,
)

/**
 * Selección de las preguntas del cuestionario diario según AGENTS.md («Cuestionario diario y semáforo»).
 * No hay preguntas ni umbrales en el código: todo sale de `pkg.questionnaire`.
 */
object QuestionnaireSelector {

    /**
     * @param latestSignals última medida de cada señal por id (las booleanas, 1.0 = verdadera).
     *        Sin medida, un disparador de señal no dispara.
     */
    fun select(
        pkg: MissionPackage,
        crewId: String,
        day: FlightDay,
        latestSignals: Map<String, Double> = emptyMap(),
    ): List<SelectedQuestion> {
        val q = pkg.questionnaire ?: return emptyList()
        val crewIndex = pkg.crew.indexOfFirst { it.id == crewId }.coerceAtLeast(0)

        // Bloque fijo: en el orden del paquete; variante = (día + índice del tripulante + índice de la pregunta) % variantes.
        val fixed = q.items.filter { it.block == QuestionBlock.FIXED }.take(q.fixedPerDay)
            .mapIndexed { i, item ->
                SelectedQuestion(item, item.variants[(day.day + crewIndex + i) % item.variants.size], reason = null)
            }

        // Banco: disparadores que se cumplen hoy, por prioridad descendente; preguntas distintas.
        val bank = q.items.filter { it.block == QuestionBlock.BANK }
        val fired = bank.flatMap { item ->
            item.triggers
                .filter { it !is QuestionTrigger.Filler && fires(it, pkg, day, latestSignals) }
                .map { item to it }
        }.sortedByDescending { it.second.priority }
        val chosen = LinkedHashMap<String, Pair<QuestionItem, QuestionTrigger>>()
        fired.forEach { (item, t) -> if (chosen.size < q.bankPerDay && item.id !in chosen) chosen[item.id] = item to t }

        // Si no llegan, relleno por prioridad.
        if (chosen.size < q.bankPerDay) {
            bank.flatMap { item -> item.triggers.filterIsInstance<QuestionTrigger.Filler>().map { item to it } }
                .sortedByDescending { it.second.priority }
                .forEach { (item, t) -> if (chosen.size < q.bankPerDay && item.id !in chosen) chosen[item.id] = item to t }
        }

        val bankSelected = chosen.values.map { (item, t) ->
            SelectedQuestion(item, item.variants[day.day % item.variants.size], reason = t.reason.text)
        }
        return fixed + bankSelected
    }

    fun fires(t: QuestionTrigger, pkg: MissionPackage, today: FlightDay, signals: Map<String, Double>): Boolean = when (t) {
        is QuestionTrigger.Transition -> pkg.days.any { d ->
            val from = d.transitionFrom ?: return@any false
            from != d.gravity &&
                (t.toGravity.isEmpty() || d.gravity in t.toGravity) &&
                today.day in (d.day - t.daysBefore)..(d.day + t.daysAfter) &&
                (t.minDaysInMicro == null || microDaysBefore(pkg, d) >= t.minDaysInMicro)
        }
        is QuestionTrigger.Environment -> today.gravity in t.gravity
        is QuestionTrigger.DaysInMicro -> microDaysUpTo(pkg, today) >= t.min
        // «En los últimos withinDays días de vuelo»: desde hoy hasta withinDays días de vuelo antes.
        is QuestionTrigger.Event -> pkg.events.any { e ->
            e.kind == t.eventKind && e.day != null && e.day in (today.day - t.withinDays)..today.day
        }
        is QuestionTrigger.SignalValue -> signalFires(t, pkg, signals)
        is QuestionTrigger.Filler -> false
    }

    private fun signalFires(t: QuestionTrigger.SignalValue, pkg: MissionPackage, signals: Map<String, Double>): Boolean {
        val value = signals[t.signalId] ?: return false
        val signal = pkg.signals.firstOrNull { it.id == t.signalId } ?: return false
        if (signal.kind == SignalKind.BOOLEAN) return value != 0.0
        val threshold = t.value ?: return false
        return when (t.op) {
            ComparisonOp.AT_LEAST -> value >= threshold
            ComparisonOp.AT_MOST -> value <= threshold
            null -> false
        }
    }

    /** Días de vuelo seguidos en microgravedad hasta [today], incluido. */
    fun microDaysUpTo(pkg: MissionPackage, today: FlightDay): Int =
        pkg.days.filter { it.day <= today.day }.sortedByDescending { it.day }
            .takeWhile { it.gravity == Gravity.MICRO }.size

    /** Días de vuelo seguidos en microgravedad justo antes del día de la transición [d]. */
    private fun microDaysBefore(pkg: MissionPackage, d: FlightDay): Int =
        pkg.days.filter { it.day < d.day }.sortedByDescending { it.day }
            .takeWhile { it.gravity == Gravity.MICRO }.size

    /** Normalización: en todo el sistema alto = peor. Las variantes en positivo se invierten. */
    fun normalize(item: QuestionItem, variant: QuestionVariant, answer: Int): Double =
        if (variant.inverted) (item.scale.max + item.scale.min - answer).toDouble() else answer.toDouble()
}
