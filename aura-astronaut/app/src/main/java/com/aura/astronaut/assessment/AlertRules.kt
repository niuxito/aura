package com.aura.astronaut.assessment

import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import com.aura.astronaut.data.AlertCodes
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.hud.HudTexts
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.CatalogItem

/** An alert mission control must know about. [dedupeKey] identifies the situation: it is reported once. */
data class AlertCandidate(
    val dedupeKey: String,
    /** `capacity-red`, `preparation-lost` or `inconsistency`. */
    val kind: String,
    /** `red` or `warning`. */
    val level: String,
    val capacityId: String?,
    val entryId: String?,
    val flightDay: Int?,
    val message: String,
    val evidence: List<EvidenceItem>,
    /** If it already comes as JSON (an adaptation's), it is sent as is. */
    val evidenceJson: String? = null,
)

/**
 * What is reported to mission control (AGENTS.md, rule 5 and "Final goal and priorities"):
 * what the interface does not resolve by adapting. What is only amber is not reported separately:
 * AURA already resolves it by adapting and the adaptation arrives with its evidence.
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
            // Capacity in red: one alert per capacity and flight day.
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
            // Inconsistency: the astronaut says they are fine and the sensor indicates otherwise.
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
        // Adaptation that removes a transition preparation measure: mission control decides.
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
