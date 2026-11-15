package com.aura.astronaut.hud

import com.aura.astronaut.data.ActivityConfirmation
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.mission.CatalogItem
import com.aura.astronaut.mission.EventKind
import com.aura.astronaut.mission.FlightDay
import com.aura.astronaut.mission.Gravity
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.MissionPhase
import com.aura.astronaut.mission.PlanActivity
import com.aura.astronaut.mission.TimelineBlock
import com.aura.astronaut.mission.ValidationIssue
import com.aura.astronaut.mission.phaseAt

/** State of a plan activity relative to the current MET (it does not say whether it was done). */
enum class TimingState { PAST, NOW, UPCOMING }

data class PlanItem(
    val activity: PlanActivity,
    val item: CatalogItem,
    val day: FlightDay,
    val state: TimingState,
    /** What the astronaut confirmed (done or omitted); null = not confirmed. */
    val confirmation: ActivityConfirmation? = null,
    /** Adaptation applied on board by the interface; [item] is already the resulting action. */
    val adaptation: LocalAdaptation? = null,
    /** Original plan action if the interface replaced it. */
    val originalItem: CatalogItem? = null,
) {
    val omittedByInterface: Boolean get() = adaptation?.type == AdaptationTypes.OMISSION
    val pending: Boolean get() = confirmation == null && !omittedByInterface
}

/** Values of an adaptation's `type`, as they travel to mission control. */
object AdaptationTypes {
    const val SUBSTITUTION = "substitution"
    const val RESCHEDULE = "reschedule"
    const val OMISSION = "omission"
}

/**
 * Applies the interface's adaptations to a plan activity on board:
 * replaces the action, moves the MET or omits it, and keeps the original to show it.
 */
fun planItemOf(
    a: PlanActivity,
    catalog: Map<String, CatalogItem>,
    day: FlightDay,
    now: Long,
    confirmation: ActivityConfirmation?,
    adaptation: LocalAdaptation?,
): PlanItem? {
    val original = catalog[a.itemId] ?: return null
    val replacement = adaptation?.replacementItemId?.let { catalog[it] }
        ?.takeIf { adaptation.type == AdaptationTypes.SUBSTITUTION }
    val effective = adaptation?.newMet?.takeIf { adaptation.type == AdaptationTypes.RESCHEDULE }
        ?.let { a.copy(met = it, metLabel = com.aura.astronaut.mission.Met.format(it)) } ?: a
    return PlanItem(
        activity = effective,
        item = replacement ?: original,
        day = day,
        state = MissionSnapshot.timingOf(effective.met, MissionSnapshot.endOf(effective, replacement ?: original), now),
        confirmation = confirmation,
        adaptation = adaptation,
        originalItem = if (replacement != null) original else null,
    )
}

/**
 * Next gravity transition. [exact] = the package places it at a specific milestone
 * (launch at the start of the day, splashdown at its end); otherwise it is only known to
 * be completed before the end of the flight day.
 */
data class TransitionAlert(
    val day: FlightDay,
    val from: Gravity,
    val to: Gravity,
    val met: Long,
    val eventName: String?,
    val exact: Boolean,
    val secondsLeft: Long,
)

data class NextAction(
    val plan: PlanItem,
    /** Seconds until its MET; zero or negative if it is due. */
    val secondsUntil: Long,
    /** true if there are no activities left today and the next one is on another flight day. */
    val laterDay: Boolean,
)

