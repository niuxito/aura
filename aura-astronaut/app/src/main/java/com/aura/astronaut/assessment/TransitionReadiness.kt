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
 * Transition priorities (AGENTS.md, "Final goal and priorities").
 *
 * Which capacities are critical for each arrival is stated by the package (`capacities[].criticalFor`).
 * If an older package does not carry it, the PROVISIONAL solution of the demo mission is used (orthostatic tolerance
 * and balance when arriving in an environment with gravity) and the interface labels it as provisional.
 */
object TransitionPriorities {
    /** Provisional: capacities critical for walking out on one's own if the package does not declare `criticalFor`. */
    val CRITICAL_CAPACITY_IDS = listOf("cardiovascular", "vestibular")

    /**
     * Words in `catalog[].capabilities` (English or Spanish) indicating that an action prepares those capacities.
     * With them, the demo catalog yields: A, V, OIG, SF and T (plus everything with `transitionPrep`).
     */
    private val CAPABILITY_KEYWORDS = listOf(
        "ortost", "orthostat", "cardiovascular", "equilibrio", "balance", "sensoriomotor", "sensorimotor", "vestibular",
    )

    /** Short name in the transition notice (the package names are long for the HUD). */
    private val SHORT_NAMES = mapOf("cardiovascular" to R.string.short_cardiovascular, "vestibular" to R.string.short_vestibular)

    fun shortName(c: Capacity): String = SHORT_NAMES[c.id]?.let { str(it) } ?: c.name.text

    /** true = the package declares the critical capacities; false = the provisional list is used. */
    fun fromPackage(pkg: MissionPackage): Boolean = pkg.capacities.any { it.criticalFor != null }

    /** Capacities critical for leaving the spacecraft when arriving in [to]. */
    fun criticalCapacities(pkg: MissionPackage, to: Gravity): List<Capacity> =
        if (fromPackage(pkg)) {
            pkg.capacities.filter { to in it.criticalFor.orEmpty() }
        } else if (to != Gravity.MICRO) {
            CRITICAL_CAPACITY_IDS.mapNotNull { id -> pkg.capacities.firstOrNull { it.id == id } }
        } else {
            emptyList()
        }

    /** The transition has a preparation panel if some capacity is critical on arrival. */
    fun appliesTo(pkg: MissionPackage, to: Gravity) = criticalCapacities(pkg, to).isNotEmpty()

    /** Catalog action that prepares the transition. */
    fun isPrepMeasure(item: CatalogItem): Boolean =
        item.transitionPrep || item.capabilities.any { cap -> cap.all.any { v -> CAPABILITY_KEYWORDS.any { v.lowercase().contains(it) } } }
}

enum class MeasureStatus(val lost: Boolean) {
    DONE(false),
    /** AURA replaced it with another one that also prepares the transition. */
    REPLACED_KEEPS(false),
    /** AURA replaced it with one that does not prepare it: the stimulus is lost. */
    REPLACED_LOST(true),
    OMITTED_BY_ASTRONAUT(true),
    OMITTED_BY_AURA(true),
    /** Already past and nobody confirmed it. */
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

/** Level of a critical capacity on each flight day up to today, and its trend. */
data class CapacityTrend(
    val capacity: Capacity,
    val byDay: List<Pair<FlightDay, CapacityLevel>>,
    val trend: Trend,
)

enum class Trend { WORSENING, STABLE, IMPROVING, NO_DATA }

/** Planned versus done count in a category, so far. */
data class Adherence(val planned: Int, val done: Int, val replaced: Int, val omitted: Int, val missed: Int)

data class TransitionReadiness(
    val transitionDay: FlightDay,
    val critical: List<CapacityTrend>,
    val measures: List<PrepMeasure>,
    val exercise: Adherence,
    /** true = provisional critical capacities (the package does not carry `criticalFor`). */
    val provisional: Boolean = false,
) {
    val done get() = measures.count { it.status == MeasureStatus.DONE || it.status == MeasureStatus.REPLACED_KEEPS }
    val lost get() = measures.count { it.status.lost }
    val ahead get() = measures.count { it.status == MeasureStatus.NOW || it.status == MeasureStatus.UPCOMING }

    companion object {
        /**
         * @return null if the transition is not into an environment with gravity (e.g. launch).
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

        /** Compares the last two days with data: red > amber > green. */
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
