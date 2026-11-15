package com.aura.astronaut.assessment

import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.data.Observation
import com.aura.astronaut.data.Origin
import com.aura.astronaut.mission.MissionPackageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AlertRulesTest {

    init {
        com.aura.astronaut.i18n.TestStrings.install("es")
    }

    private val pkg = MissionPackageParser.parse(File("../contract/mission-package.example.json").readText()).pkg
    private val catalog = pkg.catalog.associateBy { it.id }

    private fun signals(day: Int, s: DemoScenario) = s.values.filterKeys { id -> pkg.signals.any { it.id == id } }.map { (id, v) ->
        Observation(
            crewId = "cdr", kind = Observation.KIND_SIGNAL, indicator = id, value = v, rawValue = null, unit = "",
            variantId = null, entryId = null, capacityId = null, flightDay = day, met = 0, recordedAtUtcMs = 0,
            origin = Origin.SIMULATED, quality = "", packageVersion = 1,
        )
    }

    private fun answer(day: Int, id: String, v: Double) = signals(day, DemoScenario.STABLE).first().copy(
        kind = Observation.KIND_QUESTIONNAIRE, indicator = id, value = v, origin = Origin.MANUAL,
    )

    @Test
    fun `a capacity in red raises a red alert with its evidence`() {
        val a = CapacityEvaluator.evaluate(pkg, 7, signals(7, DemoScenario.COORDINATION))
        val c = AlertRules.candidates(a, emptyList(), catalog).single()
        assertEquals(AlertRules.KIND_RED, c.kind)
        assertEquals("red", c.level)
        assertEquals("coordination", c.capacityId)
        assertEquals("rojo:7:coordination", c.dedupeKey)
        assertEquals("34", c.evidence.single().value)
        assertEquals(Origin.SIMULATED, c.evidence.single().source)
        assertTrue(c.message.contains("remite al procedimiento de misión"))
    }

    @Test
    fun `what is only amber raises no alert`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE))
        assertTrue(AlertRules.candidates(a, emptyList(), catalog).isEmpty())
    }

    @Test
    fun `inconsistency between self-report and sensor`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE) + answer(8, "Q1_FATIGUE", 2.0))
        val c = AlertRules.candidates(a, emptyList(), catalog).single()
        assertEquals(AlertRules.KIND_CONSISTENCY, c.kind)
        assertEquals("warning", c.level)
        assertEquals(2, c.evidence.size)
    }

    @Test
    fun `adaptation that removes a preparation measure`() {
        val removes = adaptation("cdr-A-710100", AdaptationEngine.RULE_CRITICAL_NEXT_DAY + AdaptationEngine.REMOVES_PREP)
        val keeps = adaptation("cdr-R-591300", AdaptationEngine.RULE_CAPACITY)
        val c = AlertRules.candidates(null, listOf(removes, keeps), catalog).single()
        assertEquals(AlertRules.KIND_PREP, c.kind)
        assertEquals("cdr-A-710100", c.entryId)
        assertEquals("preparacion:cdr-A-710100", c.dedupeKey)
        assertEquals(removes.evidenceJson, c.evidenceJson)
        assertTrue(c.message.contains("Requiere decisión de control de misión"))
    }

    private fun adaptation(entryId: String, rule: String) = LocalAdaptation(
        id = entryId, crewId = "cdr", entryId = entryId, originalItemId = "A", packageVersion = 1, type = "substitution",
        replacementItemId = "RB", newMet = null, decidedAtMet = 0, reason = "r", evidenceJson = "[{\"indicator\":\"x\",\"value\":\"1\",\"source\":\"manual\"}]",
        rule = rule, createdAtUtcMs = 0,
    )
}
