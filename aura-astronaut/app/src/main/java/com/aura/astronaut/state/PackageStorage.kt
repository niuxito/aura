package com.aura.astronaut.state

import android.content.Context
import androidx.core.content.edit
import com.aura.astronaut.sync.PackageOrigin
import java.io.File

/**
 * Último paquete descargado de AURA Manager, guardado tal cual como JSON en el almacenamiento
 * privado de la app, con su procedencia. Sobrevive a cerrar la app y se usa sin conexión.
 */
class PackageStorage(context: Context) {
    private val dir = File(context.filesDir, "packages")
    private val file = File(dir, "mission-package.json")
    private val prefs = context.getSharedPreferences("package_storage", Context.MODE_PRIVATE)

    /** Texto y procedencia del paquete descargado, o null si no hay ninguno. */
    fun load(): Pair<String, PackageOrigin.Downloaded>? {
        if (!file.exists()) return null
        val url = prefs.getString(KEY_URL, null) ?: return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return text to PackageOrigin.Downloaded(url, prefs.getLong(KEY_AT, 0))
    }

    /** Guarda de forma atómica: primero un temporal y luego se renombra. */
    fun save(text: String, origin: PackageOrigin.Downloaded) {
        dir.mkdirs()
        val tmp = File(dir, "mission-package.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "No se pudo guardar el paquete descargado" }
        }
        prefs.edit {
            putString(KEY_URL, origin.url)
            putLong(KEY_AT, origin.atMs)
        }
    }

    fun clear() {
        file.delete()
        prefs.edit { clear() }
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_AT = "downloaded_at_ms"
    }
}
