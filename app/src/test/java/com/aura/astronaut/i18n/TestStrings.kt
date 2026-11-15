package com.aura.astronaut.i18n

import com.aura.astronaut.R
import com.aura.astronaut.mission.Lang
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Proveedor de textos para las pruebas unitarias: lee los mismos `strings.xml` que la app
 * (el idioma pedido sobre el inglés por defecto) y formatea como `Resources.getString`.
 */
object TestStrings {
    private val names: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    private fun read(dir: String): Map<String, String> {
        val file = File("src/main/res/$dir/strings.xml")
        if (!file.exists()) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            n.attributes.getNamedItem("name").nodeValue to unescape(n.textContent)
        }
    }

    private fun unescape(s: String): String {
        var t = s.trim()
        if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) t = t.substring(1, t.length - 1)
        return t.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")
    }

    fun install(language: String = "es") {
        val texts = read("values") + if (language == "en") emptyMap() else read("values-$language")
        val locale = Locale.forLanguageTag(language)
        Lang.active = language
        Strings.install { id, args ->
            val name = names[id] ?: error("Recurso desconocido $id")
            val raw = texts[name] ?: error("Falta el texto $name")
            if (args.isEmpty()) raw else String.format(locale, raw, *args)
        }
    }
}
