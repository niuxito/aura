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

/** A question chosen for today's questionnaire, with the wording due today and why it is asked. */
data class SelectedQuestion(
    val item: QuestionItem,
    val variant: QuestionVariant,
    /** null in the fixed block; in the bank, the `reason` of the trigger that chose it. */
    val reason: String?,
)

/**
 * Selection of the daily questionnaire questions according to AGENTS.md ("Daily questionnaire and traffic light").
 * There are no questions or thresholds in the code: everything comes from `pkg.questionnaire`.
 */
object QuestionnaireSelector {

    /**
     * @param latestSignals latest measurement of each signal by id (booleans: 1.0 = true).
     *        With no measurement, a signal trigger does not fire.
     */
    fun select(
        pkg: MissionPackage,
        crewId: String,
        day: FlightDay,
        latestSignals: Map<String, Double> = emptyMap(),
    ): List<SelectedQuestion> {
        val q = pkg.questionnaire ?: return emptyList()
        val crewIndex = pkg.crew.indexOfFirst { it.id == crewId }.coerceAtLeast(0)

        // Fixed block: in package order; variant = (day + crew member index + question index) % variants.
        val fixed = q.items.filter { it.block == QuestionBlock.FIXED }.take(q.fixedPerDay)
            .mapIndexed { i, item ->
                SelectedQuestion(item, item.variants[(day.day + crewIndex + i) % item.variants.size], reason = null)
            }

        // Bank: triggers that hold today, by descending priority; distinct questions.
        val bank = q.items.filter { it.block == QuestionBlock.BANK }
        val fired = bank.flatMap { item ->
            item.triggers
                .filter { it !is QuestionTrigger.Filler && fires(it, pkg, day, latestSignals) }
                .map { item to it }
        }.sortedByDescending { it.second.priority }
        val chosen = LinkedHashMap<String, Pair<QuestionItem, QuestionTrigger>>()
        fired.forEach { (item, t) -> if (chosen.size < q.bankPerDay && item.id !in chosen) chosen[item.id] = item to t }

        // If there are not enough, fillers by priority.
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
        // "In the last withinDays flight days": from today back to withinDays flight days earlier.
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

    /** Consecutive flight days in microgravity up to [today], inclusive. */
    fun microDaysUpTo(pkg: MissionPackage, today: FlightDay): Int =
        pkg.days.filter { it.day <= today.day }.sortedByDescending { it.day }
            .takeWhile { it.gravity == Gravity.MICRO }.size

    /** Consecutive flight days in microgravity right before the transition day [d]. */
    private fun microDaysBefore(pkg: MissionPackage, d: FlightDay): Int =
        pkg.days.filter { it.day < d.day }.sortedByDescending { it.day }
            .takeWhile { it.gravity == Gravity.MICRO }.size

    /** Normalization: across the system high = worse. Positively phrased variants are inverted. */
    fun normalize(item: QuestionItem, variant: QuestionVariant, answer: Int): Double =
        if (variant.inverted) (item.scale.max + item.scale.min - answer).toDouble() else answer.toDouble()
}
