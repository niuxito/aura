package com.aura.astronaut.assessment

import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import com.aura.astronaut.data.Origin
import com.aura.astronaut.hud.MissionSnapshot
import com.aura.astronaut.mission.AdaptationType
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.CatalogItem
import com.aura.astronaut.mission.FlightDay
import com.aura.astronaut.mission.LoadLevel
import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.PlanActivity

/** Data point that justifies an adaptation; travels to mission control as `evidence[]`. */
data class EvidenceItem(
    val indicator: String,
    val value: String,
    val unit: String?,
    val source: Origin,
    val met: Long?,
)

data class AdaptationDecision(
    val proposal: AdaptationProposal,
    val originalItemId: String,
    /** Rule that produced it, for debugging and explaining. */
    val rule: String,
    val evidence: List<EvidenceItem>,
)

/**
 * The single adaptation rules module (AGENTS.md, "When it decides"). Every rule only
 * removes load: it replaces an intense adaptable activity with a non-intense alternative
 * of the same category, or omits it if there is none. Rules never touch anything fixed nor add activities.
 * Every decision goes through [PolicyCheck] before leaving this module.
 */
object AdaptationEngine {

    /** Rule 3: if the next flight day has this operational load, no intense activities are proposed. */
    val CRITICAL_NEXT_DAY = LoadLevel.CRITICAL

    const val RULE_CRITICAL_NEXT_DAY = "carga-critica-manana"
    const val RULE_CAPACITY = "capacidad-ambar-rojo"
    /** Concrete action mission control sets for a capacity in amber (`capacities[].onAmberActions`). */
    const val RULE_AMBER_ACTION = "accion-ambar"

    /** Rule suffix when the adaptation removes a measure that prepares the transition. */
    const val REMOVES_PREP = "+quita-preparacion"

    /**
     * @param assessment today's traffic light; null or all gray = no data: no capacity-based adaptation (rule 1).
     * @param handledEntryIds activities already confirmed or already adapted: not decided again.
     */
    fun decide(
        pkg: MissionPackage,
        crewId: String,
        met: Long,
        day: FlightDay,
        assessment: Assessment?,
        handledEntryIds: Set<String>,
    ): List<AdaptationDecision> {
        // A traffic light from another day (e.g. right at the day change) is not valid evidence: wait for today's.
        if (assessment != null && assessment.flightDay != day.day) return emptyList()
        val catalog = pkg.catalog.associateBy { it.id }
        val open = pkg.plans[crewId].orEmpty().firstOrNull { it.day == day.day }?.activities.orEmpty()
            .filter { a ->
                val item = catalog[a.itemId]
                a.adaptable &&
                    item != null &&
                    a.entryId !in handledEntryIds &&
                    // Only what has not happened yet: nothing is adapted after the fact.
                    met < MissionSnapshot.endOf(a, item)
            }
        if (open.isEmpty()) return emptyList()

        // First, what mission control has set for each capacity in amber (for example, the hydration menu).
        val amberActions = amberActionDecisions(pkg, crewId, open, catalog, assessment)
        val taken = amberActions.map { it.proposal.entryId }.toSet()

        // Then, the generic rule: remove load from intense activities.
        val intense = open.filter { catalog[it.itemId]?.intense == true && it.entryId !in taken }
        val trigger = if (intense.isEmpty()) null else trigger(pkg, day, assessment)
        val generic = if (trigger == null) emptyList() else intense.mapNotNull { a -> decisionFor(pkg, crewId, met, a, catalog.getValue(a.itemId), trigger) }

        return (amberActions + generic).filter { PolicyCheck.violations(pkg, it.proposal).isEmpty() }
    }

    /**
     * `capacities[].onAmberActions`: with a capacity in amber, today's adaptable activity of that category
     * switches to the given action. Only if it is the same category and does not raise the load; in red it does
     * not apply (the interface refers to the mission procedure).
     */
    private fun amberActionDecisions(
        pkg: MissionPackage,
        crewId: String,
        open: List<PlanActivity>,
        catalog: Map<String, CatalogItem>,
        assessment: Assessment?,
    ): List<AdaptationDecision> {
        if (AdaptationType.SUBSTITUTION !in pkg.adaptationPolicy.allowed) return emptyList()
        val actionsOf = pkg.capacities.associate { it.id to it.onAmberActions }
        val amber = assessment?.capacities.orEmpty().filter { it.level == CapacityLevel.AMBER }
        val decided = mutableMapOf<String, AdaptationDecision>()
        for (state in amber) {
            for (action in actionsOf[state.capacity.id].orEmpty()) {
                val replacement = catalog[action.replacementItemId] ?: continue
                if (replacement.category != action.category) continue
                for (a in open) {
                    val item = catalog.getValue(a.itemId)
                    if (a.entryId in decided || item.category != action.category || item.id == replacement.id) continue
                    if (replacement.intense && !item.intense) continue
                    val proposal = AdaptationProposal(
                        crewId, a.entryId, AdaptationType.SUBSTITUTION, replacementItemId = replacement.id,
                        reason = str(
                            R.string.reason_amber_action, whyOf(listOf(state)), item.code, item.name.text, a.metLabel,
                            replacement.code, replacement.name.text, state.capacity.name.text,
                        ),
                    )
                    decided[a.entryId] = AdaptationDecision(proposal, item.id, RULE_AMBER_ACTION, evidenceOf(listOf(state)))
                }
            }
        }
        return decided.values.toList()
    }

