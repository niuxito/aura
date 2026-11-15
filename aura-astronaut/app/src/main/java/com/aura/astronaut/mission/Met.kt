package com.aura.astronaut.mission

import kotlin.math.abs

/*
 * MET (Mission Elapsed Time): time since launch, in seconds.
 * Written DD/HH:MM or DD/HH:MM:SS, as in NASA timelines (TLI at 01/01:08:42).
 * Replica of ../aura-manager/lib/met.ts: labels must match the Manager's.
 */
object Met {
    const val MINUTE = 60L
    const val HOUR = 3_600L
    const val DAY = 86_400L

    private val PATTERN = Regex("""^(-)?(\d{1,3})/(\d{1,2}):(\d{2})(?::(\d{2}))?$""")

    /** Manager format: seconds only appear when they are not zero. */
    fun format(seconds: Long): String = format(seconds, alwaysSeconds = false)

    /** For the HUD clock, which always shows seconds: DD/HH:MM:SS. */
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

    /** Accepts D/HH:MM and D/HH:MM:SS with an optional sign; null if the text is not a valid MET. */
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
 * Flight day a MET belongs to: the one satisfying `startMet <= met < endMet`.
 * The last day includes its `endMet` (splashdown falls right there), like the Manager's `flightDayOf`.
 */
fun List<FlightDay>.dayAt(met: Long): FlightDay? {
    val last = lastOrNull()
    return firstOrNull { d -> met >= d.startMet && (met < d.endMet || (d === last && met == d.endMet)) }
}

/** Where the mission is at a given MET. */
sealed interface MissionPhase {
    data object BeforeLaunch : MissionPhase
    data class InFlight(val day: FlightDay) : MissionPhase
    data object AfterEnd : MissionPhase
    /** The MET falls in a gap between flight days (should not happen with a consistent package). */
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

/** End of the mission according to the package (MET of the last instant of the last flight day). */
val MissionPackage.endMet: Long get() = days.last().endMet
