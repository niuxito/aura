package com.aura.astronaut.mission

import kotlin.math.abs

/*
 * MET (Mission Elapsed Time): tiempo desde el despegue, en segundos.
 * Se escribe DD/HH:MM o DD/HH:MM:SS, como en los cronogramas de NASA (TLI en 01/01:08:42).
 * Réplica de ../aura-manager/lib/met.ts: las etiquetas deben coincidir con las del Manager.
 */
object Met {
    const val MINUTE = 60L
    const val HOUR = 3_600L
    const val DAY = 86_400L

    private val PATTERN = Regex("""^(-)?(\d{1,3})/(\d{1,2}):(\d{2})(?::(\d{2}))?$""")

    /** Formato del Manager: los segundos solo aparecen si no son cero. */
    fun format(seconds: Long): String = format(seconds, alwaysSeconds = false)

    /** Para el reloj del HUD, que siempre muestra segundos: DD/HH:MM:SS. */
    fun formatClock(seconds: Long): String = format(seconds, alwaysSeconds = true)

    private fun format(seconds: Long, alwaysSeconds: Boolean): String {
        val sign = if (seconds < 0) "-" else ""
        val a = abs(seconds)
        val d = a / DAY
        val h = (a % DAY) / HOUR
        val m = (a % HOUR) / MINUTE
        val s = a % MINUTE
        val base = "$sign${pad(d)}/${pad(h)}:${pad(m)}"
        return if (alwaysSeconds || s != 0L) "$base:${pad(s)}" else base
    }

    /** Acepta D/HH:MM y D/HH:MM:SS con signo opcional; null si el texto no es un MET válido. */
    fun parse(text: String): Long? {
        val match = PATTERN.matchEntire(text.trim()) ?: return null
        val (sign, d, h, m, s) = match.destructured
        val hours = h.toLong()
        val minutes = m.toLong()
        val secs = if (s.isEmpty()) 0L else s.toLong()
        if (hours > 23 || minutes > 59 || secs > 59) return null
        val total = d.toLong() * DAY + hours * HOUR + minutes * MINUTE + secs
        return if (sign.isNotEmpty()) -total else total
    }

    fun formatFlightDay(day: Int): String = "FD${pad(day.toLong())}"

    private fun pad(n: Long) = n.toString().padStart(2, '0')
}

/**
 * Día de vuelo al que pertenece un MET: el que cumple `startMet <= met < endMet`.
 * El último día incluye su `endMet` (el amerizaje cae justo ahí), igual que `flightDayOf` del Manager.
 */
fun List<FlightDay>.dayAt(met: Long): FlightDay? {
    val last = lastOrNull()
    return firstOrNull { d -> met >= d.startMet && (met < d.endMet || (d === last && met == d.endMet)) }
}

/** Dónde está la misión en un MET dado. */
sealed interface MissionPhase {
    data object BeforeLaunch : MissionPhase
    data class InFlight(val day: FlightDay) : MissionPhase
    data object AfterEnd : MissionPhase
    /** El MET cae en un hueco entre días de vuelo (no debería ocurrir con un paquete coherente). */
    data object Unscheduled : MissionPhase
}

fun MissionPackage.phaseAt(met: Long): MissionPhase {
    val first = days.first()
    val last = days.last()
    return when {
        met < first.startMet -> MissionPhase.BeforeLaunch
        met > last.endMet -> MissionPhase.AfterEnd
        else -> days.dayAt(met)?.let { MissionPhase.InFlight(it) } ?: MissionPhase.Unscheduled
    }
}

/** Fin de la misión según el paquete (MET del último instante del último día de vuelo). */
val MissionPackage.endMet: Long get() = days.last().endMet
