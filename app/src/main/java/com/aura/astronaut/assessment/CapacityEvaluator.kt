package com.aura.astronaut.assessment

import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import com.aura.astronaut.data.Observation
import com.aura.astronaut.data.Origin
import com.aura.astronaut.mission.Capacity
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.ComparisonOp
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.SignalKind
import com.aura.astronaut.mission.WorseWhen

/** Un dato del día que cuenta para una capacidad: una respuesta del cuestionario o una señal. */
data class Cause(
    val indicatorId: String,
    val indicatorName: String,
    val value: Double,
    val unit: String,
    val level: CapacityLevel,
    /** Umbral superado («≥ 4», «≤ −15», «verdadero»); null si el dato está en verde. */
    val threshold: String?,
    val origin: Origin,
    val met: Long,
    val isQuestion: Boolean,
)

data class CapacityState(
    val capacity: Capacity,
    /** GRAY si no hay ningún dato del día: nunca verde por defecto. */
    val level: CapacityLevel,
    val causes: List<Cause>,
) {
    /** Los datos que fijan el nivel (los del peor nivel). */
    val drivers: List<Cause> get() = causes.filter { it.level == level }
}

/** Autoinforme y sensor discrepan: el astronauta dice estar bien y la señal dice lo contrario. */
data class ConsistencyFinding(val capacityId: String, val message: String, val answer: Cause, val signal: Cause)

data class Assessment(
    val flightDay: Int,
    val capacities: List<CapacityState>,
    val consistency: List<ConsistencyFinding>,
    val thresholdsAreDemo: Boolean,
) {
    fun state(capacityId: String): CapacityState? = capacities.firstOrNull { it.capacity.id == capacityId }
}

/**
 * Semáforo por capacidad (AGENTS.md, «Cuestionario diario y semáforo»). Umbrales, capacidades,
 * preguntas y señales salen del paquete; aquí solo está el algoritmo.
 */
object CapacityEvaluator {

    fun evaluate(pkg: MissionPackage, flightDay: Int, observations: List<Observation>): Assessment {
        val today = observations.filter { it.flightDay == flightDay }
        val questionCauses = latestBy(today.filter { it.kind == Observation.KIND_QUESTIONNAIRE })
            .mapNotNull { questionCause(pkg, it) }
        val signalCauses = latestBy(today.filter { it.kind == Observation.KIND_SIGNAL })
            .mapNotNull { signalCause(pkg, it) }

        val capacityOf = buildMap {
            pkg.questionnaire?.items?.forEach { put(it.id, it.capacityId) }
            pkg.signals.forEach { put(it.id, it.capacityId) }
        }
        val all = questionCauses + signalCauses
        val states = pkg.capacities.map { cap ->
            val causes = all.filter { capacityOf[it.indicatorId] == cap.id }
            CapacityState(cap, worst(causes.map { it.level }), causes)
        }

        val consistency = pkg.questionnaire?.consistencyChecks.orEmpty().mapNotNull { check ->
            val answer = questionCauses.firstOrNull { it.indicatorId == check.questionId } ?: return@mapNotNull null
            val signal = signalCauses.firstOrNull { it.indicatorId == check.signalId } ?: return@mapNotNull null
            val signalMatches = when (check.op) {
                ComparisonOp.AT_LEAST -> signal.value >= check.value
                ComparisonOp.AT_MOST -> signal.value <= check.value
            }
            if (answer.value <= check.answerAtMost && signalMatches) {
                ConsistencyFinding(capacityOf[check.questionId] ?: "", check.message.text, answer, signal)
            } else {
                null
            }
        }
        return Assessment(flightDay, states, consistency, pkg.thresholdsAreDemo)
    }

    /** Peor nivel: rojo > ámbar > verde. Sin datos, gris. */
    fun worst(levels: List<CapacityLevel>): CapacityLevel = when {
        levels.isEmpty() -> CapacityLevel.GRAY
        CapacityLevel.RED in levels -> CapacityLevel.RED
        CapacityLevel.AMBER in levels -> CapacityLevel.AMBER
        else -> CapacityLevel.GREEN
    }

    /** Última observación de cada indicador. */
    private fun latestBy(list: List<Observation>): List<Observation> =
        list.groupBy { it.indicator }.values.map { group -> group.maxWith(compareBy<Observation> { it.met }.thenBy { it.id }) }

    private fun questionCause(pkg: MissionPackage, o: Observation): Cause? {
        val item = pkg.questionnaire?.items?.firstOrNull { it.id == o.indicator } ?: return null
        val (level, threshold) = when {
            item.red != null && o.value >= item.red -> CapacityLevel.RED to "≥ ${fmt(item.red)}"
            item.amber != null && o.value >= item.amber -> CapacityLevel.AMBER to "≥ ${fmt(item.amber)}"
            else -> CapacityLevel.GREEN to null
        }
        return Cause(item.id, item.domain.text, o.value, o.unit, level, threshold, o.origin, o.met, isQuestion = true)
    }

    private fun signalCause(pkg: MissionPackage, o: Observation): Cause? {
        val s = pkg.signals.firstOrNull { it.id == o.indicator } ?: return null
        val (level, threshold) = if (s.kind == SignalKind.BOOLEAN) {
            if (o.value != 0.0) (s.whenTrue ?: CapacityLevel.AMBER) to str(R.string.threshold_true) else CapacityLevel.GREEN to null
        } else {
            val worse: (Double) -> Boolean = when (s.worseWhen) {
                WorseWhen.LOWER -> { t -> o.value <= t }
                else -> { t -> o.value >= t }
            }
            val sign = if (s.worseWhen == WorseWhen.LOWER) "≤" else "≥"
            when {
                s.red != null && worse(s.red) -> CapacityLevel.RED to "$sign ${fmt(s.red)}"
                s.amber != null && worse(s.amber) -> CapacityLevel.AMBER to "$sign ${fmt(s.amber)}"
                else -> CapacityLevel.GREEN to null
            }
        }
        return Cause(s.id, s.name.text, o.value, s.unit, level, threshold, o.origin, o.met, isQuestion = false)
    }

    /** «−22», «0,5»: sin decimales innecesarios y con el signo menos tipográfico. */
    fun fmt(v: Double): String {
        val text = if (v == Math.floor(v)) v.toLong().toString() else v.toString().replace('.', ',')
        return text.replace('-', '−')
    }
}
