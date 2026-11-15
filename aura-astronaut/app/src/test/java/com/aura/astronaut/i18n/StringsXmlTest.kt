package com.aura.astronaut.i18n

import com.aura.astronaut.R
import com.aura.astronaut.hud.HudTexts
import com.aura.astronaut.mission.CapacityLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Los textos de la interfaz existen en inglés y en español, con los mismos argumentos de formato. */
class StringsXmlTest {

    private fun read(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i -> nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent }
    }

    private val args = Regex("%(\\d+)\\$([sd])")

    @After
    fun back() = TestStrings.install("es")

    @Test
    fun `cada texto tiene su traduccion con los mismos argumentos`() {
        val en = read("values")
        val es = read("values-es")
        val missing = en.keys - es.keys - setOf("app_name")
        assertEquals("Faltan en español", emptySet<String>(), missing)
        assertEquals("Sobran en español", emptySet<String>(), es.keys - en.keys)
        es.forEach { (name, text) ->
            assertEquals("Argumentos de $name", args.findAll(en.getValue(name)).map { it.value }.toSet(), args.findAll(text).map { it.value }.toSet())
            assertTrue("$name vacío", text.isNotBlank())
        }
    }

    @Test
    fun `la interfaz funciona en ingles`() {
        TestStrings.install("en")
        assertEquals("AMBER", HudTexts.levelWord(CapacityLevel.AMBER))
        assertEquals("less than 1 min", HudTexts.formatDuration(30))
        assertEquals("Next action, in 2 h 15 min", str(R.string.next_in, HudTexts.formatDuration(2 * 3600 + 15 * 60)))
        TestStrings.install("es")
        assertEquals("ÁMBAR", HudTexts.levelWord(CapacityLevel.AMBER))
    }
}
