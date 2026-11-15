package com.aura.astronaut.assessment

import com.aura.astronaut.data.ActivityConfirmation
import com.aura.astronaut.data.ConfirmationStatus
import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.data.Observation
import com.aura.astronaut.data.Origin
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionPackageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TransitionReadinessTest {

    init {
        com.aura.astronaut.i18n.TestStrings.install("es")
    }

    private val pkg = MissionPackageParser.parse(File("../contract/mission-package.example.json").readText()).pkg
    private fun day(n: Int) = pkg.days.single { it.day == n }
    private val plan = pkg.plans.getValue("cdr").flatMap { it.activities }
    private fun entry(item: String, dayN: Int) = plan.single { it.itemId == item && pkg.days.single { d -> d.day == dayN }.let { d -> it.met >= d.startMet && it.met < d.endMet } }

    private fun build(
        met: Long,
        conf: Map<String, ActivityConfirmation> = emptyMap(),
        adapt: Map<String, LocalAdaptation> = emptyMap(),
        obs: List<Observation> = emptyList(),
    ) = TransitionReadiness.build(pkg, "cdr", met, pkg.days.firstOrNull { met >= it.startMet && met < it.endMet }, day(10), conf, adapt, obs)!!

    @Test
    fun `medidas de preparacion del amerizaje en la mision demo`() {
        val r = build(day(1).startMet)
        // A ×2, V ×4, OIG ×2, SF ×2: lo que AGENTS.md cita para la tolerancia ortostática y el retorno.
        assertEquals(mapOf("A" to 2, "V" to 4, "OIG" to 2, "SF" to 2), r.measures.groupingBy { it.item.id }.eachCount())
        assertTrue(r.measures.all { it.status == MeasureStatus.UPCOMING })
        // Las capacidades críticas las declara el paquete (criticalFor), en su orden.
        assertEquals(setOf("cardiovascular", "vestibular"), r.critical.map { it.capacity.id }.toSet())
        assertEquals(false, r.provisional)
    }

    @Test
    fun `sin criticalFor en el paquete se usa la lista provisional`() {
        val legacy = pkg.copy(capacities = pkg.capacities.map { it.copy(criticalFor = null) })
        val r = TransitionReadiness.build(legacy, "cdr", 0, day(1), day(10), emptyMap(), emptyMap(), emptyList())!!
        assertEquals(TransitionPriorities.CRITICAL_CAPACITY_IDS, r.critical.map { it.capacity.id })
        assertEquals(true, r.provisional)
    }

    @Test
    fun `el despegue no es una transicion hacia gravedad`() {
        assertNull(TransitionReadiness.build(pkg, "cdr", 0, day(1), day(1), emptyMap(), emptyMap(), emptyList()))
    }

    @Test
    fun `estado de cada medida`() {
        val met = Met.parse("08/12:00")!!
        val a7 = entry("A", 7)
        val a8 = entry("A", 8)
        val v7 = entry("V", 7)
        val v8 = entry("V", 8)
        val conf = mapOf(
            a7.entryId to confirmation(a7.entryId, ConfirmationStatus.DONE),
            v7.entryId to confirmation(v7.entryId, ConfirmationStatus.OMITTED, "Sin tiempo por operaciones"),
        )
        // AURA cambió el aeróbico de FD08 por bandas (que no preparan la tolerancia ortostática).
        val adapt = mapOf(a8.entryId to adaptation(a8.entryId, "A", "RB"))
        val r = build(met, conf, adapt)
        val status = r.measures.associate { it.activity.entryId to it.status }
        assertEquals(MeasureStatus.DONE, status[a7.entryId])
        assertEquals(MeasureStatus.OMITTED_BY_ASTRONAUT, status[v7.entryId])
        assertEquals(MeasureStatus.MISSED, status[v8.entryId])
        assertEquals(MeasureStatus.REPLACED_LOST, status[a8.entryId])
        assertEquals(MeasureStatus.UPCOMING, status[entry("OIG", 10).entryId])
        assertTrue(r.lost >= 3)
        assertEquals(1, r.done)
        // Ejercicio hasta ahora: R (FD04), A (FD07), R y A (FD08).
        assertEquals(4, r.exercise.planned)
        assertEquals(1, r.exercise.done)
        assertEquals(1, r.exercise.replaced)
    }

    @Test
    fun `tendencia de las capacidades criticas por dia`() {
        val obs = listOf(
            signal("resting_hr_delta", 3.0, 7),
            signal("resting_hr_delta", 11.0, 8),
        )
        val r = build(Met.parse("07/10:00")!!, obs = obs)
        val cardio = r.critical.single { it.capacity.id == "cardiovascular" }
        assertEquals(CapacityLevel.GREEN, cardio.byDay.single { it.first.day == 7 }.second)
        assertEquals(CapacityLevel.AMBER, cardio.byDay.single { it.first.day == 8 }.second)
        assertEquals(Trend.WORSENING, cardio.trend)
        assertEquals(Trend.NO_DATA, r.critical.single { it.capacity.id == "vestibular" }.trend)
        assertEquals(Trend.IMPROVING, TransitionReadiness.trendOf(listOf(CapacityLevel.RED, CapacityLevel.GRAY, CapacityLevel.GREEN)))
        assertEquals(Trend.STABLE, TransitionReadiness.trendOf(listOf(CapacityLevel.AMBER, CapacityLevel.AMBER)))
    }

    private fun confirmation(entryId: String, status: ConfirmationStatus, reason: String? = null) = ActivityConfirmation(
        crewId = "cdr", entryId = entryId, itemId = "", packageVersion = 1, status = status, reason = reason,
        confirmedAtMet = 0, recordedAtUtcMs = 0,
    )

    private fun adaptation(entryId: String, original: String, replacement: String) = LocalAdaptation(
        id = "x", crewId = "cdr", entryId = entryId, originalItemId = original, packageVersion = 1, type = "substitution",
        replacementItemId = replacement, newMet = null, decidedAtMet = 0, reason = "r", evidenceJson = "[]", rule = "t",
        createdAtUtcMs = 0,
    )

    private fun signal(id: String, v: Double, dayN: Int) = Observation(
        crewId = "cdr", kind = Observation.KIND_SIGNAL, indicator = id, value = v, rawValue = null, unit = "",
        variantId = null, entryId = null, capacityId = null, flightDay = dayN, met = day(dayN).startMet,
        recordedAtUtcMs = 0, origin = Origin.SIMULATED, quality = "", packageVersion = 1,
    )
}
