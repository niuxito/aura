package com.aura.astronaut.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.aura.astronaut.mission.Lang
import com.aura.astronaut.mission.MissionPackage

/**
 * Interface texts from Android resources (AGENTS.md, "Languages"): `values/strings.xml` in English
 * and `values-es/strings.xml` in Spanish. Modules without Compose (HUD and voice texts, adaptation
 * reasons, notices) request them here; tests install a provider that reads the same XML files.
 */
object Strings {
    fun interface Provider {
        fun get(@StringRes id: Int, args: Array<out Any>): String
    }

    @Volatile private var provider: Provider? = null

    fun install(p: Provider) {
        provider = p
    }

    /** With the activity's context, to follow its language (also the one chosen for the app on Android 13+). */
    fun init(context: Context) {
        val localized = context.applicationContext.createConfigurationContext(context.resources.configuration)
        install { id, args -> if (args.isEmpty()) localized.getString(id) else localized.getString(id, *args) }
    }

    fun get(@StringRes id: Int, vararg args: Any): String =
        checkNotNull(provider) { "Strings not initialized" }.get(id, args)
}

/** Shortcut for modules without Compose. */
fun str(@StringRes id: Int, vararg args: Any): String = Strings.get(id, *args)

/**
 * Active app language: the system's (or the one chosen for the app) if the package carries it; otherwise the
 * package's default language. It is chosen in demo mode with Android 13+ per-app language preferences.
 */
object AppLanguage {
    /** Languages with interface texts in the resources. */
    val SUPPORTED = listOf("en", "es")

    fun current(context: Context): String = context.resources.configuration.locales[0].language

    /** Sets the language of package texts to the interface's. */
    fun applyTo(pkg: MissionPackage, context: Context) = Lang.configure(pkg, current(context))

    /** Language chosen for the app; null = the system's. */
    fun chosen(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            null
        }

    /** Sets the app language (null = the system's). The activity is recreated with it. */
    fun choose(context: Context, language: String?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val manager = context.getSystemService(LocaleManager::class.java) ?: return false
        manager.applicationLocales = if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
        return true
    }

    val canChoose: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