    private fun whyOf(flagged: List<CapacityState>): String = flagged.joinToString("; ") { s ->
        val d = s.drivers.firstOrNull() ?: return@joinToString str(R.string.reason_capacity_level, s.capacity.name.text, levelWord(s.level))
        str(
            R.string.reason_capacity, s.capacity.name.text, levelWord(s.level), d.indicatorName, CapacityEvaluator.fmt(d.value),
            unitText(d.unit), d.threshold.orEmpty(), originWord(d.origin), Met.format(d.met),
        )
    }

    private fun evidenceOf(flagged: List<CapacityState>): List<EvidenceItem> =
        flagged.flatMap { it.drivers }.distinctBy { it.indicatorId }.map {
            EvidenceItem(it.indicatorName, CapacityEvaluator.fmt(it.value).replace('−', '-'), it.unit.ifBlank { null }, it.origin, it.met)
        }

    private data class Trigger(val rule: String, val why: String, val evidence: List<EvidenceItem>, val red: Boolean)

    private fun trigger(pkg: MissionPackage, day: FlightDay, assessment: Assessment?): Trigger? {
        // Rules 2 and 5: some capacity with data in amber or red. Gray = no data: does not count.
        val flagged = assessment?.capacities.orEmpty().filter { it.level == CapacityLevel.AMBER || it.level == CapacityLevel.RED }
        if (flagged.isNotEmpty()) {
            val red = flagged.any { it.level == CapacityLevel.RED }
            return Trigger(RULE_CAPACITY, whyOf(flagged), evidenceOf(flagged), red)
        }
        // Rule 3: critical operational load on the next flight day.
        val next = pkg.days.firstOrNull { it.day == day.day + 1 }
        if (next != null && next.opLoad == CRITICAL_NEXT_DAY) {
            return Trigger(
                RULE_CRITICAL_NEXT_DAY,
                str(R.string.reason_critical_next_day, next.label, next.milestone.text),
                listOf(EvidenceItem(str(R.string.evidence_planned_load, next.label, pkg.packageVersion), "critical", null, Origin.MANUAL, null)),
                red = false,
            )
        }
        return null
    }

    private fun decisionFor(
        pkg: MissionPackage,
        crewId: String,
        met: Long,
        a: PlanActivity,
        item: CatalogItem,
        t: Trigger,
    ): AdaptationDecision? {
        val replacement = lowerLoadAlternative(pkg, item)
        val allowed = pkg.adaptationPolicy.allowed
        val red = if (t.red) " " + str(R.string.reason_red_tail) else ""
        // Final goal: never lose sight of the transition. If a measure that prepares it is removed, say so
        // and leave the decision to mission control (the adaptation already reaches it with this reason).
        val removesPrep = TransitionPriorities.isPrepMeasure(item) && (replacement == null || !TransitionPriorities.isPrepMeasure(replacement))
        val prep = if (removesPrep) {
            " " + str(R.string.reason_prep_tail, item.code, item.capabilities.joinToString { it.text })
        } else {
            ""
        }
        val tail = red + prep
        val proposal = when {
            replacement != null && AdaptationType.SUBSTITUTION in allowed -> AdaptationProposal(
                crewId, a.entryId, AdaptationType.SUBSTITUTION, replacementItemId = replacement.id,
                reason = str(
                    R.string.reason_substitution, t.why, item.code, item.name.text, a.metLabel, replacement.code, replacement.name.text,
                ) + tail,
            )
            AdaptationType.OMISSION in allowed -> AdaptationProposal(
                crewId, a.entryId, AdaptationType.OMISSION,
                reason = str(R.string.reason_omission, t.why, item.code, item.name.text, a.metLabel) + tail,
            )
            else -> return null
        }
        return AdaptationDecision(proposal, item.id, t.rule + if (removesPrep) REMOVES_PREP else "", t.evidence)
    }

    /** First non-intense catalog action of the same category; prefers the same load type. */
    fun lowerLoadAlternative(pkg: MissionPackage, item: CatalogItem): CatalogItem? {
        val options = pkg.catalog.filter { it.category == item.category && !it.intense && it.id != item.id }
        return options.firstOrNull { it.loadType == item.loadType } ?: options.firstOrNull()
    }

    fun levelWord(l: CapacityLevel) = str(
        when (l) {
            CapacityLevel.GREEN -> R.string.level_word_green
            CapacityLevel.AMBER -> R.string.level_word_amber
            CapacityLevel.RED -> R.string.level_word_red
            CapacityLevel.GRAY -> R.string.level_word_gray
        },
    )

    fun originWord(o: Origin) = str(
        when (o) {
            Origin.MANUAL -> R.string.origin_manual
            Origin.SENSOR -> R.string.origin_sensor
            Origin.SIMULATED -> R.string.origin_simulated
        },
    )

    private fun unitText(unit: String) = when {
        unit.isBlank() -> ""
        unit == "%" -> " %"
        unit.contains('–') -> " " + str(R.string.unit_scale, unit)
        else -> " $unit"
    }
}
