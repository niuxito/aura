package com.aura.astronaut.assessment

import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import com.aura.astronaut.data.AlertCodes
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.hud.HudTexts
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.CatalogItem

/** Una alerta que debe conocer control de misión. [dedupeKey] identifica la situación: se avisa una vez. */
data class AlertCandidate(
    val dedupeKey: String,
    /** `capacity-red`, `preparation-lost` o `inconsistency`. */
    val kind: String,
    /** `red` o `warning`. */
    val level: String,
    val capacityId: String?,
    val entryId: String?,
    val flightDay: Int?,
    val message: String,
    val evidence: List<EvidenceItem>,
    /** Si ya viene en JSON (la de una adaptación), se envía tal cual. */
    val evidenceJson: String? = null,
)

/**
 * Qué se avisa a control de misión (AGENTS.md, regla 5 y «Objetivo final y prioridades»):
 * lo que la interfaz no resuelve adaptando. Lo que solo está en ámbar no se avisa aparte:
 * AURA ya lo resuelve adaptando y la adaptación llega con su evidencia.
 */
object AlertRules {
    const val KIND_RED = AlertCodes.KIND_RED
    const val KIND_PREP = AlertCodes.KIND_PREP
    const val KIND_CONSISTENCY = AlertCodes.KIND_CONSISTENCY

    fun candidates(
        assessment: Assessment?,
        adaptations: List<LocalAdaptation>,
        catalog: Map<String, CatalogItem>,
    ): List<AlertCandidate> = buildList {
        assessment?.let { a ->
            // Capacidad en rojo: una alerta por capacidad y día de vuelo.
            a.capacities.filter { it.level == CapacityLevel.RED }.forEach { s ->
                add(
                    AlertCandidate(
                        dedupeKey = "rojo:${a.flightDay}:${s.capacity.id}",
                        kind = KIND_RED,
                        level = AlertCodes.LEVEL_RED,
                        capacityId = s.capacity.id,
                        entryId = null,
                        flightDay = a.flightDay,
                        message = str(R.string.alert_message_red, s.capacity.name.text, HudTexts.capacityReason(s), s.capacity.onRed.text),
                        evidence = s.drivers.map(::evidenceOf),
                    ),
                )
            }
            // Incoherencia: el astronauta dice estar bien y el sensor indica lo contrario.
            a.consistency.forEach { f ->
                add(
                    AlertCandidate(
                        dedupeKey = "incoherencia:${a.flightDay}:${f.capacityId}:${f.answer.indicatorId}",
                        kind = KIND_CONSISTENCY,
                        level = AlertCodes.LEVEL_WARNING,
                        capacityId = f.capacityId,
                        entryId = null,
                        flightDay = a.flightDay,
                        message = f.message,
                        evidence = listOf(evidenceOf(f.answer), evidenceOf(f.signal)),
                    ),
                )
            }
        }
        // Adaptación que quita una medida de preparación de la transición: decide control de misión.
        adaptations.filter { it.rule.endsWith(AdaptationEngine.REMOVES_PREP) }.forEach { ad ->
            add(
                AlertCandidate(
                    dedupeKey = "preparacion:${ad.entryId}",
                    kind = KIND_PREP,
                    level = AlertCodes.LEVEL_WARNING,
                    capacityId = null,
                    entryId = ad.entryId,
                    flightDay = null,
                    message = str(R.string.alert_message_prep, HudTexts.adaptationSummary(ad, catalog)),
                    evidence = emptyList(),
                    evidenceJson = ad.evidenceJson,
                ),
            )
        }
    }

    private fun evidenceOf(c: Cause) = EvidenceItem(
        indicator = c.indicatorName,
        value = CapacityEvaluator.fmt(c.value).replace('−', '-'),
        unit = c.unit.ifBlank { null },
        source = c.origin,
        met = c.met,
    )
}
