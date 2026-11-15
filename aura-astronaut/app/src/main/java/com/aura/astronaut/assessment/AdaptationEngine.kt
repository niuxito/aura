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

/** Dato que justifica una adaptación; viaja como `evidence[]` a control de misión. */
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
    /** Regla que la ha producido, para depurar y explicar. */
    val rule: String,
    val evidence: List<EvidenceItem>,
)

/**
 * Único módulo de reglas de adaptación (AGENTS.md, «Cuándo decide»). Todas las reglas solo
 * quitan carga: sustituyen una actividad intensa adaptable por una alternativa no intensa
 * de la misma categoría, o la omiten si no la hay. Nunca tocan algo fijo ni añaden actividades.
 * Cada decisión pasa por [PolicyCheck] antes de salir de aquí.
 */
object AdaptationEngine {

    /** Regla 3: si el siguiente día de vuelo tiene esta carga operativa, no se proponen actividades intensas. */
    val CRITICAL_NEXT_DAY = LoadLevel.CRITICAL

    const val RULE_CRITICAL_NEXT_DAY = "carga-critica-manana"
    const val RULE_CAPACITY = "capacidad-ambar-rojo"
    /** Acción concreta que control de misión fija para una capacidad en ámbar (`capacities[].onAmberActions`). */
    const val RULE_AMBER_ACTION = "accion-ambar"

    /** Sufijo de la regla cuando la adaptación quita una medida que prepara la transición. */
    const val REMOVES_PREP = "+quita-preparacion"

    /**
     * @param assessment semáforo de hoy; null o todo en gris = sin datos: no se adapta por capacidades (regla 1).
     * @param handledEntryIds actividades ya confirmadas o ya adaptadas: no se vuelven a decidir.
     */
    fun decide(
        pkg: MissionPackage,
        crewId: String,
        met: Long,
        day: FlightDay,
        assessment: Assessment?,
        handledEntryIds: Set<String>,
    ): List<AdaptationDecision> {
        // Un semáforo de otro día (p. ej. justo al cambiar de día) no vale como evidencia: se espera al de hoy.
        if (assessment != null && assessment.flightDay != day.day) return emptyList()
        val catalog = pkg.catalog.associateBy { it.id }
        val open = pkg.plans[crewId].orEmpty().firstOrNull { it.day == day.day }?.activities.orEmpty()
            .filter { a ->
                val item = catalog[a.itemId]
                a.adaptable &&
                    item != null &&
                    a.entryId !in handledEntryIds &&
                    // Solo lo que no ha pasado todavía: no se adapta a posteriori.
                    met < MissionSnapshot.endOf(a, item)
            }
        if (open.isEmpty()) return emptyList()

        // Primero, lo que control de misión ha fijado para cada capacidad en ámbar (por ejemplo, el menú con hidratación).
        val amberActions = amberActionDecisions(pkg, crewId, open, catalog, assessment)
        val taken = amberActions.map { it.proposal.entryId }.toSet()

        // Después, la regla genérica: quitar carga a lo intenso.
        val intense = open.filter { catalog[it.itemId]?.intense == true && it.entryId !in taken }
        val trigger = if (intense.isEmpty()) null else trigger(pkg, day, assessment)
        val generic = if (trigger == null) emptyList() else intense.mapNotNull { a -> decisionFor(pkg, crewId, met, a, catalog.getValue(a.itemId), trigger) }

        return (amberActions + generic).filter { PolicyCheck.violations(pkg, it.proposal).isEmpty() }
    }

    /**
     * `capacities[].onAmberActions`: con una capacidad en ámbar, la actividad adaptable de hoy de esa categoría
     * pasa a la acción indicada. Solo si es de la misma categoría y no sube la carga; en rojo no se aplica
     * (la interfaz remite al procedimiento de misión).
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
        // Regla 2 y 5: alguna capacidad con datos en ámbar o rojo. Gris = sin datos: no cuenta.
        val flagged = assessment?.capacities.orEmpty().filter { it.level == CapacityLevel.AMBER || it.level == CapacityLevel.RED }
        if (flagged.isNotEmpty()) {
            val red = flagged.any { it.level == CapacityLevel.RED }
            return Trigger(RULE_CAPACITY, whyOf(flagged), evidenceOf(flagged), red)
        }
        // Regla 3: carga operativa crítica el siguiente día de vuelo.
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
        // Objetivo final: no perder de vista la transición. Si se quita una medida que la prepara, se dice
        // y se deja la decisión a control de misión (la adaptación ya le llega con este motivo).
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

    /** Primera acción del catálogo de la misma categoría, no intensa; prefiere el mismo tipo de carga. */
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
