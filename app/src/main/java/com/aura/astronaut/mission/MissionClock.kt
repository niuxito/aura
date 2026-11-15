package com.aura.astronaut.mission

import java.time.Instant
import java.time.format.DateTimeParseException

/** De dónde sale el MET actual. */
enum class ClockSource {
    /** Fijado a mano en el modo demo; puede avanzar acelerado. */
    MANUAL,
    /** Calculado con el reloj del dispositivo desde `mission.launchUtc`. */
    LAUNCH_UTC,
}

/**
 * Configuración del reloj de misión. El MET manual se guarda como un ancla
 * (MET en un instante real) más una velocidad, así sigue corriendo aunque la app se cierre.
 */
data class ClockSettings(
    val source: ClockSource = ClockSource.MANUAL,
    /** MET (s) en el instante [anchorWallMs]. */
    val anchorMet: Long = 0,
    /** Instante real (ms desde epoch) en que se fijó [anchorMet]. */
    val anchorWallMs: Long = 0,
    /** Segundos de MET por segundo real. */
    val rate: Int = 1,
    val running: Boolean = true,
)

object MissionClock {
    /**
     * Velocidades del modo demo: tiempo real, 1 min/s, 10 min/s y 1 h/s.
     * Con 1 h/s, un día de vuelo de unas 17 h pasa en unos 17 s.
     */
    val DEMO_RATES = listOf(1, 60, 600, 3_600)

    /** MET actual. Si la fuente es launchUtc pero el paquete no lo trae, se usa el MET manual. */
    fun metAt(settings: ClockSettings, nowWallMs: Long, launchUtcMs: Long?): Long {
        if (settings.source == ClockSource.LAUNCH_UTC && launchUtcMs != null) {
            return Math.floorDiv(nowWallMs - launchUtcMs, 1_000L)
        }
        if (!settings.running) return settings.anchorMet
        val elapsedMs = nowWallMs - settings.anchorWallMs
        return settings.anchorMet + Math.floorDiv(elapsedMs * settings.rate, 1_000L)
    }

    /** Fija el MET a mano: pasa a modo manual y conserva la velocidad y si corre o no. */
    fun setMet(settings: ClockSettings, met: Long, nowWallMs: Long): ClockSettings =
        settings.copy(source = ClockSource.MANUAL, anchorMet = met, anchorWallMs = nowWallMs)

    /** Cambia la velocidad sin saltos: re-ancla en el MET actual. */
    fun setRate(settings: ClockSettings, rate: Int, nowWallMs: Long, launchUtcMs: Long?): ClockSettings {
        require(rate > 0) { "La velocidad debe ser positiva" }
        return reanchor(settings, nowWallMs, launchUtcMs).copy(rate = rate)
    }

    fun setRunning(settings: ClockSettings, running: Boolean, nowWallMs: Long, launchUtcMs: Long?): ClockSettings =
        reanchor(settings, nowWallMs, launchUtcMs).copy(running = running)

    /** Cambia de fuente; al volver a manual se parte del MET que se estaba mostrando. */
    fun setSource(settings: ClockSettings, source: ClockSource, nowWallMs: Long, launchUtcMs: Long?): ClockSettings =
        reanchor(settings, nowWallMs, launchUtcMs).copy(source = source)

    private fun reanchor(settings: ClockSettings, nowWallMs: Long, launchUtcMs: Long?): ClockSettings =
        settings.copy(anchorMet = metAt(settings, nowWallMs, launchUtcMs), anchorWallMs = nowWallMs)

    /** `launchUtc` en milisegundos, o null si no está fijado o no es un instante ISO-8601 válido. */
    fun launchUtcMs(launchUtc: String?): Long? = launchUtc?.let {
        try {
            Instant.parse(it).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
