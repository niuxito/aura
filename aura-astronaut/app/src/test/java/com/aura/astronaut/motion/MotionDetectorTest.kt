package com.aura.astronaut.motion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic 50 Hz signals: the tablet upright (gravity along +y) and body or arm movements.
 * Vertical acceleration is the second derivative of a smooth displacement d(t) = −A·(1 − cos(π·t/T)) / 2.
 */
class MotionDetectorTest {

    private val dt = 0.02f
    private val g = 9.81f

    private class Run(val detector: MotionDetector = MotionDetector(), var t: Float = 0f) {
        var squatSeen = false
        var walkSeen = false
    }

    /** Advances [seconds] with vertical acceleration [accel](τ) and gravity tilted [tiltDeg](τ) degrees toward z. */
    private fun Run.feed(seconds: Float, accel: (Float) -> Float = { 0f }, tiltDeg: (Float) -> Float = { 0f }) {
        var tau = 0f
        while (tau < seconds) {
            val tilt = Math.toRadians(tiltDeg(tau).toDouble())
            val gy = (g * cos(tilt)).toFloat()
            val gz = (g * sin(tilt)).toFloat()
            detector.onGravity(t, 0f, gy, gz)
            val a = accel(tau)
            // Linear acceleration along the vertical (direction of gravity).
            val s = detector.onLinear(t, 0f, a * gy / g, a * gz / g)
            squatSeen = squatSeen || s.squatting
            walkSeen = walkSeen || s.walking
            t += dt
            tau += dt
        }
    }

    /** Acceleration of a smooth drop of [amplitude] m over [duration] s. */
    private fun descent(amplitude: Float, duration: Float): (Float) -> Float = { tau ->
        val w = (PI / duration).toFloat()
        if (tau > duration) 0f else (-amplitude / 2f * w * w * cos(w * tau))
    }

    @Test
    fun `squatting with the tablet in front is a squat`() {
        val r = Run()
        r.feed(1f)
        r.feed(1.2f, descent(0.45f, 1.0f))
        r.feed(1f)
        assertTrue(r.squatSeen)
    }

    @Test
    fun `squatting with a slight natural tablet rotation is still a squat`() {
        val r = Run()
        r.feed(1f)
        r.feed(1.2f, descent(0.45f, 1.2f)) { tau -> minOf(6f, 6f * tau / 1.2f) }
        r.feed(1f, tiltDeg = { 6f })
        assertTrue(r.squatSeen)
    }

    @Test
    fun `lowering the tablet while tilting it is not a squat`() {
        val r = Run()
        r.feed(1f)
        // Same drop, but the tablet rotates 45° (from upright to tilted) while going down.
        r.feed(1.2f, descent(0.45f, 1.0f)) { tau -> minOf(45f, 45f * tau / 1.0f) }
        r.feed(1f, tiltDeg = { 45f })
        assertFalse(r.squatSeen)
    }

    @Test
    fun `a sudden jerk is not a squat`() {
        val r = Run()
        r.feed(1f)
        r.feed(0.5f, descent(0.45f, 0.18f))
        r.feed(1.5f)
        assertFalse(r.squatSeen)
    }

    @Test
    fun `walking is not mistaken for squatting`() {
        val r = Run()
        r.feed(1f)
        // Vertical sway of ±2 m/s² at 1.8 steps/s for 6 s.
        r.feed(6f, accel = { tau -> 2f * sin(2f * PI.toFloat() * 1.8f * tau) })
        assertTrue(r.walkSeen)
        assertFalse(r.squatSeen)
    }

    @Test
    fun `at rest there is no movement`() {
        val r = Run()
        r.feed(5f)
        assertFalse(r.walkSeen)
        assertFalse(r.squatSeen)
    }
}
