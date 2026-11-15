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
 * Actividad muscular SIMULADA para la silueta del HUD (AGENTS.md, «Silueta y actividad simulada»).
 * Imita lo que registraría la ropa inteligente usando el acelerómetro de la tablet. Es solo visual:
 * no se guarda como observación, no alimenta el semáforo y no dice nada de la salud del astronauta.
 */

/** De dónde sale el movimiento de la silueta. */
enum class MotionSource { SENSOR, WALK, SQUAT, STILL }

/** Movimiento detectado: caminando (con su cadencia) o agachado. */
data class MotionInput(
    val walking: Boolean = false,
    /** Pasos por segundo. */
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

/** Umbrales de demostración para detectar pasos y sentadillas con la tablet en la mano. No son clínicos. */
object MotionRules {
    /** Pico de aceleración vertical (m/s²) que cuenta como paso: el balanceo de caminar con la tablet. */
    const val STEP_PEAK = 1.1f
    /** Separación mínima entre pasos (s): por debajo sería ruido o un golpe. */
    const val STEP_MIN_INTERVAL = 0.28f
    /** Se considera que camina si ha habido al menos 2 pasos y el último hace menos de esto (s). */
    const val WALK_TIMEOUT = 1.3f
    const val DEFAULT_CADENCE = 1.8f
    /** Bajada (m) de la tablet que cuenta como agacharse y subida que cuenta como levantarse. */
    const val SQUAT_DROP = 0.22f
    const val SQUAT_RISE = 0.16f
    /** Bajada (m) a partir de la cual empieza a contar un descenso (para medir su duración e inclinación). */
    const val DESCENT_START = 0.05f
    /**
     * Tiempo mínimo (s) entre empezar a bajar ([DESCENT_START]) y llegar a [SQUAT_DROP]: agacharse lleva
     * su tiempo (unos 0,3 s en ese tramo); un tirón del brazo o un golpe, menos de 0,1 s.
     */
    const val SQUAT_MIN_DESCENT = 0.2f
    /**
     * Giro máximo de la tablet (grados) durante el descenso. Al agacharse con la tablet delante se mantiene
     * su orientación; al bajarla con los brazos o dejarla en la mesa, se inclina.
     */
    const val SQUAT_MAX_TILT_DEG = 25f
    /**
     * Giro medio máximo de la tablet durante el descenso (grados/s). Agacharse con la tablet delante apenas
     * la gira; bajarla con los brazos la gira decenas de grados por segundo.
     */
    const val SQUAT_MAX_TILT_RATE_DEG_S = 20f
    /** Giro rápido de la tablet (grados/s): se descarta lo integrado hasta ahora, porque no es un movimiento del cuerpo. */
    const val TILT_RATE_RESET_DEG_S = 60f
    /** Tras un paso, no se buscan sentadillas durante este tiempo (s): el balanceo de caminar deriva la integración. */
    const val STEP_GUARD = 1.0f
    /** Si sigue agachado más de esto (s), se da por levantado (la integración deriva). */
    const val SQUAT_TIMEOUT = 15f
    /** Constantes de fuga de la integración (s): evitan que la deriva del sensor acumule desplazamiento. */
    const val VELOCITY_LEAK = 1.2f
    const val DISPLACEMENT_LEAK = 3f
}

/**
 * Detección de pasos y sentadillas a partir de la gravedad y la aceleración lineal del dispositivo.
 * Sin dependencias de Android, para poder probarla con señales sintéticas.
 */
class MotionDetector {
    private val gravity = floatArrayOf(0f, 0f, 9.81f)
    private var lastGravityT = -1f
    private var t = -1f
    // Pasos
    private var above = false
    private val steps = ArrayDeque<Float>()
    // Sentadilla
    private var velocity = 0f
    private var displacement = 0f
    private var descentStart: Float? = null
    private var descentGravity: FloatArray? = null
    private var squatting = false
    private var squatSince = 0f
    private var lowest = 0f

    var state = MotionInput()
        private set

    /** Vector de gravedad del sensor, en m/s². [time] en segundos. */
    fun onGravity(time: Float, x: Float, y: Float, z: Float) {
        val previous = gravity.copyOf()
        gravity[0] = x; gravity[1] = y; gravity[2] = z
        val dt = time - lastGravityT
        if (lastGravityT >= 0f && dt in 0.001f..0.5f) {
            // Giro rápido de la tablet: no es el cuerpo bajando, sino la tablet girando en la mano.
            if (angleDeg(previous, gravity) / dt > MotionRules.TILT_RATE_RESET_DEG_S && !squatting) resetIntegration()
        }
        lastGravityT = time
    }

    /** Aceleración lineal (sin gravedad), en m/s². [time] en segundos. Devuelve el estado actualizado. */
    fun onLinear(time: Float, x: Float, y: Float, z: Float): MotionInput {
        val dt = if (t < 0f) 0f else (time - t).coerceIn(0f, 0.1f)
        t = time
        val g = norm(gravity).takeIf { it > 1f } ?: return state
        // Aceleración vertical: positiva hacia arriba (la gravedad del sensor apunta hacia arriba en reposo).
        val vertical = (x * gravity[0] + y * gravity[1] + z * gravity[2]) / g

        // Pasos: cada cruce por encima del pico, separado del anterior.
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

        // Sentadilla: desplazamiento vertical con integración con fuga.
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
            // Se decide una sola vez por descenso, al cruzar el umbral: o es una sentadilla o se descarta lo integrado.
            if (start != null && displacement < -MotionRules.SQUAT_DROP) {
                val afterStep = steps.isNotEmpty() && t - steps.last() < MotionRules.STEP_GUARD
                // Si la tablet se ha inclinado al bajar, se ha movido con los brazos, no con el cuerpo.
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
 * Lee aceleración lineal y gravedad del dispositivo y se las pasa a [MotionDetector].
 * Registrar solo mientras la silueta está en pantalla.
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
