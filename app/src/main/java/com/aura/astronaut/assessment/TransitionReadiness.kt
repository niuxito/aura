package com.aura.astronaut.assessment

import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import com.aura.astronaut.data.ActivityConfirmation
import com.aura.astronaut.data.ConfirmationStatus
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.data.Observation
import com.aura.astronaut.hud.AdaptationTypes
import com.aura.astronaut.hud.MissionSnapshot
import com.aura.astronaut.mission.Capacity
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.CatalogItem
import com.aura.astronaut.mission.Category
import com.aura.astronaut.mission.FlightDay
import com.aura.astronaut.mission.Gravity
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.PlanActivity

/**
 * Prioridades de la transición (AGENTS.md, «Objetivo final y prioridades»).
 *
 * Qué capacidades son críticas para cada llegada lo dice el paquete (`capacities[].criticalFor`).
 * Si un paquete anterior no lo trae, se usa la solución PROVISIONAL de la misión demo (tolerancia ortostática
 * y equilibrio al llegar a un entorno con gravedad) y la interfaz la etiqueta como provisional.
 */
object TransitionPriorities {
    /** Provisional: capacidades críticas para salir por su pie si el paquete no declara `criticalFor`. */
    val CRITICAL_CAPACITY_IDS = listOf("cardiovascular", "vestibular")

    /**
     * Palabras de `catalog[].capabilities` que indican que una acción prepara esas capacidades.
     * Con ellas, el catálogo demo da: A, V, OIG, SF y T (además de todo lo que tenga `transitionPrep`).
     */
    private val CAPABILITY_KEYWORDS = listOf(
        "ortost", "orthostat", "cardiovascular", "equilibrio", "balance", "sensoriomotor", "sensorimotor", "vestibular",
    )

    /** Nombre corto en el aviso de transición (los del paquete son largos para el HUD). */
    private val SHORT_NAMES = mapOf("cardiovascular" to R.string.short_cardiovascular, "vestibular" to R.string.short_vestibular)

    fun shortName(c: Capacity): String = SHORT_NAMES[c.id]?.let { str(it) } ?: c.name.text

    /** true = el paquete declara las capacidades críticas; false = se usa la lista provisional. */
    fun fromPackage(pkg: MissionPackage): Boolean = pkg.capacities.any { it.criticalFor != null }

    /** Capacidades críticas para salir de la nave al llegar a [to]. */
    fun criticalCapacities(pkg: MissionPackage, to: Gravity): List<Capacity> =
        if (fromPackage(pkg)) {
            pkg.capacities.filter { to in it.criticalFor.orEmpty() }
        } else if (to != Gravity.MICRO) {
            CRITICAL_CAPACITY_IDS.mapNotNull { id -> pkg.capacities.firstOrNull { it.id == id } }
        } else {
            emptyList()
        }

    /** La transición tiene panel de preparación si alguna capacidad es crítica al llegar. */
    fun appliesTo(pkg: MissionPackage, to: Gravity) = criticalCapacities(pkg, to).isNotEmpty()

    /** Acción del catálogo que prepara la transición. */
    fun isPrepMeasure(item: CatalogItem): Boolean =
        item.transitionPrep || item.capabilities.any { cap -> cap.all.any { v -> CAPABILITY_KEYWORDS.any { v.lowercase().contains(it) } } }
}

enum class MeasureStatus(val lost: Boolean) {
    DONE(false),
    /** AURA la cambió por otra que también prepara la transición. */
    REPLACED_KEEPS(false),
    /** AURA la cambió por otra que no la prepara: se pierde el estímulo. */
    REPLACED_LOST(true),
    OMITTED_BY_ASTRONAUT(true),
    OMITTED_BY_AURA(true),
    /** Ya pasó y nadie la confirmó. */
    MISSED(true),
    NOW(false),
    UPCOMING(false),
}

data class PrepMeasure(
    val activity: PlanActivity,
    val day: FlightDay,
    val item: CatalogItem,
    val replacement: CatalogItem?,
    val status: MeasureStatus,
    val note: String?,
)

/** Nivel de una capacidad crítica en cada día de vuelo hasta hoy, y su tendencia. */
data class CapacityTrend(
    val capacity: Capacity,
    val byDay: List<Pair<FlightDay, CapacityLevel>>,
    val trend: Trend,
)

enum class Trend { WORSENING, STABLE, IMPROVING, NO_DATA }

/** Cuenta de lo planificado frente a lo hecho en una categoría, hasta ahora. */
data class Adherence(val planned: Int, val done: Int, val replaced: Int, val omitted: Int, val missed: Int)

