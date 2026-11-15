package com.aura.astronaut.assessment

import com.aura.astronaut.mission.AdaptationType
import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionPackage

/** Lo mínimo de una adaptación para comprobarla contra la política. */
data class AdaptationProposal(
    val crewId: String,
    val entryId: String,
    val type: AdaptationType,
    val replacementItemId: String? = null,
    val newMet: Long? = null,
    val reason: String,
)

/**
 * Réplica en el dispositivo de `checkPolicy` de AURA Manager (lib/adaptations.ts), más lo que
 * el servidor da por supuesto (tipo permitido y motivo). Una propuesta con violaciones no se aplica.
 */
object PolicyCheck {

    fun violations(pkg: MissionPackage, p: AdaptationProposal): List<String> = buildList {
        val policy = pkg.adaptationPolicy
        if (p.type !in policy.allowed) add("El tipo de adaptación no está permitido por adaptationPolicy.")
        if (p.reason.isBlank()) add("Toda adaptación lleva su motivo.")

        val day = pkg.plans[p.crewId]?.firstOrNull { d -> d.activities.any { it.entryId == p.entryId } }
        val activity = day?.activities?.firstOrNull { it.entryId == p.entryId }
        if (day == null || activity == null) {
            add("La actividad no figura en el plan de ese tripulante.")
            return@buildList
        }
        val item = pkg.catalog.firstOrNull { it.id == activity.itemId }
        if (!activity.adaptable) add("La actividad está marcada como fija: solo puede cambiarla control de misión.")

        when (p.type) {
            AdaptationType.SUBSTITUTION -> {
                val replacement = pkg.catalog.firstOrNull { it.id == p.replacementItemId }
                if (replacement == null) {
                    add("La acción sustituta no existe en el catálogo del paquete.")
                } else {
                    if (item != null && replacement.category != item.category) add("La acción sustituta es de otra categoría.")
                    if (item != null && replacement.intense && !item.intense) add("La sustitución aumenta la carga: introduce una acción exigente.")
                }
            }
            AdaptationType.RESCHEDULE -> {
                val newMet = p.newMet
                if (newMet == null) {
                    add("Una reprogramación necesita newMet.")
                } else {
                    val fd = pkg.days.firstOrNull { it.day == day.day }
                    if (fd == null || newMet < fd.startMet || newMet >= fd.endMet) {
                        add("El nuevo MET ${Met.format(newMet)} cae fuera del día de vuelo original.")
                    }
                    val inWindow = item != null && pkg.blocks.any { b ->
                        item.category in b.adaptableCategories && newMet >= b.startMet && newMet < b.endMet
                    }
                    if (!inWindow) add("El nuevo MET ${Met.format(newMet)} no está dentro de un periodo adaptable de su categoría.")
                }
            }
            AdaptationType.OMISSION -> Unit
        }
    }
}
