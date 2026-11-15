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
 * Heart rate from the watch (AGENTS.md, "Heart rate from the watch").
 * Real source: standard Bluetooth LE Heart Rate profile (Garmin with "Broadcast Heart Rate").
 * Simulated source: to show the demo without a watch; its data is stored and shown as simulated.
 */

/** Where the heart rate comes from. */
enum class HeartSource { WATCH, SIMULATED, OFF }

/** State of the connection with the watch, to explain it in the HUD. */
enum class WatchStatus {
    OFF,
    /** The device has no Bluetooth LE. */
    UNAVAILABLE,
    NO_PERMISSION,
    BLUETOOTH_OFF,
    /** No watch chosen: it has to be searched for. */
    NOT_PAIRED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    /** The connection was lost; it retries on its own. */
    DISCONNECTED,
}

data class HeartState(
    val source: HeartSource = HeartSource.OFF,
    val status: WatchStatus = WatchStatus.OFF,
    val bpm: Int? = null,
    /** Instant (ms) of the last data point received. */
    val receivedAtMs: Long? = null,
    val deviceName: String? = null,
) {
    /** The data point is valid if recent; otherwise the HUD shows gray "no watch data". */
    fun fresh(nowMs: Long): Boolean = bpm != null && receivedAtMs != null && nowMs - receivedAtMs < HeartRules.STALE_MS
}

/** A watch found while searching. */
data class FoundWatch(val address: String, val name: String, val rssi: Int)

/** Demo baselines, thresholds and rates. They are not clinical. */
object HeartRules {
    /** A data point older than this is no longer shown as current. */
    const val STALE_MS = 6_000L
    /** An aggregated sample (mean) is stored every interval, not every notification. */
    const val SAMPLE_INTERVAL_MS = 30_000L
    /** Time without movement (steps, squats) to consider the HR a resting one. */
    const val REST_WINDOW_MS = 90_000L
    /**
     * Baseline resting HR if the package does not carry the crew member's (`crew[].restingHrBpm`):
     * DEMONSTRATION value. In a mission it would come from their preflight baseline.
     */
    const val DEMO_RESTING_BASELINE_BPM = 62f
    /** Indicator of the stored HR samples (not a package signal: it does not enter the traffic light). */
    const val HEART_RATE_ID = "heart_rate"
    /** Package signal computed from the watch at rest. */
    const val RESTING_DELTA_SIGNAL_ID = "resting_hr_delta"
    /** Plausible range: readings outside it are discarded (poor contact, noise). */
    val PLAUSIBLE = 30..220
    /** Reconnection retry after losing the connection. */
    const val RECONNECT_DELAY_MS = 3_000L
    /** Scanning: stops on its own to save battery. */
    const val SCAN_TIMEOUT_MS = 20_000L
}

/**
 * Parsing of the Heart Rate Measurement characteristic (0x2A37):
 * flags; HR as uint8 or uint16 (bit 0); contact (bits 1–2); energy (bit 3, skipped);
 * RR intervals in 1/1024 s (bit 4, optional).
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
 * SIMULATED heart rate: rises when walking or squatting and drops at rest, with some variation.
 * Activity is set by the silhouette (movement simulated with the tablet).
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
        /** Time constant (s) of the HR rise or fall. */
        const val TAU_S = 8f
    }
}
