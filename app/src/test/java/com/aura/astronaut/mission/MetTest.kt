package com.aura.astronaut.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetTest {

    @Test
    fun `formatea igual que el Manager`() {
        assertEquals("00/00:00", Met.format(0))
        assertEquals("00/17:00", Met.format(61_200))
        assertEquals("01/01:08:42", Met.format(90_522))
        assertEquals("09/01:42:48", Met.format(783_768))
        assertEquals("-00/01:00", Met.format(-3_600))
    }

    @Test
    fun `el reloj siempre muestra segundos`() {
        assertEquals("00/17:00:00", Met.formatClock(61_200))
        assertEquals("09/01:42:48", Met.formatClock(783_768))
    }

    @Test
    fun `lee las etiquetas del cronograma`() {
        assertEquals(90_522L, Met.parse("01/01:08:42"))
        assertEquals(61_200L, Met.parse("00/17:00"))
        assertEquals(61_200L, Met.parse(" 0/17:00 "))
        assertEquals(-3_600L, Met.parse("-00/01:00"))
    }

    @Test
    fun `rechaza textos que no son MET`() {
        assertNull(Met.parse(""))
        assertNull(Met.parse("17:00"))
        assertNull(Met.parse("00/24:00"))
        assertNull(Met.parse("00/10:60"))
        assertNull(Met.parse("00/10:00:60"))
        assertNull(Met.parse("abc"))
    }

    @Test
    fun `ida y vuelta`() {
        listOf(0L, 59L, 3_600L, 90_522L, 783_768L).forEach {
            assertEquals(it, Met.parse(Met.format(it)))
        }
    }

    @Test
    fun `etiqueta del dia de vuelo`() {
        assertEquals("FD01", Met.formatFlightDay(1))
        assertEquals("FD10", Met.formatFlightDay(10))
    }
}
