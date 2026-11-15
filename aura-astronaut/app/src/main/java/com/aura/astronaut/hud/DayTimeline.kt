package com.aura.astronaut.hud

import com.aura.astronaut.data.ActivityConfirmation
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.mission.FlightDay
import com.aura.astronaut.mission.MissionEvent
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.TimelineBlock
import com.aura.astronaut.mission.ValidationIssue

/** A line of a day's full plan, sorted by MET. */
sealed interface DayEntry {
    val met: Long

    data class Block(val block: TimelineBlock) : DayEntry {
        override val met get() = block.startMet
    }

    data class Event(val event: MissionEvent) : DayEntry {
        override val met get() = event.met
    }

    data class Activity(val plan: PlanItem) : DayEntry {
        override val met get() = plan.activity.met
    }

    /** Position of the current MET within the day. */
    data class Now(override val met: Long) : DayEntry
}

data class DayTimeline(
    val day: FlightDay,
    val entries: List<DayEntry>,
    /** Mission control warnings and information for this day and crew member. */
    val dayNotices: List<ValidationIssue>,
    /** Those that affect the whole mission. */
    val missionNotices: List<ValidationIssue>,
) {
    companion object {
        fun build(
            pkg: MissionPackage,
            crewId: String,
            day: FlightDay,
            met: Long,
            confirmations: Map<String, ActivityConfirmation> = emptyMap(),
            adaptations: Map<String, LocalAdaptation> = emptyMap(),
        ): DayTimeline {
            val catalog = pkg.catalog.associateBy { it.id }
            // Blocks may cross the boundary between days: every block overlapping the day is included.
            val blocks = pkg.blocks
                .filter { it.startMet < day.endMet && it.endMet > day.startMet }
                .map { DayEntry.Block(it) }
            val events = pkg.events.filter { it.day == day.day }.map { DayEntry.Event(it) }
            val activities = pkg.plans[crewId].orEmpty()
                .firstOrNull { it.day == day.day }?.activities.orEmpty()
                .mapNotNull { a ->
                    planItemOf(a, catalog, day, met, confirmations[a.entryId], adaptations[a.entryId])
                        ?.let { DayEntry.Activity(it) }
                }
            val inDay = met >= day.startMet && met < day.endMet
            val now = if (inDay) listOf(DayEntry.Now(met)) else emptyList()
            // At the same MET: block, event, activity and, last, the "now" mark.
            val order = { e: DayEntry ->
                when (e) {
                    is DayEntry.Block -> 0
                    is DayEntry.Event -> 1
                    is DayEntry.Activity -> 2
                    is DayEntry.Now -> 3
                }
            }
            val entries = (blocks + events + activities + now).sortedWith(compareBy<DayEntry> { it.met }.thenBy(order))
            val relevant = pkg.validation.filter { it.crewId == null || it.crewId == crewId }
            return DayTimeline(
                day = day,
                entries = entries,
                dayNotices = relevant.filter { it.day == day.day },
                missionNotices = relevant.filter { it.day == null },
            )
        }
    }
}
