package com.aura.astronaut.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.acos
import kotlin.math.sqrt

/*
 * SIMULATED muscle activity for the HUD silhouette (AGENTS.md, "Silhouette and simulated activity").
 * It imitates what smart clothing would record using the tablet's accelerometer. It is visual only:
 * it is not stored as an observation, does not feed the traffic light and says nothing about the astronaut's health.
 */

/** Where the silhouette's movement comes from. */
enum class MotionSource { SENSOR, WALK, SQUAT, STILL }

/** Detected movement: walking (with its cadence) or squatting. */
data class MotionInput(
    val walking: Boolean = false,
    /** Steps per second. */
    val cadence: Float = MotionRules.DEFAULT_CADENCE,
    val squatting: Boolean = false,
    val source: MotionSource = MotionSource.SENSOR,
) {
    companion object {
        fun forced(source: MotionSource) = when (source) {
            MotionSource.WALK -> MotionInput(walking = true, source = source)
            MotionSource.SQUAT -> MotionInput(squatting = true, source = source)
            else -> MotionInput(source = source)
        }
    }
}

/** Demonstration thresholds to detect steps and squats with the tablet in hand. They are not clinical. */
object MotionRules {
    /** Vertical acceleration peak (m/s²) that counts as a step: the sway of walking with the tablet. */
    const val STEP_PEAK = 1.1f
    /** Minimum spacing between steps (s): below it, it would be noise or a knock. */
    const val STEP_MIN_INTERVAL = 0.28f
    /** Walking is assumed if there have been at least 2 steps and the last one was less than this ago (s). */
    const val WALK_TIMEOUT = 1.3f
    const val DEFAULT_CADENCE = 1.8f
    /** Drop (m) of the tablet that counts as squatting, and rise that counts as standing up. */
    const val SQUAT_DROP = 0.22f
    const val SQUAT_RISE = 0.16f
    /** Drop (m) from which a descent starts being counted (to measure its duration and tilt). */
    const val DESCENT_START = 0.05f
    /**
     * Minimum time (s) between starting to go down ([DESCENT_START]) and reaching [SQUAT_DROP]: squatting takes
     * some time (about 0.3 s over that stretch); an arm jerk or a knock, less than 0.1 s.
     */
    const val SQUAT_MIN_DESCENT = 0.2f
    /**
     * Maximum rotation of the tablet (degrees) during the descent. When squatting with the tablet in front, its
     * orientation is kept; when lowering it with the arms or putting it on the table, it tilts.
     */
    const val SQUAT_MAX_TILT_DEG = 25f
    /**
     * Maximum mean rotation of the tablet during the descent (degrees/s). Squatting with the tablet in front barely
     * rotates it; lowering it with the arms rotates it by tens of degrees per second.
     */
    const val SQUAT_MAX_TILT_RATE_DEG_S = 20f
    /** Fast tablet rotation (degrees/s): what has been integrated so far is discarded, because it is not a body movement. */
    const val TILT_RATE_RESET_DEG_S = 60f
    /** After a step, squats are not looked for during this time (s): the walking sway makes the integration drift. */
    const val STEP_GUARD = 1.0f
    /** If still squatting after this long (s), it is assumed to have stood up (the integration drifts). */
    const val SQUAT_TIMEOUT = 15f
    /** Leak time constants of the integration (s): they prevent sensor drift from accumulating displacement. */
    const val VELOCITY_LEAK = 1.2f
    const val DISPLACEMENT_LEAK = 3f
}

/**
 * Step and squat detection from the device's gravity and linear acceleration.
 * No Android dependencies, so it can be tested with synthetic signals.
 */
class MotionDetector {
    private val gravity = floatArrayOf(0f, 0f, 9.81f)
    private var lastGravityT = -1f
    private var t = -1f
    // Steps
    private var above = false
    private val steps = ArrayDeque<Float>()
    // Squat
    private var velocity = 0f
    private var displacement = 0f
    private var descentStart: Float? = null
    private var descentGravity: FloatArray? = null
    private var squatting = false
    private var squatSince = 0f
    private var lowest = 0f

    var state = MotionInput()
        private set

    /** Sensor gravity vector, in m/s². [time] in seconds. */
    fun onGravity(time: Float, x: Float, y: Float, z: Float) {
        val previous = gravity.copyOf()
        gravity[0] = x; gravity[1] = y; gravity[2] = z
        val dt = time - lastGravityT
        if (lastGravityT >= 0f && dt in 0.001f..0.5f) {
            // Fast tablet rotation: it is not the body going down, but the tablet turning in the hand.
            if (angleDeg(previous, gravity) / dt > MotionRules.TILT_RATE_RESET_DEG_S && !squatting) resetIntegration()
        }
        lastGravityT = time
    }

