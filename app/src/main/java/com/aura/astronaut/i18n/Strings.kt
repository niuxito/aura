package com.aura.astronaut.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.aura.astronaut.mission.Lang
import com.aura.astronaut.mission.MissionPackage

/**
 * Textos de la interfaz desde los recursos de Android (AGENTS.md, «Idiomas»): `values/strings.xml` en inglés
 * y `values-es/strings.xml` en español. Los módulos sin Compose (textos del HUD y de la voz, motivos de las
 * adaptaciones, avisos) los piden aquí; las pruebas instalan un proveedor que lee los mismos XML.
 */
object Strings {
    fun interface Provider {
        fun get(@StringRes id: Int, args: Array<out Any>): String
    }

    @Volatile private var provider: Provider? = null

    fun install(p: Provider) {
        provider = p
    }

    /** Con el contexto de la actividad, para seguir su idioma (también el elegido para la app en Android 13+). */
    fun init(context: Context) {
        val localized = context.applicationContext.createConfigurationContext(context.resources.configuration)
        install { id, args -> if (args.isEmpty()) localized.getString(id) else localized.getString(id, *args) }
    }

    fun get(@StringRes id: Int, vararg args: Any): String =
        checkNotNull(provider) { "Strings sin inicializar" }.get(id, args)
}

/** Atajo para los módulos sin Compose. */
fun str(@StringRes id: Int, vararg args: Any): String = Strings.get(id, *args)

/**
 * Idioma activo de la app: el del sistema (o el elegido para la app) si el paquete lo trae; si no, el idioma
 * por defecto del paquete. Se elige en el modo demo con las preferencias de idioma por app de Android 13+.
 */
object AppLanguage {
    /** Idiomas con textos de interfaz en los recursos. */
    val SUPPORTED = listOf("en", "es")

    fun current(context: Context): String = context.resources.configuration.locales[0].language

    /** Ajusta el idioma de los textos del paquete al de la interfaz. */
    fun applyTo(pkg: MissionPackage, context: Context) = Lang.configure(pkg, current(context))

    /** Idioma elegido para la app; null = el del sistema. */
    fun chosen(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            null
        }

    /** Fija el idioma de la app (null = el del sistema). La actividad se recrea con él. */
    fun choose(context: Context, language: String?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val manager = context.getSystemService(LocaleManager::class.java) ?: return false
        manager.applicationLocales = if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
        return true
    }

    val canChoose: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
