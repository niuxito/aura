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
 * Texto del paquete en varios idiomas: `{ "en": "Sleep", "es": "Sueño" }` (AGENTS.md, «Idiomas»).
 * Se resuelve siempre con [Lang]: idioma activo → idioma por defecto del paquete → el primero que haya.
 * `toString()` devuelve el texto resuelto, así que puede usarse directamente en plantillas.
 */
@Serializable(with = LocalizedTextSerializer::class)
class LocalizedText(val values: Map<String, String>) {
    val text: String get() = Lang.resolve(values)

    /** Todas las traducciones (para buscar palabras clave sin depender del idioma activo). */
    val all: Collection<String> get() = values.values

    override fun toString(): String = text
    override fun equals(other: Any?) = other is LocalizedText && other.values == values
    override fun hashCode() = values.hashCode()

    companion object {
        fun of(vararg pairs: Pair<String, String>) = LocalizedText(mapOf(*pairs))
    }
}

/** Idioma activo de los textos del paquete. Única función de resolución de toda la app. */
object Lang {
    @Volatile var active: String = Locale.getDefault().language
    @Volatile var fallback: String = "en"

    fun resolve(values: Map<String, String>): String =
        values[active]?.takeIf { it.isNotBlank() }
            ?: values[fallback]?.takeIf { it.isNotBlank() }
            ?: values.values.firstOrNull { it.isNotBlank() }
            ?: ""

    /** Idioma del sistema si el paquete lo trae; si no, su idioma por defecto. */
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
