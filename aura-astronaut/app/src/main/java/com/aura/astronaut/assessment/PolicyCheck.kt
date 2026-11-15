package com.aura.astronaut.assessment

import com.aura.astronaut.mission.AdaptationType
import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionPackage

/** The minimum of an adaptation needed to check it against the policy. */
data class AdaptationProposal(
    val crewId: String,
    val entryId: String,
    val type: AdaptationType,
    val replacementItemId: String? = null,
    val newMet: Long? = null,
    val reason: String,
)

/**
 * On-device replica of AURA Manager's `checkPolicy` (lib/adaptations.ts), plus what the
 * server takes for granted (allowed type and reason). A proposal with violations is not applied.
 */
object PolicyCheck {

    fun violations(pkg: MissionPackage, p: AdaptationProposal): List<String> = buildList {
        val policy = pkg.adaptationPolicy
        if (p.type !in policy.allowed) add("The adaptation type is not allowed by adaptationPolicy.")
        if (p.reason.isBlank()) add("Every adaptation carries its reason.")

        val day = pkg.plans[p.crewId]?.firstOrNull { d -> d.activities.any { it.entryId == p.entryId } }
        val activity = day?.activities?.firstOrNull { it.entryId == p.entryId }
        if (day == null || activity == null) {
            add("The activity is not in that crew member's plan.")
            return@buildList
        }
        val item = pkg.catalog.firstOrNull { it.id == activity.itemId }
        if (!activity.adaptable) add("The activity is marked as fixed: only mission control can change it.")

        when (p.type) {
            AdaptationType.SUBSTITUTION -> {
                val replacement = pkg.catalog.firstOrNull { it.id == p.replacementItemId }
                if (replacement == null) {
                    add("The replacement action does not exist in the package catalog.")
                } else {
                    if (item != null && replacement.category != item.category) add("The replacement action belongs to another category.")
                    if (item != null && replacement.intense && !item.intense) add("The substitution increases the load: it introduces a demanding action.")
                }
            }
            AdaptationType.RESCHEDULE -> {
                val newMet = p.newMet
                if (newMet == null) {
                    add("A reschedule needs newMet.")
                } else {
                    val fd = pkg.days.firstOrNull { it.day == day.day }
                    if (fd == null || newMet < fd.startMet || newMet >= fd.endMet) {
                        add("The new MET ${Met.format(newMet)} falls outside the original flight day.")
                    }
                    val inWindow = item != null && pkg.blocks.any { b ->
                        item.category in b.adaptableCategories && newMet >= b.startMet && newMet < b.endMet
                    }
                    if (!inWindow) add("The new MET ${Met.format(newMet)} is not within an adaptable window of its category.")
                }
            }
            AdaptationType.OMISSION -> Unit
        }
    }
}
