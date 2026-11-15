package com.aura.astronaut.motion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Señales sintéticas a 50 Hz: la tablet en vertical (gravedad en +y) y movimientos del cuerpo o de los brazos.
 * La aceleración vertical es la segunda derivada de un desplazamiento suave d(t) = −A·(1 − cos(π·t/T)) / 2.
 */
class MotionDetectorTest {

    private val dt = 0.02f
    private val g = 9.81f

    private class Run(val detector: MotionDetector = MotionDetector(), var t: Float = 0f) {
        var squatSeen = false
        var walkSeen = false
    }

    /** Avanza [seconds] con aceleración vertical [accel](τ) y gravedad girada [tiltDeg](τ) grados hacia z. */
    private fun Run.feed(seconds: Float, accel: (Float) -> Float = { 0f }, tiltDeg: (Float) -> Float = { 0f }) {
        var tau = 0f
        while (tau < seconds) {
            val tilt = Math.toRadians(tiltDeg(tau).toDouble())
            val gy = (g * cos(tilt)).toFloat()
            val gz = (g * sin(tilt)).toFloat()
            detector.onGravity(t, 0f, gy, gz)
            val a = accel(tau)
            // Aceleración lineal a lo largo de la vertical (dirección de la gravedad).
            val s = detector.onLinear(t, 0f, a * gy / g, a * gz / g)
            squatSeen = squatSeen || s.squatting
            walkSeen = walkSeen || s.walking
            t += dt
            tau += dt
        }
    }

    /** Aceleración de una bajada suave de [amplitude] m en [duration] s. */
    private fun descent(amplitude: Float, duration: Float): (Float) -> Float = { tau ->
        val w = (PI / duration).toFloat()
        if (tau > duration) 0f else (-amplitude / 2f * w * w * cos(w * tau))
    }

    @Test
    fun `agacharse con la tablet delante es una sentadilla`() {
        val r = Run()
        r.feed(1f)
        r.feed(1.2f, descent(0.45f, 1.0f))
        r.feed(1f)
        assertTrue(r.squatSeen)
    }

    @Test
    fun `agacharse con un leve giro natural de la tablet sigue siendo sentadilla`() {
        val r = Run()
        r.feed(1f)
        r.feed(1.2f, descent(0.45f, 1.2f)) { tau -> minOf(6f, 6f * tau / 1.2f) }
        r.feed(1f, tiltDeg = { 6f })
        assertTrue(r.squatSeen)
    }

    @Test
    fun `bajar la tablet inclinandola no es una sentadilla`() {
        val r = Run()
        r.feed(1f)
        // Misma bajada, pero la tablet gira 45° (de vertical a inclinada) mientras baja.
        r.feed(1.2f, descent(0.45f, 1.0f)) { tau -> minOf(45f, 45f * tau / 1.0f) }
        r.feed(1f, tiltDeg = { 45f })
        assertFalse(r.squatSeen)
    }

    @Test
    fun `un tiron brusco no es una sentadilla`() {
        val r = Run()
        r.feed(1f)
        r.feed(0.5f, descent(0.45f, 0.18f))
        r.feed(1.5f)
        assertFalse(r.squatSeen)
    }

    @Test
    fun `caminar no se confunde con agacharse`() {
        val r = Run()
        r.feed(1f)
        // Balanceo vertical de ±2 m/s² a 1,8 pasos/s durante 6 s.
        r.feed(6f, accel = { tau -> 2f * sin(2f * PI.toFloat() * 1.8f * tau) })
        assertTrue(r.walkSeen)
        assertFalse(r.squatSeen)
    }

    @Test
    fun `en reposo no hay movimiento`() {
        val r = Run()
        r.feed(5f)
        assertFalse(r.walkSeen)
        assertFalse(r.squatSeen)
    }
}
