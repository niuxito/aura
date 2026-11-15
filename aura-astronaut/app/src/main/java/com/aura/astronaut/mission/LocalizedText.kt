package com.aura.astronaut.mission

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Locale

/**
 * Package text in several languages: `{ "en": "Sleep", "es": "Sueño" }` (AGENTS.md, "Languages").
 * Always resolved with [Lang]: active language → package default language → the first available.
 * `toString()` returns the resolved text, so it can be used directly in templates.
 */
@Serializable(with = LocalizedTextSerializer::class)
class LocalizedText(val values: Map<String, String>) {
    val text: String get() = Lang.resolve(values)

    /** All translations (to search keywords regardless of the active language). */
    val all: Collection<String> get() = values.values

    override fun toString(): String = text
    override fun equals(other: Any?) = other is LocalizedText && other.values == values
    override fun hashCode() = values.hashCode()

    companion object {
        fun of(vararg pairs: Pair<String, String>) = LocalizedText(mapOf(*pairs))
    }
}

/** Active language of package texts. The single resolution function of the whole app. */
object Lang {
    @Volatile var active: String = Locale.getDefault().language
    @Volatile var fallback: String = "en"

    fun resolve(values: Map<String, String>): String =
        values[active]?.takeIf { it.isNotBlank() }
            ?: values[fallback]?.takeIf { it.isNotBlank() }
            ?: values.values.firstOrNull { it.isNotBlank() }
            ?: ""

    /** System language if the package carries it; otherwise its default language. */
    fun configure(pkg: MissionPackage, systemLanguage: String = Locale.getDefault().language) {
        fallback = pkg.defaultLanguage
        active = if (systemLanguage in pkg.languages) systemLanguage else pkg.defaultLanguage
    }
}

object LocalizedTextSerializer : KSerializer<LocalizedText> {
    private val delegate = MapSerializer(String.serializer(), String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder) = LocalizedText(delegate.deserialize(decoder))
    override fun serialize(encoder: Encoder, value: LocalizedText) = delegate.serialize(encoder, value.values)
}
