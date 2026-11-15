package com.aura.astronaut.hud

import com.aura.astronaut.mission.Met
import com.aura.astronaut.mission.MissionPackageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HudTextsTest {

    init {
        com.aura.astronaut.i18n.TestStrings.install("es")
    }

    private val pkg = MissionPackageParser.parse(File("../contract/mission-package.example.json").readText()).pkg

    @Test
    fun `duraciones legibles`() {
        assertEquals("menos de 1 min", HudTexts.formatDuration(59))
        assertEquals("1 min", HudTexts.formatDuration(60))
        assertEquals("2 h 15 min", HudTexts.formatDuration(2 * 3600 + 15 * 60 + 30))
        assertEquals("1 d 6 h", HudTexts.formatDuration(Met.DAY + 6 * Met.HOUR + 10 * Met.MINUTE))
        assertEquals("1 d 5 min", HudTexts.formatDuration(Met.DAY + 5 * Met.MINUTE))
    }

    @Test
    fun `aviso de amerizaje con la gravedad del paquete`() {
        val t = MissionSnapshot.build(pkg, "cdr", Met.parse("08/00:00")!!).transition!!
        assertEquals("Amerizaje en 1 d 1 h", HudTexts.transitionTitle(t))
        assertEquals("De microgravedad a Tierra · 1 g", HudTexts.transitionChange(t, pkg.gravityReference))
        val a = HudTexts.transitionAnnouncement(t, pkg.gravityReference)
        assertEquals("Transición gravitatoria. Amerizaje en 1 d 1 h. De microgravedad a Tierra · 1 g.", a.text)
        assertEquals("transition-10", a.id)
    }

    @Test
    fun `la voz dice lo mismo que el HUD`() {
        val s = MissionSnapshot.build(pkg, "cdr", Met.parse("00/05:00")!!)
        val next = s.nextAction!!
        val spoken = HudTexts.announcementsFor(s, pkg.gravityReference).single { it.id.startsWith("next-") }
        assertTrue(spoken.text.startsWith(HudTexts.nextActionTitle(next)))
        assertEquals("Siguiente acción, en 30 min: SD · Suplemento de vitamina D (ejemplo)", HudTexts.nextActionTitle(next))
        assertTrue(HudTexts.nextActionReason(next).contains("Condicional, no obligatoria"))
    }

    @Test
    fun `al pasar a toca ahora es un anuncio nuevo`() {
        val before = MissionSnapshot.build(pkg, "cdr", Met.parse("00/05:00")!!).nextAction!!
        val now = MissionSnapshot.build(pkg, "cdr", Met.parse("00/05:31")!!).nextAction!!
        assertNotEquals(HudTexts.nextActionAnnouncement(before).id, HudTexts.nextActionAnnouncement(now).id)
        assertTrue(HudTexts.nextActionTitle(now).startsWith("Toca ahora: SD"))
    }

    @Test
    fun `la transicion tiene mas prioridad que la siguiente accion`() {
        assertTrue(HudTexts.PRIORITY_TRANSITION > HudTexts.PRIORITY_NEXT_ACTION)
    }
}
