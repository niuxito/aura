package com.aura.astronaut.assessment

import com.aura.astronaut.mission.MissionPackageParser
import com.aura.astronaut.mission.QuestionBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuestionnaireSelectorTest {

    init {
        com.aura.astronaut.i18n.TestStrings.install("es")
    }

    private val pkg = MissionPackageParser.parse(File("../contract/mission-package.example.json").readText()).pkg
    private val q = pkg.questionnaire
    private fun day(n: Int) = pkg.days.single { it.day == n }
    private fun ids(list: List<SelectedQuestion>) = list.map { it.item.id }

    @Test
    fun `fixed block with variant rotating by day, crew member and question`() {
        // CDR is crew member 0. FD01: (1 + 0 + i) % 3.
        val fd1 = QuestionnaireSelector.select(pkg, "cdr", day(1)).take(q.fixedPerDay)
        assertEquals(listOf("Q1_FATIGUE", "Q2_SANS", "Q3_MUSCULAR"), ids(fd1))
        assertEquals(listOf("Q1_V2", "Q2_V3", "Q3_V1"), fd1.map { it.variant.id })
        fd1.forEach { assertNull(it.reason) }
        // PLT (index 1) sees another wording on the same day.
        val plt = QuestionnaireSelector.select(pkg, "plt", day(1)).take(q.fixedPerDay)
        assertEquals(listOf("Q1_V3", "Q2_V1", "Q3_V2"), plt.map { it.variant.id })
    }

    @Test
    fun `each day has the fixed questions plus the bank ones`() {
        (1..10).forEach { n ->
            val s = QuestionnaireSelector.select(pkg, "cdr", day(n))
            assertEquals("FD$n", q.fixedPerDay + q.bankPerDay, s.size)
            assertEquals(s.size, s.map { it.item.id }.distinct().size)
            assertTrue(s.drop(q.fixedPerDay).all { it.item.block == QuestionBlock.BANK && it.reason != null })
        }
    }

    @Test
    fun `FD01 near launch asks about the vestibular system and fills in`() {
        val bank = QuestionnaireSelector.select(pkg, "cdr", day(1)).drop(q.fixedPerDay)
        // VES-01: 3 flight days or less from a gravity change (launch). Nothing else fires: filler LOC-01.
        assertEquals(listOf("VES-01", "LOC-01"), ids(bank))
        assertEquals("Pregunta de relleno cuando no hay otras disparadas.", bank[1].reason)
        // Bank variant: day % variants.
        assertEquals(bank[0].item.variants[1 % 2].id, bank[0].variant.id)
    }

    @Test
    fun `FD08 three days before splashdown prioritizes landing`() {
        val bank = QuestionnaireSelector.select(pkg, "cdr", day(8)).drop(q.fixedPerDay)
        // VES-02 (priority 10, landing on Earth) and VES-01 (9) beat LOC-02 (6, 7 or more days in micro-g).
        assertEquals(listOf("VES-02", "VES-01"), ids(bank))
        assertEquals("Faltan 3 días de vuelo o menos para un aterrizaje.", bank[0].reason)
    }

    @Test
    fun `a measured signal enters by priority and without measurement it does not fire`() {
        val without = QuestionnaireSelector.select(pkg, "cdr", day(5)).drop(q.fixedPerDay)
        assertTrue("CAR-01" !in ids(without))
        val with = QuestionnaireSelector.select(pkg, "cdr", day(5), mapOf("ecg_arrhythmia" to 1.0)).drop(q.fixedPerDay)
        assertEquals("CAR-01", with.first().item.id)
        assertEquals("La prenda ha registrado latidos irregulares.", with.first().reason)
        // Numeric signal: hrv ≤ −15 fires CIR-01; −10 does not.
        assertTrue("CIR-01" in ids(QuestionnaireSelector.select(pkg, "cdr", day(5), mapOf("night_hrv_delta" to -18.0))))
        assertTrue("CIR-01" !in ids(QuestionnaireSelector.select(pkg, "cdr", day(5), mapOf("night_hrv_delta" to -10.0))))
    }

    @Test
    fun `consecutive days in microgravity`() {
        assertEquals(1, QuestionnaireSelector.microDaysUpTo(pkg, day(1)))
        assertEquals(7, QuestionnaireSelector.microDaysUpTo(pkg, day(7)))
        // FD10 ends on Earth.
        assertEquals(0, QuestionnaireSelector.microDaysUpTo(pkg, day(10)))
    }

    @Test
    fun `normalizes positively phrased variants so that high is worse`() {
        val q1 = q.items.single { it.id == "Q1_FATIGUE" }
        val positive = q1.variants.single { it.inverted }
        val negative = q1.variants.first { !it.inverted }
        // Scale 1–7: answering 2 to a positive wording ("little sharpness") equals 6.
        assertEquals(6.0, QuestionnaireSelector.normalize(q1, positive, 2), 0.0)
        assertEquals(2.0, QuestionnaireSelector.normalize(q1, negative, 2), 0.0)
        assertEquals(7.0, QuestionnaireSelector.normalize(q1, positive, 1), 0.0)
    }
}
