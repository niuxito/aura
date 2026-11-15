package com.aura.astronaut.hud

import com.aura.astronaut.mission.EntryStatus
import com.aura.astronaut.mission.Gravity
import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionPackageParser
import com.aura.astronaut.mission.MissionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MissionSnapshotTest {

    init {
        com.aura.astronaut.i18n.TestStrings.install("es")
    }

    private val pkg = MissionPackageParser.parse(File("../contract/mission-package.example.json").readText()).pkg
    private fun at(label: String, crew: String = "cdr") = MissionSnapshot.build(pkg, crew, Met.parse(label)!!)

    @Test
    fun `contexto del primer dia tras el despegue`() {
        val s = at("00/05:10")
        assertEquals(1, s.day?.day)
        assertEquals(Gravity.MICRO, s.gravityNow)
        assertEquals(listOf("Configuración de cabina"), s.currentBlocks.map { it.name.text })
    }

    @Test
    fun `antes del despegue se esta en Tierra y se anuncia el despegue`() {
        val s = MissionSnapshot.build(pkg, "cdr", -600)
        assertEquals(MissionPhase.BeforeLaunch, s.phase)
        assertEquals(Gravity.EARTH, s.gravityNow)
        val t = s.transition!!
        assertEquals("Despegue", t.eventName)
        assertEquals(600L, t.secondsLeft)
        assertEquals(Gravity.MICRO, t.to)
    }

    @Test
    fun `el amerizaje se anuncia con dos dias de antelacion y no antes`() {
        // FD07 está a tres días de FD10: todavía no se anuncia.
        assertNull(at("06/00:00").transition)
        // FD08: FD10 entra en la ventana de los dos días siguientes.
        val t = at("07/00:00").transition
        assertNotNull(t)
        assertEquals(10, t!!.day.day)
        assertEquals("Amerizaje", t.eventName)
        assertTrue(t.exact)
        assertEquals(pkg.days.last().endMet, t.met)
        assertEquals(Gravity.MICRO, t.from)
        assertEquals(Gravity.EARTH, t.to)
    }

    @Test
    fun `en FD10 sigue en microgravedad hasta el amerizaje`() {
        val s = at("09/01:00")
        assertEquals(10, s.day?.day)
        assertEquals(Gravity.MICRO, s.gravityNow)
        assertEquals(Met.parse("00/00:42:48"), s.transition?.secondsLeft)
    }

    @Test
    fun `una transicion pasada no se anuncia`() {
        // FD01, ya en órbita: el despegue ha ocurrido y FD10 queda lejos.
        assertNull(at("00/05:00").transition)
    }

    @Test
    fun `siguiente accion y actividad que toca ahora`() {
        // CDR, FD01: SD a las 00/05:30 y F a las 00/07:15.
        val before = at("00/05:00").nextAction!!
        assertEquals("SD", before.plan.item.id)
        assertEquals(30 * 60L, before.secondsUntil)
        assertEquals(EntryStatus.CONDITIONAL, before.plan.activity.status)

        val now = at("00/05:40").nextAction!!
        assertEquals("SD", now.plan.item.id)
        assertTrue(now.secondsUntil <= 0)
        assertEquals(TimingState.NOW, now.plan.state)

        // Pasada la ventana de «ahora», la siguiente es F.
        val after = at("00/06:05").nextAction!!
        assertEquals("F", after.plan.item.id)
        assertFalse(after.laterDay)
    }

    @Test
    fun `sin actividades restantes hoy se propone la del dia siguiente`() {
        // FD01 de CDR acaba en 00/17:00; su última actividad es a las 00/07:15.
        val s = at("00/12:00")
        val n = s.nextAction!!
        assertEquals(2, n.plan.day.day)
        assertTrue(n.laterDay)
        assertTrue(HudTexts.nextActionReason(n).startsWith("No quedan actividades hoy."))
    }

    @Test
    fun `el plan de hoy es el del tripulante y del dia`() {
        val s = at("01/01:00", crew = "plt")
        assertEquals(2, s.day?.day)
        assertTrue(s.todayPlan.isNotEmpty())
        assertTrue(s.todayPlan.all { it.day.day == 2 && it.activity.entryId.startsWith("plt-") })
    }

    @Test
    fun `avisos de control de mision del dia y del tripulante`() {
        // El paquete demo ya no trae avisos por día y tripulante: se añaden a una copia.
        fun issue(day: Int?, crew: String?) = com.aura.astronaut.mission.ValidationIssue(
            id = "t-$day-$crew", rule = "t", severity = com.aura.astronaut.mission.Severity.WARNING, day = day, crewId = crew,
            message = com.aura.astronaut.mission.LocalizedText.of("es" to "aviso"),
        )
        val p = pkg.copy(validation = pkg.validation + issue(9, "cdr") + issue(9, "plt") + issue(8, "cdr") + issue(null, null))
        val s = MissionSnapshot.build(p, "cdr", Met.parse("07/20:00")!!)
        assertEquals(9, s.day?.day)
        assertTrue(s.notices.any { it.day == 9 && it.crewId == "cdr" })
        assertTrue(s.notices.none { it.crewId != null && it.crewId != "cdr" })
        assertTrue(s.notices.none { it.day != null && it.day != 9 })
    }

    @Test
    fun `la transicion usa el MET exacto del paquete`() {
        val fd10 = pkg.days.last()
        assertEquals(Met.parse("09/01:42:48"), fd10.transitionMet)
        val s = at("09/00:00")
        val t = s.transition!!
        assertEquals("Amerizaje", t.eventName)
        assertEquals(Met.parse("09/01:42:48")!! - Met.parse("09/00:00")!!, t.secondsLeft)
        // Sin transitionMet, se recurre al hito del fin del día.
        val legacy = pkg.copy(days = pkg.days.map { it.copy(transitionMet = null) })
        assertEquals(t.met, MissionSnapshot.transitionMoment(legacy, legacy.days.last()).first)
    }

    @Test
    fun `una actividad esta en curso hasta su fin previsto`() {
        val r = pkg.plans.getValue("cdr").flatMap { it.activities }.first { it.itemId == "R" }
        val item = pkg.catalog.single { it.id == "R" }
        assertEquals(45, item.durationMin)
        assertEquals(r.met + 45 * 60, r.endMet)
        assertEquals(r.met + 45 * 60, MissionSnapshot.endOf(r, item))
        assertEquals(TimingState.NOW, MissionSnapshot.timingOf(r.met, MissionSnapshot.endOf(r, item), r.met + 40 * 60))
        // Sin duración, la ventana de demostración.
        assertEquals(r.met + HudTiming.NOW_WINDOW_SECONDS, MissionSnapshot.endOf(r.copy(endMet = null), item.copy(durationMin = null)))
    }

    @Test
    fun `las actividades condicionales dicen de que dependen`() {
        val conditional = pkg.plans.values.flatten().flatMap { it.activities }.filter { it.status == EntryStatus.CONDITIONAL }
        assertTrue(conditional.isNotEmpty())
        assertTrue(conditional.all { it.note != null && it.note!!.text.isNotBlank() })
    }

    @Test
    fun `tras el amerizaje no hay transicion ni siguiente accion`() {
        val s = MissionSnapshot.build(pkg, "cdr", pkg.days.last().endMet + 60)
        assertEquals(MissionPhase.AfterEnd, s.phase)
        assertNull(s.transition)
        assertNull(s.nextAction)
        assertEquals(Gravity.EARTH, s.gravityNow)
    }

    @Test
    fun `plan completo del dia ordenado con la marca de ahora`() {
        val day = pkg.days.first()
        val t = DayTimeline.build(pkg, "cdr", day, Met.parse("00/05:40")!!)
        assertEquals(t.entries.map { it.met }, t.entries.map { it.met }.sorted())
        assertEquals(1, t.entries.count { it is DayEntry.Now })
        assertEquals(2, t.entries.count { it is DayEntry.Activity })
        assertTrue(t.entries.any { it is DayEntry.Event && it.event.name.text == "Despegue" })
    }

    @Test
    fun `una actividad confirmada deja de ser la siguiente accion`() {
        val met = Met.parse("00/05:40")!!
        val sd = MissionSnapshot.build(pkg, "cdr", met).nextAction!!.plan
        assertEquals("SD", sd.item.id)
        val done = com.aura.astronaut.data.ActivityConfirmation(
            crewId = "cdr", entryId = sd.activity.entryId, itemId = "SD", packageVersion = 1,
            status = com.aura.astronaut.data.ConfirmationStatus.DONE, reason = null,
            confirmedAtMet = met, recordedAtUtcMs = 0,
        )
        val after = MissionSnapshot.build(pkg, "cdr", met, mapOf(sd.activity.entryId to done))
        assertEquals("F", after.nextAction!!.plan.item.id)
        assertEquals(done, after.todayPlan.single { it.item.id == "SD" }.confirmation)
        // Omitir también la saca de la siguiente acción.
        val omitted = done.copy(status = com.aura.astronaut.data.ConfirmationStatus.OMITTED, reason = "Fatiga o malestar")
        assertEquals("F", MissionSnapshot.build(pkg, "cdr", met, mapOf(sd.activity.entryId to omitted)).nextAction!!.plan.item.id)
    }
}
