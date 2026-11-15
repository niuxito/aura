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

/** Situación de una actividad del plan respecto al MET actual (no dice si se ha hecho). */
enum class TimingState { PAST, NOW, UPCOMING }

data class PlanItem(
    val activity: PlanActivity,
    val item: CatalogItem,
    val day: FlightDay,
    val state: TimingState,
    /** Lo que el astronauta ha confirmado (hecha u omitida); null = sin confirmar. */
    val confirmation: ActivityConfirmation? = null,
    /** Adaptación aplicada a bordo por la interfaz; [item] ya es la acción resultante. */
    val adaptation: LocalAdaptation? = null,
    /** Acción del plan original si la interfaz la ha sustituido. */
    val originalItem: CatalogItem? = null,
) {
    val omittedByInterface: Boolean get() = adaptation?.type == AdaptationTypes.OMISSION
    val pending: Boolean get() = confirmation == null && !omittedByInterface
}

/** Valores de `type` de una adaptación, tal como viajan a control de misión. */
object AdaptationTypes {
    const val SUBSTITUTION = "substitution"
    const val RESCHEDULE = "reschedule"
    const val OMISSION = "omission"
}

/**
 * Aplica a bordo las adaptaciones de la interfaz sobre una actividad del plan:
 * sustituye la acción, mueve el MET u omite, y conserva la original para mostrarla.
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
 * Próxima transición gravitatoria. [exact] = el paquete la sitúa en un hito concreto
 * (el despegue al empezar el día, el amerizaje al acabarlo); si no, solo se sabe que se
 * completa antes del fin del día de vuelo.
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
    /** Segundos hasta su MET; cero o negativo si ya toca. */
    val secondsUntil: Long,
    /** true si no quedan actividades hoy y la siguiente es de otro día de vuelo. */
    val laterDay: Boolean,
)

/** Todo lo que el HUD necesita saber en un MET, calculado solo a partir del paquete. */
data class MissionSnapshot(
    val met: Long,
    val phase: MissionPhase,
    val day: FlightDay?,
    /** Entorno gravitatorio en este momento. */
    val gravityNow: Gravity?,
    /** Bloques del cronograma de la tripulación que contienen el MET actual. */
    val currentBlocks: List<TimelineBlock>,
    val nextBlock: TimelineBlock?,
    val transition: TransitionAlert?,
    val todayPlan: List<PlanItem>,
    val nextAction: NextAction?,
    /** Avisos e informaciones de control de misión vigentes hoy para este tripulante. */
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
            // La siguiente acción es la primera sin confirmar que aún no ha pasado.
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
         * Fin previsto de una actividad: la duración del catálogo de la acción que se hace (la sustituta, si la hay),
         * o el `endMet` del paquete; si no hay ninguno, la ventana de demostración [HudTiming.NOW_WINDOW_SECONDS].
         */
        fun endOf(a: PlanActivity, item: CatalogItem?): Long =
            item?.durationMin?.let { a.met + it * 60L } ?: a.endMet ?: (a.met + HudTiming.NOW_WINDOW_SECONDS)

        /**
         * Momento de la transición de un día con `transitionFrom`: `transitionMet` si el paquete lo trae.
         * Si no, el hito que cae justo al acabar o al empezar el día (amerizaje, despegue); y si tampoco,
         * el fin del día, porque `gravity` es el entorno al acabar el día de vuelo.
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
