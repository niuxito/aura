package com.aura.astronaut.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class MissionPackageParserTest {

    init {
        com.aura.astronaut.i18n.TestStrings.install("es")
    }

    // Las pruebas se ejecutan desde el módulo app/.
    private val contractText = File("../contract/mission-package.example.json").readText()
    private val assetText = File("src/main/assets/mission-package.json").readText()

    private val loaded = MissionPackageParser.parse(contractText)
    private val pkg = loaded.pkg

    @Test
    fun `el paquete embebido es la copia del contrato`() {
        assertEquals("Copia contract/mission-package.example.json en app/src/main/assets/mission-package.json", contractText, assetText)
    }

    @Test
    fun `lee el paquete de ejemplo`() {
        assertEquals(SUPPORTED_SCHEMA, pkg.schema)
        assertEquals(1, pkg.packageVersion)
        assertEquals(listOf("en", "es"), pkg.languages)
        assertEquals("en", pkg.defaultLanguage)
        assertTrue(pkg.simulated)
        assertNull(pkg.mission.launchUtc)
        assertEquals("Artemis II · cronograma planificado", pkg.mission.name.text)
        assertEquals(listOf("cdr", "plt", "ms1", "ms2"), pkg.crew.map { it.id })
        assertEquals(10, pkg.days.size)
        assertEquals("FD01", pkg.days.first().label)
        assertEquals(783_768L, pkg.days.last().endMet)
        assertEquals(0.0, pkg.gravityReference[Gravity.MICRO])
        assertEquals(1.0, pkg.gravityReference[Gravity.EARTH])
        assertEquals(BlockKind.EXERCISE, pkg.adaptationPolicy.windows[Category.EXERCISE])
        assertEquals(false, pkg.adaptationPolicy.report.blocking)
    }

    @Test
    fun `las transiciones gravitatorias son el despegue y el regreso`() {
        val transitions = pkg.days.filter { it.transitionFrom != null }
        assertEquals(listOf(1, 10), transitions.map { it.day })
        assertEquals(Gravity.EARTH, transitions.first().transitionFrom)
        assertEquals(Gravity.MICRO, transitions.first().gravity)
        assertEquals(Gravity.MICRO, transitions.last().transitionFrom)
        assertEquals(Gravity.EARTH, transitions.last().gravity)
    }

    @Test
    fun `cada tripulante tiene un plan por dia de vuelo con actividades del catalogo`() {
        val catalog = pkg.catalog.map { it.id }.toSet()
        pkg.crew.forEach { member ->
            val plan = pkg.plans.getValue(member.id)
            assertEquals(pkg.days.map { it.day }, plan.map { it.day })
            plan.flatMap { it.activities }.forEach { assertTrue(it.itemId in catalog) }
        }
        assertTrue(MissionPackageParser.integrityProblems(pkg).isEmpty())
    }

    @Test
    fun `verifica el checksum del Manager`() {
        assertEquals(ChecksumStatus.VERIFIED, loaded.checksum)
    }

    @Test
    fun `detecta un paquete alterado`() {
        val tampered = contractText.replaceFirst("\"packageVersion\": 1", "\"packageVersion\": 2")
        check(tampered != contractText) { "La prueba no ha alterado el paquete" }
        assertEquals(ChecksumStatus.MISMATCH, MissionPackageParser.parse(tampered).checksum)
    }

    @Test
    fun `rechaza otro esquema`() {
        val other = contractText.replaceFirst(SUPPORTED_SCHEMA, "aura.mission-package/v4")
        check(other != contractText)
        try {
            MissionPackageParser.parse(other)
            fail("Debía rechazar el esquema v4")
        } catch (e: PackageException) {
            assertTrue(e.message!!.contains("v4"))
        }
    }

    @Test
    fun `lee la autoevaluacion v4`() {
        assertTrue(pkg.thresholdsAreDemo)
        assertEquals(
            listOf("fatigue", "musculoskeletal", "vestibular", "cardiovascular", "vision", "coordination"),
            pkg.capacities.map { it.id },
        )
        val spo2 = pkg.signals.single { it.id == "spo2" }
        assertEquals(SignalKind.NUMBER, spo2.kind)
        assertEquals(WorseWhen.LOWER, spo2.worseWhen)
        assertNull(spo2.amber)
        assertEquals(92.0, spo2.red)
        assertTrue(spo2.simulated)
        val arritmia = pkg.signals.single { it.id == "ecg_arrhythmia" }
        assertEquals(SignalKind.BOOLEAN, arritmia.kind)
        assertEquals(CapacityLevel.AMBER, arritmia.whenTrue)

        val q = pkg.questionnaire
        assertEquals("F", q.catalogItemId)
        assertEquals(3, q.fixedPerDay)
        assertEquals(2, q.bankPerDay)
        assertEquals(15, q.items.size)
        assertTrue(q.items.first().variants.any { it.inverted })
        val triggers = q.items.flatMap { it.triggers }
        assertTrue(triggers.any { it is QuestionTrigger.Transition })
        assertTrue(triggers.any { it is QuestionTrigger.Environment })
        assertTrue(triggers.any { it is QuestionTrigger.DaysInMicro })
        assertTrue(triggers.any { it is QuestionTrigger.Event })
        assertTrue(triggers.any { it is QuestionTrigger.SignalValue })
        assertTrue(triggers.any { it is QuestionTrigger.Filler })
        assertEquals(ComparisonOp.AT_MOST, q.consistencyChecks.first().op)
    }

    @Test
    fun `detecta referencias rotas de la autoevaluacion`() {
        val broken = pkg.copy(capacities = pkg.capacities.filter { it.id != "cardiovascular" })
        assertTrue(MissionPackageParser.integrityProblems(broken).any { "cardiovascular" in it })
    }

    @Test(expected = PackageException::class)
    fun `rechaza un JSON roto`() {
        MissionPackageParser.parse(contractText.dropLast(10))
    }

    @Test
    fun `rechaza actividades que no estan en el catalogo`() {
        val broken = pkg.copy(catalog = pkg.catalog.filter { it.id != "F" })
        assertTrue(MissionPackageParser.integrityProblems(broken).any { "«F»" in it })
    }

    @Test
    fun `dia de vuelo de un MET`() {
        val fd1 = pkg.days[0]
        val fd2 = pkg.days[1]
        val last = pkg.days.last()
        assertEquals(1, pkg.days.dayAt(0)?.day)
        assertEquals(1, pkg.days.dayAt(fd1.endMet - 1)?.day)
        // El fin de un día pertenece ya al siguiente.
        assertEquals(2, pkg.days.dayAt(fd1.endMet)?.day)
        assertEquals(fd2.startMet, fd1.endMet)
        // El amerizaje (fin del último día) cuenta dentro de FD10.
        assertEquals(10, pkg.days.dayAt(last.endMet)?.day)
        assertNull(pkg.days.dayAt(last.endMet + 1))
        assertNull(pkg.days.dayAt(-1))
    }

    @Test
    fun `fase de la mision`() {
        assertEquals(MissionPhase.BeforeLaunch, pkg.phaseAt(-60))
        assertEquals(MissionPhase.AfterEnd, pkg.phaseAt(pkg.endMet + 60))
        val inFlight = pkg.phaseAt(Met.parse("01/01:08:42")!!)
        assertTrue(inFlight is MissionPhase.InFlight)
        assertEquals(2, (inFlight as MissionPhase.InFlight).day.day)
    }

    @Test
    fun `json canonico como JSON stringify`() {
        val tree = kotlinx.serialization.json.Json.parseToJsonElement(
            """{ "b": [1, 0.165, true, null], "a": "x\"y\\z\n→" }""",
        )
        assertEquals("""{"a":"x\"y\\z\n→","b":[1,0.165,true,null]}""", CanonicalJson.write(tree))
        assertNotNull(tree)
    }
}