data class TransitionReadiness(
    val transitionDay: FlightDay,
    val critical: List<CapacityTrend>,
    val measures: List<PrepMeasure>,
    val exercise: Adherence,
    /** true = capacidades críticas provisionales (el paquete no trae `criticalFor`). */
    val provisional: Boolean = false,
) {
    val done get() = measures.count { it.status == MeasureStatus.DONE || it.status == MeasureStatus.REPLACED_KEEPS }
    val lost get() = measures.count { it.status.lost }
    val ahead get() = measures.count { it.status == MeasureStatus.NOW || it.status == MeasureStatus.UPCOMING }

    companion object {
        /**
         * @return null si la transición no es hacia un entorno con gravedad (p. ej. el despegue).
         */
        fun build(
            pkg: MissionPackage,
            crewId: String,
            met: Long,
            today: FlightDay?,
            transitionDay: FlightDay,
            confirmations: Map<String, ActivityConfirmation>,
            adaptations: Map<String, LocalAdaptation>,
            observations: List<Observation>,
        ): TransitionReadiness? {
            if (!TransitionPriorities.appliesTo(pkg, transitionDay.gravity)) return null
            val catalog = pkg.catalog.associateBy { it.id }
            val days = pkg.days.associateBy { it.day }
            val activities = pkg.plans[crewId].orEmpty()
                .filter { it.day <= transitionDay.day }
                .flatMap { dp -> dp.activities.map { days.getValue(dp.day) to it } }

            val measures = activities.mapNotNull { (day, a) ->
                val item = catalog[a.itemId] ?: return@mapNotNull null
                if (!TransitionPriorities.isPrepMeasure(item)) return@mapNotNull null
                val adaptation = adaptations[a.entryId]
                val replacement = adaptation?.replacementItemId?.let { catalog[it] }
                    ?.takeIf { adaptation.type == AdaptationTypes.SUBSTITUTION }
                val c = confirmations[a.entryId]
                val status = when {
                    adaptation?.type == AdaptationTypes.OMISSION -> MeasureStatus.OMITTED_BY_AURA
                    replacement != null && !TransitionPriorities.isPrepMeasure(replacement) -> MeasureStatus.REPLACED_LOST
                    c?.status == ConfirmationStatus.OMITTED -> MeasureStatus.OMITTED_BY_ASTRONAUT
                    c?.status == ConfirmationStatus.DONE -> if (replacement != null) MeasureStatus.REPLACED_KEEPS else MeasureStatus.DONE
                    replacement != null -> MeasureStatus.REPLACED_KEEPS
                    met < a.met -> MeasureStatus.UPCOMING
                    met < MissionSnapshot.endOf(a, replacement ?: item) -> MeasureStatus.NOW
                    else -> MeasureStatus.MISSED
                }
                PrepMeasure(a, day, item, replacement, status, c?.reason)
            }

            val critical = TransitionPriorities.criticalCapacities(pkg, transitionDay.gravity).map { cap ->
                val upTo = today?.day ?: 0
                val byDay = pkg.days.filter { it.day <= upTo }.map { d ->
                    d to (CapacityEvaluator.evaluate(pkg, d.day, observations).state(cap.id)?.level ?: CapacityLevel.GRAY)
                }
                CapacityTrend(cap, byDay, trendOf(byDay.map { it.second }))
            }

            val exercisePlan = activities.filter { (_, a) -> catalog[a.itemId]?.category == Category.EXERCISE && a.met <= met }
            val exercise = Adherence(
                planned = exercisePlan.size,
                done = exercisePlan.count { (_, a) -> confirmations[a.entryId]?.status == ConfirmationStatus.DONE },
                replaced = exercisePlan.count { (_, a) -> adaptations[a.entryId]?.type == AdaptationTypes.SUBSTITUTION },
                omitted = exercisePlan.count { (_, a) ->
                    confirmations[a.entryId]?.status == ConfirmationStatus.OMITTED || adaptations[a.entryId]?.type == AdaptationTypes.OMISSION
                },
                missed = exercisePlan.count { (_, a) ->
                    confirmations[a.entryId] == null && adaptations[a.entryId]?.type != AdaptationTypes.OMISSION &&
                        met >= MissionSnapshot.endOf(a, catalog[a.itemId])
                },
            )
            return TransitionReadiness(transitionDay, critical, measures, exercise, provisional = !TransitionPriorities.fromPackage(pkg))
        }

        /** Compara los dos últimos días con datos: rojo > ámbar > verde. */
        fun trendOf(levels: List<CapacityLevel>): Trend {
            val withData = levels.filter { it != CapacityLevel.GRAY }
            if (withData.size < 2) return Trend.NO_DATA
            val rank = { l: CapacityLevel -> when (l) { CapacityLevel.RED -> 2; CapacityLevel.AMBER -> 1; else -> 0 } }
            val (prev, last) = withData.takeLast(2).map(rank)
            return when {
                last > prev -> Trend.WORSENING
                last < prev -> Trend.IMPROVING
                else -> Trend.STABLE
            }
        }
    }
}
