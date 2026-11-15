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

    // ---------- Semáforo ----------

    @Test
    fun `sin datos del dia todas las capacidades en gris, nunca verde`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, emptyList())
        assertEquals(6, a.capacities.size)
        assertTrue(a.capacities.all { it.level == CapacityLevel.GRAY })
        // Datos de otro día no cuentan.
        val other = CapacityEvaluator.evaluate(pkg, 8, signals(7, DemoScenario.FATIGUE))
        assertTrue(other.capacities.all { it.level == CapacityLevel.GRAY })
    }

    @Test
    fun `perfil estable en verde`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.STABLE))
        // Vestibular no tiene señales: sigue en gris hasta responder el cuestionario.
        assertEquals(CapacityLevel.GRAY, a.state("vestibular")!!.level)
        assertEquals(CapacityLevel.GREEN, a.state("fatigue")!!.level)
        assertEquals(CapacityLevel.GREEN, a.state("cardiovascular")!!.level)
    }

    @Test
    fun `fatiga acumulada pone en ambar fatiga, musculo y cardiovascular con su motivo`() {
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
    fun `la coordinacion empeorada pasa a rojo`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.COORDINATION))
        assertEquals(CapacityLevel.RED, a.state("coordination")!!.level)
        assertEquals("≥ 30", a.state("coordination")!!.drivers.single().threshold)
    }

    @Test
    fun `una respuesta alta cuenta por si sola y gana el peor nivel`() {
        // Q1 en 6 (normalizado) es rojo aunque las señales estén bien.
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.STABLE) + answer(8, "Q1_FATIGUE", 6.0))
        assertEquals(CapacityLevel.RED, a.state("fatigue")!!.level)
        assertTrue(a.state("fatigue")!!.drivers.single().isQuestion)
    }

    @Test
    fun `se usa la ultima medida del dia`() {
        val early = obs(Observation.KIND_SIGNAL, "night_hrv_delta", -22.0, 8, day(8).startMet, Origin.SIMULATED)
        val late = obs(Observation.KIND_SIGNAL, "night_hrv_delta", -3.0, 8, day(8).startMet + 60, Origin.SIMULATED)
        assertEquals(CapacityLevel.GREEN, CapacityEvaluator.evaluate(pkg, 8, listOf(early, late)).state("fatigue")!!.level)
    }

    @Test
    fun `incoherencia entre autoinforme y sensor`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE) + answer(8, "Q1_FATIGUE", 2.0))
        val finding = a.consistency.single()
        assertEquals("fatigue", finding.capacityId)
        // El nivel no baja: sigue en ámbar por la señal.
        assertEquals(CapacityLevel.AMBER, a.state("fatigue")!!.level)
    }

    // ---------- Reglas de adaptación ----------

    @Test
    fun `en gris no se adapta a ciegas`() {
        // FD08: R es intensa y adaptable, pero sin datos y con FD09 de carga alta, no hay motivo.
        val met = Met.parse("06/20:00")!!
        val a = CapacityEvaluator.evaluate(pkg, 8, emptyList())
        assertTrue(AdaptationEngine.decide(pkg, "cdr", met, day(8), a, emptySet()).isEmpty())
    }

    @Test
    fun `con fatiga sustituye el ejercicio intenso por uno de menor carga`() {
        val met = Met.parse("06/20:00")!!
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE))
        val all = AdaptationEngine.decide(pkg, "cdr", met, day(8), a, emptySet())
        val d = all.single { it.originalItemId == "R" }
        assertEquals(AdaptationType.SUBSTITUTION, d.proposal.type)
        assertEquals("RB", d.proposal.replacementItemId)
        // R no prepara la transición: no hay aviso de preparación.
        assertEquals(AdaptationEngine.RULE_CAPACITY, d.rule)
        assertTrue(d.proposal.reason.contains("umbral de demostración"))
        assertTrue(d.evidence.all { it.source == Origin.SIMULATED })
        // El aeróbico de FD08 sí prepara la transición: se señala para control de misión.
        assertEquals(AdaptationEngine.RULE_CAPACITY + AdaptationEngine.REMOVES_PREP, all.single { it.originalItemId == "A" }.rule)
    }

    @Test
    fun `con la capacidad cardiovascular en ambar aplica la accion de control de mision`() {
        // Fatiga acumulada: cardiovascular en ámbar y su onAmberActions pide el menú con hidratación (N2).
        val met = Met.parse("06/20:00")!!
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE))
        assertEquals(CapacityLevel.AMBER, a.state("cardiovascular")!!.level)
        val menu = AdaptationEngine.decide(pkg, "cdr", met, day(8), a, emptySet()).single { it.originalItemId == "N1" }
        assertEquals(AdaptationEngine.RULE_AMBER_ACTION, menu.rule)
        assertEquals("N2", menu.proposal.replacementItemId)
        assertTrue(menu.proposal.reason.contains("control de misión fija"))
        assertTrue(menu.evidence.isNotEmpty())
        // Sin onAmberActions en el paquete, el menú no se toca (la interfaz no adivina cuál).
        val legacy = pkg.copy(capacities = pkg.capacities.map { it.copy(onAmberActions = emptyList()) })
        assertTrue(AdaptationEngine.decide(legacy, "cdr", met, day(8), a, emptySet()).none { it.originalItemId == "N1" })
        // En rojo no se aplica: se remite al procedimiento de misión.
        val red = a.copy(capacities = a.capacities.map { if (it.capacity.id == "cardiovascular") it.copy(level = CapacityLevel.RED) else it })
        assertTrue(AdaptationEngine.decide(pkg, "cdr", met, day(8), red, emptySet()).none { it.rule == AdaptationEngine.RULE_AMBER_ACTION })
    }

    /** El plan demo ya no pone ejercicio intenso la víspera de un día crítico: se fuerza FD09 crítico. */
    private val criticalFd9 = pkg.copy(days = pkg.days.map { if (it.day == 9) it.copy(opLoad = com.aura.astronaut.mission.LoadLevel.CRITICAL) else it })

    @Test
    fun `la vispera de un dia critico no hay actividades intensas`() {
        // FD08: R y A (intensas, adaptables) con FD09 crítico. Sin datos de capacidades.
        val met = Met.parse("06/20:00")!!
        val all = AdaptationEngine.decide(criticalFd9, "cdr", met, day(8), null, emptySet())
        assertEquals(setOf("R", "A"), all.map { it.originalItemId }.toSet())
        val d = all.single { it.originalItemId == "A" }
        // A prepara la tolerancia ortostática: la regla lo señala para control de misión.
        assertEquals(AdaptationEngine.RULE_CRITICAL_NEXT_DAY + AdaptationEngine.REMOVES_PREP, d.rule)
        assertTrue(d.proposal.reason.contains("FD09"))
        assertTrue(d.proposal.reason.contains("requiere decisión de control de misión"))
    }

    @Test
    fun `el plan demo ya no tiene ejercicio intenso la vispera del amerizaje`() {
        val fd9 = pkg.plans.getValue("cdr").single { it.day == 9 }.activities
        assertTrue(fd9.none { a -> pkg.catalog.single { it.id == a.itemId }.intense })
        assertTrue(AdaptationEngine.decide(pkg, "cdr", Met.parse("07/18:00")!!, day(9), null, emptySet()).isEmpty())
    }

    @Test
    fun `no decide con el semaforo de otro dia`() {
        // Al pasar de FD07 a FD08, un semáforo de FD07 en rojo no puede justificar nada en FD08.
        val fd7 = CapacityEvaluator.evaluate(criticalFd9, 7, signals(7, DemoScenario.COORDINATION))
        assertTrue(AdaptationEngine.decide(criticalFd9, "cdr", Met.parse("06/20:00")!!, day(8), fd7, emptySet()).isEmpty())
        // Con el de FD08 (sin datos) decide por la víspera crítica, con su propia evidencia.
        val fd8 = CapacityEvaluator.evaluate(criticalFd9, 8, emptyList())
        val all = AdaptationEngine.decide(criticalFd9, "cdr", Met.parse("06/20:00")!!, day(8), fd8, emptySet())
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.rule.startsWith(AdaptationEngine.RULE_CRITICAL_NEXT_DAY) })
    }

    @Test
    fun `no se decide dos veces ni sobre lo pasado`() {
        val a = CapacityEvaluator.evaluate(pkg, 8, signals(8, DemoScenario.FATIGUE))
        val met = Met.parse("06/20:00")!!
        val first = AdaptationEngine.decide(pkg, "cdr", met, day(8), a, emptySet())
        assertTrue(first.isNotEmpty())
        assertTrue(AdaptationEngine.decide(pkg, "cdr", met, day(8), a, first.map { it.proposal.entryId }.toSet()).isEmpty())
        // R (06/20:15), A (06/22:00) y el menú (07/00:30) ya han acabado: horas después no se adapta.
        assertTrue(AdaptationEngine.decide(pkg, "cdr", Met.parse("07/02:00")!!, day(8), a, emptySet()).isEmpty())
    }

    @Test
    fun `ninguna regla produce una adaptacion fuera de politica ni sobre algo fijo`() {
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
                            assertTrue("${act.entryId} es fija", act.adaptable)
                            val replacement = decision.proposal.replacementItemId?.let { id -> pkg.catalog.single { it.id == id } }
                            assertFalse("no aumenta la carga", replacement?.intense == true)
                            assertTrue(decision.proposal.reason.isNotBlank())
                            assertTrue(decision.evidence.isNotEmpty())
                        }
                    }
                }
            }
        }
        assertTrue("las reglas deben producir alguna adaptación en la misión demo", total > 0)
    }

    // ---------- Comprobación de política ----------

    @Test
    fun `la comprobacion local detecta lo mismo que el servidor`() {
        val fixed = pkg.plans.getValue("cdr").flatMap { it.activities }.first { !it.adaptable }
        assertTrue(PolicyCheck.violations(pkg, AdaptationProposal("cdr", fixed.entryId, AdaptationType.OMISSION, reason = "x")).any { "fija" in it })

        val rb = pkg.plans.getValue("cdr").flatMap { it.activities }.first { it.itemId == "R" }
        // Cambiar a A (intensa) desde R (intensa) no aumenta la carga; a N2 es otra categoría.
        assertTrue(PolicyCheck.violations(pkg, AdaptationProposal("cdr", rb.entryId, AdaptationType.SUBSTITUTION, "N2", reason = "x")).any { "categoría" in it })
        val menu = pkg.plans.getValue("cdr").flatMap { it.activities }.first { it.itemId == "N1" && it.adaptable }
        assertTrue(PolicyCheck.violations(pkg, AdaptationProposal("cdr", menu.entryId, AdaptationType.RESCHEDULE, newMet = 0, reason = "x")).isNotEmpty())
        assertTrue(PolicyCheck.violations(pkg, AdaptationProposal("cdr", rb.entryId, AdaptationType.OMISSION, reason = " ")).any { "motivo" in it })
        assertNotNull(AdaptationEngine.lowerLoadAlternative(pkg, pkg.catalog.single { it.id == "A" }))
    }
}