/** Everything the HUD needs to know at a MET, computed only from the package. */
data class MissionSnapshot(
    val met: Long,
    val phase: MissionPhase,
    val day: FlightDay?,
    /** Gravity environment at this moment. */
    val gravityNow: Gravity?,
    /** Crew timeline blocks containing the current MET. */
    val currentBlocks: List<TimelineBlock>,
    val nextBlock: TimelineBlock?,
    val transition: TransitionAlert?,
    val todayPlan: List<PlanItem>,
    val nextAction: NextAction?,
    /** Mission control warnings and information in force today for this crew member. */
    val notices: List<ValidationIssue>,
) {
    companion object {
        fun build(
            pkg: MissionPackage,
            crewId: String,
            met: Long,
            confirmations: Map<String, ActivityConfirmation> = emptyMap(),
            adaptations: Map<String, LocalAdaptation> = emptyMap(),
        ): MissionSnapshot {
            val phase = pkg.phaseAt(met)
            val day = (phase as? MissionPhase.InFlight)?.day
            val catalog = pkg.catalog.associateBy { it.id }
            val daysByNumber = pkg.days.associateBy { it.day }

            val allPlan = pkg.plans[crewId].orEmpty().flatMap { dayPlan ->
                val planDay = daysByNumber[dayPlan.day] ?: return@flatMap emptyList()
                dayPlan.activities.mapNotNull { a ->
                    planItemOf(a, catalog, planDay, met, confirmations[a.entryId], adaptations[a.entryId])
                }
            }.sortedBy { it.activity.met }

            val todayPlan = if (day == null) emptyList() else allPlan.filter { it.day.day == day.day }
            // The next action is the first unconfirmed one that has not passed yet.
            val next = allPlan.firstOrNull { it.pending && it.state != TimingState.PAST }?.let {
                NextAction(it, it.activity.met - met, laterDay = day != null && it.day.day != day.day)
            }

            return MissionSnapshot(
                met = met,
                phase = phase,
                day = day,
                gravityNow = gravityAt(pkg, phase, met),
                currentBlocks = pkg.blocks.filter { met >= it.startMet && met < it.endMet },
                nextBlock = pkg.blocks.firstOrNull { it.startMet > met },
                transition = nextTransition(pkg, phase, met),
                todayPlan = todayPlan,
                nextAction = next,
                notices = pkg.validation.filter { v ->
                    (v.crewId == null || v.crewId == crewId) && (v.day == null || v.day == day?.day)
                },
            )
        }

        fun timingOf(activityMet: Long, endMet: Long, now: Long): TimingState = when {
            now < activityMet -> TimingState.UPCOMING
            now < endMet -> TimingState.NOW
            else -> TimingState.PAST
        }

        /**
         * Planned end of an activity: the catalog duration of the action being done (the replacement, if any),
         * or the package's `endMet`; if neither exists, the demonstration window [HudTiming.NOW_WINDOW_SECONDS].
         */
        fun endOf(a: PlanActivity, item: CatalogItem?): Long =
            item?.durationMin?.let { a.met + it * 60L } ?: a.endMet ?: (a.met + HudTiming.NOW_WINDOW_SECONDS)

        /**
         * Moment of the transition on a day with `transitionFrom`: `transitionMet` if the package carries it.
         * Otherwise, the milestone right at the end or start of the day (splashdown, launch); failing that,
         * the end of the day, because `gravity` is the environment at the end of the flight day.
         */
        fun transitionMoment(pkg: MissionPackage, day: FlightDay): Triple<Long, String?, Boolean> {
            val milestones = pkg.events.filter { it.kind == EventKind.MILESTONE }
            day.transitionMet?.let { at ->
                val name = milestones.firstOrNull { it.met == at }?.name?.text ?: pkg.events.firstOrNull { it.met == at }?.name?.text
                return Triple(at, name, true)
            }
            milestones.firstOrNull { it.met == day.endMet }?.let { return Triple(it.met, it.name.text, true) }
            milestones.firstOrNull { it.met == day.startMet }?.let { return Triple(it.met, it.name.text, true) }
            return Triple(day.endMet, null, false)
        }

        private fun nextTransition(pkg: MissionPackage, phase: MissionPhase, met: Long): TransitionAlert? {
            val currentDay = when (phase) {
                is MissionPhase.InFlight -> phase.day.day
                MissionPhase.BeforeLaunch -> pkg.days.first().day
                else -> return null
            }
            return pkg.days
                .asSequence()
                .filter { it.transitionFrom != null && it.day in currentDay..currentDay + HudTiming.TRANSITION_LOOKAHEAD_DAYS }
                .map { d ->
                    val (moment, name, exact) = transitionMoment(pkg, d)
                    TransitionAlert(d, d.transitionFrom!!, d.gravity, moment, name, exact, moment - met)
                }
                .firstOrNull { it.secondsLeft > 0 }
        }

        private fun gravityAt(pkg: MissionPackage, phase: MissionPhase, met: Long): Gravity? = when (phase) {
            is MissionPhase.InFlight -> {
                val d = phase.day
                val from = d.transitionFrom
                if (from != null && met < transitionMoment(pkg, d).first) from else d.gravity
            }
            MissionPhase.BeforeLaunch -> pkg.days.first().transitionFrom
            MissionPhase.AfterEnd -> pkg.days.last().gravity
            MissionPhase.Unscheduled -> null
        }
    }
}
