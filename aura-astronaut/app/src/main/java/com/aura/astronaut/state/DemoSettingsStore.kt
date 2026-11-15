package com.aura.astronaut.state

import android.content.Context
import androidx.core.content.edit
import com.aura.astronaut.mission.ClockSettings
import com.aura.astronaut.heart.HeartSource
import com.aura.astronaut.mission.ClockSource

/**
 * Guarda en el dispositivo el astronauta elegido y el reloj de misión,
 * para que sobrevivan a cerrar la app. No contiene datos de salud.
 */
class DemoSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("demo_settings", Context.MODE_PRIVATE)

    fun loadCrewId(): String? = prefs.getString(KEY_CREW, null)

    fun saveCrewId(id: String) = prefs.edit { putString(KEY_CREW, id) }

    /** Fuente de la frecuencia cardiaca: reloj, simulada o apagada. Por defecto, simulada (demo sin reloj). */
    fun loadHeartSource(): HeartSource =
        prefs.getString(KEY_HEART_SOURCE, null)?.let { runCatching { HeartSource.valueOf(it) }.getOrNull() } ?: HeartSource.SIMULATED

    fun saveHeartSource(s: HeartSource) = prefs.edit { putString(KEY_HEART_SOURCE, s.name) }

    /** Dirección Bluetooth del reloj elegido. */
    fun loadWatchAddress(): String? = prefs.getString(KEY_WATCH, null)

    fun saveWatchAddress(address: String?) = prefs.edit { if (address == null) remove(KEY_WATCH) else putString(KEY_WATCH, address) }

    /** URL de AURA Manager elegida en el modo demo; null = la de BuildConfig. */
    fun loadManagerUrl(): String? = prefs.getString(KEY_MANAGER_URL, null)

    fun saveManagerUrl(url: String) = prefs.edit { putString(KEY_MANAGER_URL, url) }

    fun loadClock(): ClockSettings? {
        if (!prefs.contains(KEY_ANCHOR_MET)) return null
        return ClockSettings(
            source = prefs.getString(KEY_SOURCE, null)
                ?.let { runCatching { ClockSource.valueOf(it) }.getOrNull() }
                ?: ClockSource.MANUAL,
            anchorMet = prefs.getLong(KEY_ANCHOR_MET, 0),
            anchorWallMs = prefs.getLong(KEY_ANCHOR_WALL, 0),
            rate = prefs.getInt(KEY_RATE, 1).coerceAtLeast(1),
            running = prefs.getBoolean(KEY_RUNNING, true),
        )
    }

    fun saveClock(s: ClockSettings) = prefs.edit {
        putString(KEY_SOURCE, s.source.name)
        putLong(KEY_ANCHOR_MET, s.anchorMet)
        putLong(KEY_ANCHOR_WALL, s.anchorWallMs)
        putInt(KEY_RATE, s.rate)
        putBoolean(KEY_RUNNING, s.running)
    }

    private companion object {
        const val KEY_CREW = "crew_id"
        const val KEY_SOURCE = "clock_source"
        const val KEY_ANCHOR_MET = "clock_anchor_met"
        const val KEY_ANCHOR_WALL = "clock_anchor_wall_ms"
        const val KEY_RATE = "clock_rate"
        const val KEY_RUNNING = "clock_running"
        const val KEY_MANAGER_URL = "manager_url"
        const val KEY_HEART_SOURCE = "heart_source"
        const val KEY_WATCH = "watch_address"
    }
}
