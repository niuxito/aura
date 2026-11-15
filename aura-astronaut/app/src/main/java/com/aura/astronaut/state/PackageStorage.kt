package com.aura.astronaut.state

import android.content.Context
import androidx.core.content.edit
import com.aura.astronaut.sync.PackageOrigin
import java.io.File

/**
 * Last package downloaded from AURA Manager, stored as is as JSON in the app's private
 * storage, with its origin. It survives closing the app and is used offline.
 */
class PackageStorage(context: Context) {
    private val dir = File(context.filesDir, "packages")
    private val file = File(dir, "mission-package.json")
    private val prefs = context.getSharedPreferences("package_storage", Context.MODE_PRIVATE)

    /** Text and origin of the downloaded package, or null if there is none. */
    fun load(): Pair<String, PackageOrigin.Downloaded>? {
        if (!file.exists()) return null
        val url = prefs.getString(KEY_URL, null) ?: return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return text to PackageOrigin.Downloaded(url, prefs.getLong(KEY_AT, 0))
    }

    /** Saves atomically: first a temporary file, then it is renamed. */
    fun save(text: String, origin: PackageOrigin.Downloaded) {
        dir.mkdirs()
        val tmp = File(dir, "mission-package.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save the downloaded package" }
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
