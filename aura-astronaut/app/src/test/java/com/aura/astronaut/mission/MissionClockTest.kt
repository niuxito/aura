package com.aura.astronaut.mission

import org.junit.Assert.assertEquals
import org.junit.Test

class MissionClockTest {

    private val t0 = 1_800_000_000_000L

    @Test
    fun `the manual MET runs in real time`() {
        val s = ClockSettings(anchorMet = 1_000, anchorWallMs = t0, rate = 1, running = true)
        assertEquals(1_000L, MissionClock.metAt(s, t0, null))
        assertEquals(1_010L, MissionClock.metAt(s, t0 + 10_000, null))
    }

    @Test
    fun `the accelerated MET advances according to the rate`() {
        val s = ClockSettings(anchorMet = 0, anchorWallMs = t0, rate = 3_600, running = true)
        assertEquals(3_600L, MissionClock.metAt(s, t0 + 1_000, null))
    }

    @Test
    fun `when paused the MET does not advance`() {
        val s = ClockSettings(anchorMet = 500, anchorWallMs = t0, rate = 60, running = false)
        assertEquals(500L, MissionClock.metAt(s, t0 + 60_000, null))
    }

    @Test
    fun `changing the rate does not cause jumps`() {
        val s = ClockSettings(anchorMet = 0, anchorWallMs = t0, rate = 60, running = true)
        val now = t0 + 10_000 // MET 600
        val faster = MissionClock.setRate(s, 3_600, now, null)
        assertEquals(600L, MissionClock.metAt(faster, now, null))
        assertEquals(600L + 3_600L, MissionClock.metAt(faster, now + 1_000, null))
    }

    @Test
    fun `pausing and resuming keeps the MET`() {
        val s = ClockSettings(anchorMet = 0, anchorWallMs = t0, rate = 1, running = true)
        val paused = MissionClock.setRunning(s, false, t0 + 5_000, null)
        assertEquals(5L, MissionClock.metAt(paused, t0 + 50_000, null))
        val resumed = MissionClock.setRunning(paused, true, t0 + 50_000, null)
        assertEquals(7L, MissionClock.metAt(resumed, t0 + 52_000, null))
    }

    @Test
    fun `setting the MET switches to manual mode`() {
        val s = ClockSettings(source = ClockSource.LAUNCH_UTC)
        val set = MissionClock.setMet(s, 90_522, t0)
        assertEquals(ClockSource.MANUAL, set.source)
        assertEquals(90_522L, MissionClock.metAt(set, t0, t0))
    }

    @Test
    fun `with launchUtc the MET comes from the clock`() {
        val launch = MissionClock.launchUtcMs("2026-04-01T22:35:00Z")!!
        val s = ClockSettings(source = ClockSource.LAUNCH_UTC)
        assertEquals(90_522L, MissionClock.metAt(s, launch + 90_522_000, launch))
        // Before launch the MET is negative.
        assertEquals(-60L, MissionClock.metAt(s, launch - 60_000, launch))
    }

    @Test
    fun `without launchUtc the manual MET is used`() {
        val s = ClockSettings(source = ClockSource.LAUNCH_UTC, anchorMet = 42, anchorWallMs = t0, running = false)
        assertEquals(42L, MissionClock.metAt(s, t0 + 1_000, null))
        assertEquals(null, MissionClock.launchUtcMs(null))
        assertEquals(null, MissionClock.launchUtcMs("not a date"))
    }
}
