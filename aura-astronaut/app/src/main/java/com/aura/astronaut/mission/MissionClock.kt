package com.aura.astronaut.mission

import java.time.Instant
import java.time.format.DateTimeParseException

/** Where the current MET comes from. */
enum class ClockSource {
    /** Set by hand in demo mode; may run accelerated. */
    MANUAL,
    /** Computed with the device clock from `mission.launchUtc`. */
    LAUNCH_UTC,
}

/**
 * Mission clock configuration. The manual MET is stored as an anchor
 * (MET at a real instant) plus a rate, so it keeps running even if the app is closed.
 */
data class ClockSettings(
    val source: ClockSource = ClockSource.MANUAL,
    /** MET (s) at instant [anchorWallMs]. */
    val anchorMet: Long = 0,
    /** Real instant (ms since epoch) at which [anchorMet] was set. */
    val anchorWallMs: Long = 0,
    /** MET seconds per real second. */
    val rate: Int = 1,
    val running: Boolean = true,
)

object MissionClock {
    /**
     * Demo mode rates: real time, 1 min/s, 10 min/s and 1 h/s.
     * At 1 h/s, a flight day of about 17 h passes in about 17 s.
     */
    val DEMO_RATES = listOf(1, 60, 600, 3_600)

    /** Current MET. If the source is launchUtc but the package does not carry it, the manual MET is used. */
    fun metAt(settings: ClockSettings, nowWallMs: Long, launchUtcMs: Long?): Long {
        if (settings.source == ClockSource.LAUNCH_UTC && launchUtcMs != null) {
            return Math.floorDiv(nowWallMs - launchUtcMs, 1_000L)
        }
        if (!settings.running) return settings.anchorMet
        val elapsedMs = nowWallMs - settings.anchorWallMs
        return settings.anchorMet + Math.floorDiv(elapsedMs * settings.rate, 1_000L)
    }

    /** Sets the MET by hand: switches to manual mode and keeps the rate and whether it is running. */
    fun setMet(settings: ClockSettings, met: Long, nowWallMs: Long): ClockSettings =
        settings.copy(source = ClockSource.MANUAL, anchorMet = met, anchorWallMs = nowWallMs)

    /** Changes the rate without jumps: re-anchors at the current MET. */
    fun setRate(settings: ClockSettings, rate: Int, nowWallMs: Long, launchUtcMs: Long?): ClockSettings {
        require(rate > 0) { "The rate must be positive" }
        return reanchor(settings, nowWallMs, launchUtcMs).copy(rate = rate)
    }

    fun setRunning(settings: ClockSettings, running: Boolean, nowWallMs: Long, launchUtcMs: Long?): ClockSettings =
        reanchor(settings, nowWallMs, launchUtcMs).copy(running = running)

    /** Changes the source; when going back to manual it starts from the MET being shown. */
    fun setSource(settings: ClockSettings, source: ClockSource, nowWallMs: Long, launchUtcMs: Long?): ClockSettings =
        reanchor(settings, nowWallMs, launchUtcMs).copy(source = source)

    private fun reanchor(settings: ClockSettings, nowWallMs: Long, launchUtcMs: Long?): ClockSettings =
        settings.copy(anchorMet = metAt(settings, nowWallMs, launchUtcMs), anchorWallMs = nowWallMs)

    /** `launchUtc` in milliseconds, or null if it is not set or is not a valid ISO-8601 instant. */
    fun launchUtcMs(launchUtc: String?): Long? = launchUtc?.let {
        try {
            Instant.parse(it).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
