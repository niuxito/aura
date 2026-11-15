package com.aura.astronaut.heart

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.random.Random

/*
 * Frecuencia cardiaca del reloj (AGENTS.md, «Frecuencia cardiaca desde el reloj»).
 * Fuente real: perfil estándar Heart Rate de Bluetooth LE (Garmin con «Transmitir FC»).
 * Fuente simulada: para enseñar la demo sin reloj; sus datos se guardan y muestran como simulados.
 */

/** De dónde sale la frecuencia cardiaca. */
enum class HeartSource { WATCH, SIMULATED, OFF }

/** Estado de la conexión con el reloj, para explicarlo en el HUD. */
enum class WatchStatus {
    OFF,
    /** El dispositivo no tiene Bluetooth LE. */
    UNAVAILABLE,
    NO_PERMISSION,
    BLUETOOTH_OFF,
    /** No hay reloj elegido: hay que buscarlo. */
    NOT_PAIRED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    /** Se ha perdido la conexión; se reintenta sola. */
    DISCONNECTED,
}

data class HeartState(
    val source: HeartSource = HeartSource.OFF,
    val status: WatchStatus = WatchStatus.OFF,
    val bpm: Int? = null,
    /** Instante (ms) del último dato recibido. */
    val receivedAtMs: Long? = null,
    val deviceName: String? = null,
) {
    /** El dato vale si es reciente; si no, el HUD muestra gris «sin datos del reloj». */
    fun fresh(nowMs: Long): Boolean = bpm != null && receivedAtMs != null && nowMs - receivedAtMs < HeartRules.STALE_MS
}

/** Un reloj encontrado al buscar. */
data class FoundWatch(val address: String, val name: String, val rssi: Int)

/** Referencias, umbrales y frecuencias de la demo. No son clínicos. */
object HeartRules {
    /** Un dato con más antigüedad que esto ya no se muestra como actual. */
    const val STALE_MS = 6_000L
    /** Se guarda una muestra agregada (media) cada este intervalo, no cada notificación. */
    const val SAMPLE_INTERVAL_MS = 30_000L
    /** Tiempo sin movimiento (pasos, sentadillas) para considerar que la FC es de reposo. */
    const val REST_WINDOW_MS = 90_000L
    /**
     * FC en reposo de referencia si el paquete no trae la del tripulante (`crew[].restingHrBpm`):
     * valor de DEMOSTRACIÓN. En una misión vendría de su línea base preflight.
     */
    const val DEMO_RESTING_BASELINE_BPM = 62f
    /** Indicador de las muestras de FC guardadas (no es una señal del paquete: no entra en el semáforo). */
    const val HEART_RATE_ID = "heart_rate"
    /** Señal del paquete que se calcula con el reloj en reposo. */
    const val RESTING_DELTA_SIGNAL_ID = "resting_hr_delta"
    /** Rango plausible: fuera de él se descarta la lectura (contacto malo, ruido). */
    val PLAUSIBLE = 30..220
    /** Reintento de conexión tras perderla. */
    const val RECONNECT_DELAY_MS = 3_000L
    /** Búsqueda: se detiene sola para no gastar batería. */
    const val SCAN_TIMEOUT_MS = 20_000L
}

/**
 * Lectura de la característica Heart Rate Measurement (0x2A37):
 * flags; FC en uint8 o uint16 (bit 0); contacto (bits 1–2); energía (bit 3, se salta);
 * intervalos RR en 1/1024 s (bit 4, opcionales).
 */
object HeartRateParser {
    data class Measurement(val bpm: Int, val rrMs: List<Int>, val contact: Boolean?)

    fun parse(b: ByteArray): Measurement? {
        if (b.size < 2) return null
        val flags = b[0].toInt() and 0xFF
        var i = 1
        val bpm = if (flags and 0x01 != 0) {
            if (b.size < 3) return null
            ((b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)).also { i = 3 }
        } else {
            (b[1].toInt() and 0xFF).also { i = 2 }
        }
        val contact = if (flags and 0x04 != 0) flags and 0x02 != 0 else null
        if (flags and 0x08 != 0) i += 2
        val rr = mutableListOf<Int>()
        if (flags and 0x10 != 0) {
            while (i + 1 < b.size) {
                val v = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
                rr += Math.round(v * 1000f / 1024f)
                i += 2
            }
        }
        return Measurement(bpm, rr, contact)
    }
}

/**
 * Frecuencia cardiaca SIMULADA: sube al caminar o agacharse y baja en reposo, con algo de variación.
 * La actividad la marca la silueta (movimiento simulado con la tablet).
 */
class SimulatedHeartRate(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(HeartState(source = HeartSource.SIMULATED, status = WatchStatus.CONNECTED))
    val state: StateFlow<HeartState> = _state.asStateFlow()

    @Volatile private var target = RESTING
    private var job: Job? = null

    fun setActivity(walking: Boolean, squatting: Boolean) {
        target = when {
            squatting -> SQUAT
            walking -> WALK
            else -> RESTING
        }
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var bpm = RESTING
            while (isActive) {
                bpm += (target - bpm) * (1f - exp(-1f / TAU_S)) + Random.nextFloat() * 2f - 1f
                _state.value = HeartState(
                    source = HeartSource.SIMULATED,
                    status = WatchStatus.CONNECTED,
                    bpm = Math.round(bpm),
                    receivedAtMs = System.currentTimeMillis(),
                    deviceName = null,
                )
                delay(1_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = HeartState(source = HeartSource.SIMULATED, status = WatchStatus.OFF)
    }

    private companion object {
        const val RESTING = 66f
        const val WALK = 96f
        const val SQUAT = 104f
        /** Constante de tiempo (s) de la subida o bajada de la FC. */
        const val TAU_S = 8f
    }
}