    /** Linear acceleration (without gravity), in m/s². [time] in seconds. Returns the updated state. */
    fun onLinear(time: Float, x: Float, y: Float, z: Float): MotionInput {
        val dt = if (t < 0f) 0f else (time - t).coerceIn(0f, 0.1f)
        t = time
        val g = norm(gravity).takeIf { it > 1f } ?: return state
        // Vertical acceleration: positive upward (the sensor's gravity points upward at rest).
        val vertical = (x * gravity[0] + y * gravity[1] + z * gravity[2]) / g

        // Steps: each crossing above the peak, spaced from the previous one.
        if (!above && vertical > MotionRules.STEP_PEAK) {
            above = true
            if (steps.isEmpty() || t - steps.last() > MotionRules.STEP_MIN_INTERVAL) steps.addLast(t)
        } else if (above && vertical < 0f) {
            above = false
        }
        while (steps.isNotEmpty() && t - steps.first() > 3f) steps.removeFirst()
        val walking = steps.size >= 2 && t - steps.last() < MotionRules.WALK_TIMEOUT && !squatting
        val cadence = if (steps.size >= 2) {
            ((steps.size - 1) / (steps.last() - steps.first())).coerceIn(1f, 3f)
        } else {
            MotionRules.DEFAULT_CADENCE
        }

        // Squat: vertical displacement with leaky integration.
        if (dt > 0f) {
            velocity = (velocity + vertical * dt) * (1f - dt / MotionRules.VELOCITY_LEAK)
            displacement = (displacement + velocity * dt) * (1f - dt / MotionRules.DISPLACEMENT_LEAK)
        }
        if (!squatting) {
            if (descentStart == null && displacement < -MotionRules.DESCENT_START) {
                descentStart = t
                descentGravity = gravity.copyOf()
            } else if (descentStart != null && displacement > -MotionRules.DESCENT_START / 2) {
                descentStart = null
                descentGravity = null
            }
            val start = descentStart
            // Decided only once per descent, when crossing the threshold: either it is a squat or the integration is discarded.
            if (start != null && displacement < -MotionRules.SQUAT_DROP) {
                val afterStep = steps.isNotEmpty() && t - steps.last() < MotionRules.STEP_GUARD
                // If the tablet tilted while going down, it was moved with the arms, not with the body.
                val elapsed = t - start
                val tilt = descentGravity?.let { angleDeg(it, gravity) } ?: Float.MAX_VALUE
                val steady = tilt <= MotionRules.SQUAT_MAX_TILT_DEG && tilt / elapsed.coerceAtLeast(dt) <= MotionRules.SQUAT_MAX_TILT_RATE_DEG_S
                val gradual = elapsed >= MotionRules.SQUAT_MIN_DESCENT
                if (steady && gradual && !walking && !afterStep) {
                    squatting = true
                    squatSince = t
                    lowest = displacement
                } else {
                    resetIntegration()
                }
            }
        } else {
            lowest = minOf(lowest, displacement)
            val rose = displacement - lowest > MotionRules.SQUAT_RISE
            if (rose || t - squatSince > MotionRules.SQUAT_TIMEOUT) {
                squatting = false
                resetIntegration()
            }
        }
        state = MotionInput(walking = walking, cadence = cadence, squatting = squatting)
        return state
    }

    private fun resetIntegration() {
        velocity = 0f
        displacement = 0f
        descentStart = null
        descentGravity = null
    }

    private companion object {
        fun norm(v: FloatArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

        fun angleDeg(a: FloatArray, b: FloatArray): Float {
            val na = norm(a)
            val nb = norm(b)
            if (na < 1e-3f || nb < 1e-3f) return 0f
            val cos = ((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb)).coerceIn(-1f, 1f)
            return Math.toDegrees(acos(cos).toDouble()).toFloat()
        }
    }
}

/**
 * Reads the device's linear acceleration and gravity and passes them to [MotionDetector].
 * Register only while the silhouette is on screen.
 */
class BodyMotionSensor(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val linear = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gravitySensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

    val available: Boolean get() = linear != null && gravitySensor != null

    private val _input = MutableStateFlow(MotionInput())
    val input: StateFlow<MotionInput> = _input.asStateFlow()

    private var detector = MotionDetector()

    fun start() {
        if (!available) return
        detector = MotionDetector()
        manager?.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME)
        manager?.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager?.unregisterListener(this)
        _input.value = MotionInput()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(e: SensorEvent) {
        val time = e.timestamp / 1e9f
        if (e.sensor.type == Sensor.TYPE_GRAVITY) {
            detector.onGravity(time, e.values[0], e.values[1], e.values[2])
            return
        }
        val next = detector.onLinear(time, e.values[0], e.values[1], e.values[2])
        if (next != _input.value) _input.value = next
    }
}
