package com.aura.astronaut.assessment

import com.aura.astronaut.data.Observation
import com.aura.astronaut.data.Origin
import com.aura.astronaut.mission.AdaptationType
import com.aura.astronaut.mission.CapacityLevel
import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.MissionPackageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdaptationRulesTest {

    init {
        com.aura.astronaut.i18n.TestStrings.install("es")
    }

    private val pkg: MissionPackage = MissionPackageParser.parse(File("../contract/mission-package.example.json").readText()).pkg
    private fun day(n: Int) = pkg.days.single { it.day == n }

    private fun signals(day: Int, scenario: DemoScenario, met: Long = day(day).startMet) =
        scenario.values.filterKeys { id -> pkg.signals.any { it.id == id } }.map { (id, v) ->
            obs(Observation.KIND_SIGNAL, id, v, day, met, Origin.SIMULATED)
        }

    private fun answer(day: Int, questionId: String, normalized: Double, met: Long = day(day).startMet) =
        obs(Observation.KIND_QUESTIONNAIRE, questionId, normalized, day, met, Origin.MANUAL)

    private fun obs(kind: String, id: String, v: Double, day: Int, met: Long, origin: Origin) = Observation(
        crewId = "cdr", kind = kind, indicator = id, value = v, rawValue = v, unit = "", variantId = null,
        entryId = null, capacityId = null, flightDay = day, met = met, recordedAtUtcMs = 0, origin = origin,
        quality = "", packageVersion = 1,
    )

    // ---------- Traffic light ----------

    @Test
    fun `with no data for the day every capacity is gray, never green`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, emptyList())
        assertEquals(6, a.capacities.size)
        assertTrue(a.capacities.all { it.level == CapacityLevel.GRAY })
        // Data from another day does not count.
        val other = CapacityEvaluator.evaluate(pkg, 8, signals(7, DemoScenario.FATIGUE))
        assertTrue(other.capacities.all { it.level == CapacityLevel.GRAY })
    }

    @Test
    fun `stable profile is green`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.STABLE))
        // Vestibular has no signals: it stays gray until the questionnaire is answered.
        assertEquals(CapacityLevel.GRAY, a.state("vestibular")!!.level)
        assertEquals(CapacityLevel.GREEN, a.state("fatigue")!!.level)
        assertEquals(CapacityLevel.GREEN, a.state("cardiovascular")!!.level)
    }

    @Test
    fun `accumulated fatigue turns fatigue, musculoskeletal and cardiovascular amber with their reason`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE))
        val fatiga = a.state("fatigue")!!
        assertEquals(CapacityLevel.AMBER, fatiga.level)
        assertEquals("night_hrv_delta", fatiga.drivers.single().indicatorId)
        assertEquals("≤ −15", fatiga.drivers.single().threshold)
        assertEquals(Origin.SIMULATED, fatiga.drivers.single().origin)
        assertEquals(CapacityLevel.AMBER, a.state("musculoskeletal")!!.level)
        assertEquals(CapacityLevel.AMBER, a.state("cardiovascular")!!.level)
    }

    @Test
    fun `worsened coordination turns red`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.COORDINATION))
        assertEquals(CapacityLevel.RED, a.state("coordination")!!.level)
        assertEquals("≥ 30", a.state("coordination")!!.drivers.single().threshold)
    }

    @Test
    fun `a high answer counts on its own and the worst level wins`() {
        // Q1 at 6 (normalized) is red even though the signals are fine.
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.STABLE) + answer(8, "Q1_FATIGUE", 6.0))
        assertEquals(CapacityLevel.RED, a.state("fatigue")!!.level)
        assertTrue(a.state("fatigue")!!.drivers.single().isQuestion)
    }

    @Test
    fun `the latest measurement of the day is used`() {
        val early = obs(Observation.KIND_SIGNAL, "night_hrv_delta", -22.0, 8, day(8).startMet, Origin.SIMULATED)
        val late = obs(Observation.KIND_SIGNAL, "night_hrv_delta", -3.0, 8, day(8).startMet + 60, Origin.SIMULATED)
        assertEquals(CapacityLevel.GREEN, CapacityEvaluator.evaluate(pkg, 8, listOf(early, late)).state("fatigue")!!.level)
    }

    @Test
    fun `inconsistency between self-report and sensor`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE) + answer(8, "Q1_FATIGUE", 2.0))
        val finding = a.consistency.single()
        assertEquals("fatigue", finding.capacityId)
        // The level does not drop: it stays amber because of the signal.
        assertEquals(CapacityLevel.AMBER, a.state("fatigue")!!.level)
    }

    // ---------- Adaptation rules ----------

    @Test
    fun `in gray nothing is adapted blindly`() {
        // FD08: R is intense and adaptable, but with no data and FD09 of high load there is no reason.
        val met = Met.parse("06/20:00")!!
        val a = CapacityEvaluator.evaluate(pkg, 8, emptyList())
        assertTrue(AdaptationEngine.decide(pkg, "cdr", met, day(8), a, emptySet()).isEmpty())
    }

    @Test
    fun `with fatigue intense exercise is replaced by a lower-load one`() {
        val met = Met.parse("06/20:00")!!
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE))
        val all = AdaptationEngine.decide(pkg, "cdr", met, day(8), a, emptySet())
        val d = all.single { it.originalItemId == "R" }
        assertEquals(AdaptationType.SUBSTITUTION, d.proposal.type)
        assertEquals("RB", d.proposal.replacementItemId)
        // R does not prepare the transition: there is no preparation notice.
        assertEquals(AdaptationEngine.RULE_CAPACITY, d.rule)
        assertTrue(d.proposal.reason.contains("umbral de demostración"))
        assertTrue(d.evidence.all { it.source == Origin.SIMULATED })
        // FD08's aerobic does prepare the transition: it is flagged for mission control.
        assertEquals(AdaptationEngine.RULE_CAPACITY + AdaptationEngine.REMOVES_PREP, all.single { it.originalItemId == "A" }.rule)
    }

    @Test
    fun `with the cardiovascular capacity in amber the mission control action is applied`() {
        // Accumulated fatigue: cardiovascular in amber and its onAmberActions asks for the hydration menu (N2).
        val met = Met.parse("06/20:00")!!
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE))
        assertEquals(CapacityLevel.AMBER, a.state("cardiovascular")!!.level)
        val menu = AdaptationEngine.decide(pkg, "cdr", met, day(8), a, emptySet()).single { it.originalItemId == "N1" }
        assertEquals(AdaptationEngine.RULE_AMBER_ACTION, menu.rule)
        assertEquals("N2", menu.proposal.replacementItemId)
        assertTrue(menu.proposal.reason.contains("control de misión fija"))
        assertTrue(menu.evidence.isNotEmpty())
        // Without onAmberActions in the package, the menu is not touched (the interface does not guess which).
        val legacy = pkg.copy(capacities = pkg.capacities.map { it.copy(onAmberActions = emptyList()) })
        assertTrue(AdaptationEngine.decide(legacy, "cdr", met, day(8), a, emptySet()).none { it.originalItemId == "N1" })
        // In red it does not apply: the interface refers to the mission procedure.
        val red = a.copy(capacities = a.capacities.map { if (it.capacity.id == "cardiovascular") it.copy(level = CapacityLevel.RED) else it })
        assertTrue(AdaptationEngine.decide(pkg, "cdr", met, day(8), red, emptySet()).none { it.rule == AdaptationEngine.RULE_AMBER_ACTION })
    }

    /** The demo plan no longer puts intense exercise on the eve of a critical day: FD09 is forced critical. */
    private val criticalFd9 = pkg.copy(days = pkg.days.map { if (it.day == 9) it.copy(opLoad = com.aura.astronaut.mission.LoadLevel.CRITICAL) else it })

    @Test
    fun `on the eve of a critical day there are no intense activities`() {
        // FD08: R and A (intense, adaptable) with FD09 critical. No capacity data.
        val met = Met.parse("06/20:00")!!
        val all = AdaptationEngine.decide(criticalFd9, "cdr", met, day(8), null, emptySet())
        assertEquals(setOf("R", "A"), all.map { it.originalItemId }.toSet())
        val d = all.single { it.originalItemId == "A" }
        // A prepares orthostatic tolerance: the rule flags it for mission control.
        assertEquals(AdaptationEngine.RULE_CRITICAL_NEXT_DAY + AdaptationEngine.REMOVES_PREP, d.rule)
        assertTrue(d.proposal.reason.contains("FD09"))
        assertTrue(d.proposal.reason.contains("requiere decisión de control de misión"))
    }

    @Test
    fun `the demo plan no longer has intense exercise on the eve of splashdown`() {
        val fd9 = pkg.plans.getValue("cdr").single { it.day == 9 }.activities
        assertTrue(fd9.none { a -> pkg.catalog.single { it.id == a.itemId }.intense })
        assertTrue(AdaptationEngine.decide(pkg, "cdr", Met.parse("07/18:00")!!, day(9), null, emptySet()).isEmpty())
    }

    @Test
    fun `it does not decide with another day's traffic light`() {
        // When moving from FD07 to FD08, a red FD07 traffic light cannot justify anything on FD08.
        val fd7 = CapacityEvaluator.evaluate(criticalFd9, 7, signals(7, DemoScenario.COORDINATION))
        assertTrue(AdaptationEngine.decide(criticalFd9, "cdr", Met.parse("06/20:00")!!, day(8), fd7, emptySet()).isEmpty())
        // With FD08's (no data) it decides because of the critical eve, with its own evidence.
        val fd8 = CapacityEvaluator.evaluate(criticalFd9, 8, emptyList())
        val all = AdaptationEngine.decide(criticalFd9, "cdr", Met.parse("06/20:00")!!, day(8), fd8, emptySet())
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.rule.startsWith(AdaptationEngine.RULE_CRITICAL_NEXT_DAY) })
    }

    @Test
    fun `it does not decide twice nor about the past`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE))
        val met = Met.parse("06/20:00")!!
        val first = AdaptationEngine.decide(pkg, "cdr", met, day(8), a, emptySet())
        assertTrue(first.isNotEmpty())
        assertTrue(AdaptationEngine.decide(pkg, "cdr", met, day(8), a, first.map { it.proposal.entryId }.toSet()).isEmpty())
        // R (06/20:15), A (06/22:00) and the menu (07/00:30) are over: hours later nothing is adapted.
        assertTrue(AdaptationEngine.decide(pkg, "cdr", Met.parse("07/02:00")!!, day(8), a, emptySet()).isEmpty())
    }

    @Test
    fun `no rule produces an adaptation out of policy or on something fixed`() {
        var total = 0
        pkg.crew.forEach { member ->
            pkg.days.forEach { d ->
                val activities = pkg.plans.getValue(member.id).single { it.day == d.day }.activities
                val mets = listOf(d.startMet) + activities.map { it.met - 60 }
                DemoScenario.entries.map { signals(d.day, it) }.plus(listOf(emptyList())).forEach { obs ->
                    val a = CapacityEvaluator.evaluate(pkg, d.day, obs.map { it.copy(crewId = member.id) })
                    mets.forEach { met ->
                        AdaptationEngine.decide(pkg, member.id, met, d, a, emptySet()).forEach { decision ->
                            total++
                            assertEquals(emptyList<String>(), PolicyCheck.violations(pkg, decision.proposal))
                            val act = activities.single { it.entryId == decision.proposal.entryId }
                            assertTrue("${act.entryId} is fixed", act.adaptable)
                            val replacement = decision.proposal.replacementItemId?.let { id -> pkg.catalog.single { it.id == id } }
                            assertFalse("does not increase the load", replacement?.intense == true)
                            assertTrue(decision.proposal.reason.isNotBlank())
                            assertTrue(decision.evidence.isNotEmpty())
                        }
                    }
                }
            }
        }
        assertTrue("the rules must produce some adaptation in the demo mission", total > 0)
    }

    // ---------- Policy check ----------

    @Test
    fun `the local check detects the same as the server`() {
        val fixed = pkg.plans.getValue("cdr").flatMap { it.activities }.first { !it.adaptable }
        assertTrue(PolicyCheck.violations(pkg, AdaptationProposal("cdr", fixed.entryId, AdaptationType.OMISSION, reason = "x")).any { "fixed" in it })

        val rb = pkg.plans.getValue("cdr").flatMap { it.activities }.first { it.itemId == "R" }
        // Switching to A (intense) from R (intense) does not increase the load; N2 is another category.
        assertTrue(PolicyCheck.violations(pkg, AdaptationProposal("cdr", rb.entryId, AdaptationType.SUBSTITUTION, "N2", reason = "x")).any { "category" in it })
        val menu = pkg.plans.getValue("cdr").flatMap { it.activities }.first { it.itemId == "N1" && it.adaptable }
        assertTrue(PolicyCheck.violations(pkg, AdaptationProposal("cdr", menu.entryId, AdaptationType.RESCHEDULE, newMet = 0, reason = "x")).isNotEmpty())
        assertTrue(PolicyCheck.violations(pkg, AdaptationProposal("cdr", rb.entryId, AdaptationType.OMISSION, reason = " ")).any { "reason" in it })
        assertNotNull(AdaptationEngine.lowerLoadAlternative(pkg, pkg.catalog.single { it.id == "A" }))
    }
}
