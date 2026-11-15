package com.aura.astronaut.heart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `fc en uint8 sin mas campos`() {
        val m = HeartRateParser.parse(bytes(0x00, 72))!!
        assertEquals(72, m.bpm)
        assertEquals(emptyList<Int>(), m.rrMs)
        assertNull(m.contact)
    }

    @Test
    fun `fc en uint16`() {
        // 0x012C = 300 (no plausible, pero el formato se lee igual; el filtro está aparte).
        assertEquals(300, HeartRateParser.parse(bytes(0x01, 0x2C, 0x01))!!.bpm)
        assertEquals(185, HeartRateParser.parse(bytes(0x01, 185, 0))!!.bpm)
    }

    @Test
    fun `contacto con la piel`() {
        assertEquals(true, HeartRateParser.parse(bytes(0x06, 60))!!.contact)
        assertEquals(false, HeartRateParser.parse(bytes(0x04, 60))!!.contact)
    }

    @Test
    fun `intervalos RR en 1 entre 1024 s, saltando la energia`() {
        // flags: uint8 + energía (0x08) + RR (0x10). Energía 0x0102; RR 1024 → 1000 ms, 820 → 801 ms.
        val m = HeartRateParser.parse(bytes(0x18, 64, 0x02, 0x01, 0x00, 0x04, 0x34, 0x03))!!
        assertEquals(64, m.bpm)
        assertEquals(listOf(1000, 801), m.rrMs)
    }

    @Test
    fun `tramas incompletas`() {
        assertNull(HeartRateParser.parse(bytes()))
        assertNull(HeartRateParser.parse(bytes(0x00)))
        assertNull(HeartRateParser.parse(bytes(0x01, 70)))
    }

    @Test
    fun `dato caducado`() {
        val s = HeartState(source = HeartSource.WATCH, status = WatchStatus.CONNECTED, bpm = 70, receivedAtMs = 1_000)
        assertEquals(true, s.fresh(1_000 + HeartRules.STALE_MS - 1))
        assertEquals(false, s.fresh(1_000 + HeartRules.STALE_MS))
        assertEquals(false, HeartState().fresh(0))
    }
}
