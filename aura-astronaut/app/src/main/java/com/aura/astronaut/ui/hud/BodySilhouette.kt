package com.aura.astronaut.ui.hud

import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.astronaut.heart.HeartSource
import com.aura.astronaut.heart.HeartState
import com.aura.astronaut.heart.WatchStatus
import com.aura.astronaut.motion.MotionInput
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D human silhouette that rotates and reproduces the detected movement (walking, squatting),
 * lighting up the muscle groups at work, as smart clothing would show it.
 * SIMULATED: the movement comes from the tablet's accelerometer or from the demo mode buttons;
 * it does not represent health status and is not stored. (AGENTS.md, "Silhouette and simulated activity".)
 *
 * Mesh: BodyParts3D skin (DBCLS, CC BY-SA) via anatomia.alexisparron.com (assets/models/LICENSE-body.txt).
 */
@Composable
fun BodySilhouette(
    motion: MotionInput,
    heart: HeartState,
    onHeartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rig by produceState<BodyRig?>(null) { value = withContext(Dispatchers.IO) { BodyMesh.load(context)?.let(::BodyRig) } }
    BoxWithConstraints(modifier) {
        val r = rig
        if (maxHeight < MIN_HEIGHT || r == null) return@BoxWithConstraints
        val anim = remember { SilhouetteAnimator() }
        val currentMotion by rememberUpdatedState(motion)
        val currentHeart by rememberUpdatedState(heart)
        var frame by remember { mutableLongStateOf(0L) }
        LaunchedEffect(anim) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                    last = now
                    anim.step(currentMotion, dt)
                    val h = currentHeart
                    anim.heartbeat(if (h.fresh(System.currentTimeMillis())) h.bpm else null, dt)
                    frame = now
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            if (frame >= 0L) drawHologram(r, anim) // reading `frame` makes it redraw on every frame
        }
        HeartLabel(heart, frame, onHeartClick, Modifier.align(Alignment.TopEnd))
        val label = if (frame >= 0L) anim.label() else null
        if (label != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                Text(
                    str(R.string.sim_label, label),
                    style = HudText.Label.copy(fontSize = 12.sp, letterSpacing = 0.3.sp),
                    color = HudColors.Amber,
                    maxLines = 1,
                )
            }
        }
    }
}

/** HR value, source and age of the data point. With no recent data: gray, "no watch data". */
@Composable
private fun HeartLabel(heart: HeartState, frame: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = if (frame >= 0L) System.currentTimeMillis() else 0L
    val fresh = heart.fresh(now)
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            // U+FE0E: the heart as a text symbol in our color, not as a red emoji (red belongs to the traffic light).
            if (fresh) str(R.string.heart_bpm, heart.bpm ?: 0) else "\u2665\uFE0E —",
            style = HudText.Title.copy(fontSize = 24.sp),
            color = if (fresh) HEART_COLOR else HudColors.Gray,
        )
        val age = heart.receivedAtMs?.let { ((now - it) / 1000).coerceAtLeast(0) }
        val text = when {
            heart.source == HeartSource.OFF -> str(R.string.heart_off)
            !fresh && heart.source == HeartSource.WATCH -> str(R.string.heart_no_watch_data, watchStatusText(heart.status))
            !fresh -> str(R.string.heart_no_data)
            heart.source == HeartSource.SIMULATED -> str(R.string.heart_simulated)
            else -> str(R.string.heart_watch_age, (age ?: 0).toInt())
        }
        Text(
            text,
            style = HudText.Label.copy(fontSize = 12.sp, letterSpacing = 0.3.sp),
            color = if (heart.source == HeartSource.SIMULATED && fresh) HudColors.Amber else HudColors.TextDim,
            maxLines = 1,
        )
    }
}

fun watchStatusText(s: WatchStatus): String = when (s) {
    WatchStatus.OFF -> str(R.string.watch_off)
    WatchStatus.UNAVAILABLE -> str(R.string.watch_unavailable)
    WatchStatus.NO_PERMISSION -> str(R.string.watch_no_permission)
    WatchStatus.BLUETOOTH_OFF -> str(R.string.watch_bt_off)
    WatchStatus.NOT_PAIRED -> str(R.string.watch_not_paired)
    WatchStatus.SCANNING -> str(R.string.watch_scanning)
    WatchStatus.CONNECTING -> str(R.string.watch_connecting)
    WatchStatus.CONNECTED -> str(R.string.watch_connected)
    WatchStatus.DISCONNECTED -> str(R.string.watch_disconnected)
}

private val MIN_HEIGHT = 160.dp
/** Maximum body height (before SIZE_FACTOR): the one it has with the transition notice visible on the tablet. */
private val MAX_BODY_HEIGHT = 440.dp
/** Color of the heart and its aura: warm and soft, distinct from the traffic-light colors (red and amber). */
private val HEART_COLOR = Color(0xFFFF9EC4)
/** Size relative to the available space and horizontal position of the axis (0 = left, 1 = right). */
private const val SIZE_FACTOR = 0.9f
private const val CENTER_X = 0.62f
private const val ROTATION_PERIOD_S = 16f
private const val CAMERA_DISTANCE = 4f
private val INNER_ALPHAS = floatArrayOf(0f, 0.025f, 0.06f, 0.12f)
/** Muscle activity color: lighter than the HUD cyan, distinct from the traffic-light colors. */
private val ACTIVE_COLOR = Color(0xFFCFF8FF)

private fun rad(deg: Float) = deg * PI.toFloat() / 180f

// ---------------------------------------------------------------- Mesh

/** Normalized mesh: height 1, feet at y = 0, centered in x/z. */
class BodyMesh(val xyz: FloatArray, val tri: IntArray, val edges: IntArray) {
    val vertexCount get() = xyz.size / 3
    val triCount get() = tri.size / 3

    companion object {
        fun load(context: Context): BodyMesh? = runCatching {
            val bytes = context.assets.open("models/body.bin").use { it.readBytes() }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val nv = buf.int
            val nt = buf.int
            val xyz = FloatArray(nv * 3) { buf.float }
            val tri = IntArray(nt * 3) { buf.short.toInt() and 0xFFFF }
            // Edges with their two neighboring faces: a, b, face 1, face 2 (−1 if it is a border).
            val faces = HashMap<Long, IntArray>(nt * 2)
            for (t in 0 until nt) for (k in 0 until 3) {
                val a = tri[t * 3 + k]
                val b = tri[t * 3 + (k + 1) % 3]
                val key = min(a, b).toLong() shl 32 or max(a, b).toLong()
                val f = faces.getOrPut(key) { intArrayOf(min(a, b), max(a, b), t, -1) }
                if (f[2] != t && f[3] == -1) f[3] = t
            }
            val edges = IntArray(faces.size * 4)
            var i = 0
            for (f in faces.values) { f.copyInto(edges, i); i += 4 }
            BodyMesh(xyz, tri, edges)
        }.getOrNull()
    }
}

// ---------------------------------------------------------------- Approximate skeleton

/** Muscle groups that light up. */
private object Muscle {
    const val NONE: Byte = 0
    const val QUADS: Byte = 1
    const val GLUTES_HAMSTRINGS: Byte = 2
    const val CALVES: Byte = 3
    const val ABDOMEN: Byte = 4
}

/**
 * Assigns each vertex to a leg (left/right), trunk or arm according to its position in the rest mesh
 * (human proportions normalized to height 1) and computes hip and knee pivots.
 */
class BodyRig(val mesh: BodyMesh) {
    private val n = mesh.vertexCount
    private val rest = mesh.xyz
    /** −1 left, +1 right (by the sign of x); only counts for the legs. */
    val side = IntArray(n)
    val wHip = FloatArray(n)
    val wKnee = FloatArray(n)
    val wTorso = FloatArray(n)
    val muscle = ByteArray(n)
    /** Direction of "front" in z: where the toes point. */
    val front: Float
    val hipZ: Float
    val kneeZ: Float
    /** Heart position at rest: chest, slightly left of the sternum. */
    val heartRest: FloatArray

    init {
        fun meanZ(filter: (Int) -> Boolean): Float {
            var s = 0f; var c = 0
            for (i in 0 until n) if (filter(i)) { s += rest[i * 3 + 2]; c++ }
            return if (c > 0) s / c else 0f
        }
        val y = { i: Int -> rest[i * 3 + 1] }
        val x = { i: Int -> rest[i * 3] }
        val ankle = meanZ { y(it) in 0.05f..0.08f }
        val foot = meanZ { y(it) < 0.025f }
        front = if (foot >= ankle) 1f else -1f
        hipZ = meanZ { y(it) in HIP_Y - 0.03f..HIP_Y + 0.01f && abs(x(it)) < ARM_X }
        kneeZ = meanZ { y(it) in KNEE_Y - 0.015f..KNEE_Y + 0.015f }
        // Facing +z with y up, the body's left is +x: the left side is `front`.
        val chestZ = meanZ { y(it) in HEART_Y - 0.02f..HEART_Y + 0.02f && abs(x(it)) < 0.1f }
        heartRest = floatArrayOf(0.014f * front, HEART_Y, chestZ + 0.012f * front)

        // Z center of each leg by height bands, to know which is the front face of the thigh.
        val bins = 40
        val sum = Array(2) { FloatArray(bins) }
        val cnt = Array(2) { IntArray(bins) }
        for (i in 0 until n) {
            val yi = y(i)
            if (yi < LEG_TOP && abs(x(i)) < ARM_X) {
                val s = if (x(i) < 0) 0 else 1
                val b = (yi / LEG_TOP * (bins - 1)).toInt().coerceIn(0, bins - 1)
                sum[s][b] += rest[i * 3 + 2]; cnt[s][b]++
            }
        }
        for (i in 0 until n) {
            val xi = x(i); val yi = y(i); val zi = rest[i * 3 + 2]
            val isArm = abs(xi) > ARM_X && yi > 0.36f
            side[i] = if (xi < 0) -1 else 1
            if (isArm) {
                wTorso[i] = 1f
                continue
            }
            wHip[i] = smooth(HIP_BLEND_TOP, HIP_BLEND_BOTTOM, yi)
            wKnee[i] = smooth(KNEE_Y + 0.02f, KNEE_Y - 0.03f, yi)
            wTorso[i] = smooth(HIP_Y, HIP_Y + 0.07f, yi)
            val s = if (xi < 0) 0 else 1
            val b = (min(yi, LEG_TOP) / LEG_TOP * (bins - 1)).toInt().coerceIn(0, bins - 1)
            val center = if (cnt[s][b] > 0) sum[s][b] / cnt[s][b] else 0f
            val forward = (zi - center) * front > 0f
            muscle[i] = when {
                yi in 0.31f..0.49f && forward -> Muscle.QUADS
                yi in 0.33f..0.56f && !forward -> Muscle.GLUTES_HAMSTRINGS
                yi in 0.08f..0.27f && !forward -> Muscle.CALVES
                yi in 0.56f..0.68f && abs(xi) < 0.09f && (zi - hipZ) * front > 0f -> Muscle.ABDOMEN
                else -> Muscle.NONE
            }
        }
    }

    /** Heart in the current pose: it moves with the trunk. */
    fun heart(p: Pose): FloatArray {
        val r = rotate(heartRest[1], heartRest[2], HIP_Y, hipZ, front * p.lean)
        return floatArrayOf(heartRest[0], r.first - p.drop, r.second + front * p.shift)
    }

    /** Deformed rest pose: knee, hip and trunk (angles in radians, forward positive). */
    fun deform(p: Pose, out: FloatArray) {
        for (i in 0 until n) {
            var y = rest[i * 3 + 1]
            var z = rest[i * 3 + 2]
            val s = if (side[i] < 0) 0 else 1
            if (wKnee[i] > 0f) {
                // Knee flexion: the foot goes backward.
                val r = rotate(y, z, KNEE_Y, kneeZ, front * p.knee[s])
                y += (r.first - y) * wKnee[i]; z += (r.second - z) * wKnee[i]
            }
            if (wHip[i] > 0f) {
                // Hip flexion: the thigh goes forward.
                val r = rotate(y, z, HIP_Y, hipZ, -front * p.hip[s])
                y += (r.first - y) * wHip[i]; z += (r.second - z) * wHip[i]
            }
            if (wTorso[i] > 0f) {
                // Forward trunk lean.
                val r = rotate(y, z, HIP_Y, hipZ, front * p.lean)
                y += (r.first - y) * wTorso[i]; z += (r.second - z) * wTorso[i]
            }
            out[i * 3] = rest[i * 3]
            out[i * 3 + 1] = y - p.drop
            out[i * 3 + 2] = z + front * p.shift
        }
    }

    companion object {
        const val HIP_Y = 0.50f
        const val HEART_Y = 0.725f
        const val KNEE_Y = 0.285f
        const val ANKLE_Y = 0.04f
        const val LEG_TOP = 0.56f
        const val ARM_X = 0.125f
        const val HIP_BLEND_TOP = 0.54f
        const val HIP_BLEND_BOTTOM = 0.47f

        /** 0 at `from`, 1 at `to`, smooth in between (works in both directions). */
        fun smooth(from: Float, to: Float, v: Float): Float {
            val t = ((v - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }

        /** Rotates (y, z) around (py, pz) in the sagittal plane. */
        fun rotate(y: Float, z: Float, py: Float, pz: Float, a: Float): Pair<Float, Float> {
            val dy = y - py; val dz = z - pz
            val c = cos(a); val s = sin(a)
            return (py + dy * c - dz * s) to (pz + dy * s + dz * c)
        }
    }
}

class Pose {
    val hip = FloatArray(2)
    val knee = FloatArray(2)
    var lean = 0f
    var drop = 0f
    var shift = 0f
}

// ---------------------------------------------------------------- Animation

/** Integrates the detected movement into a smooth pose and decides which muscles light up. */
class SilhouetteAnimator {
    var angle = 0f; private set
    private var walkPhase = 0f
    private var walkAmp = 0f
    private var squat = 0f
    val pose = Pose()
    /** Heartbeat phase [0, 1): 0 = beat. null if there is no recent HR. */
    var beatPhase: Float? = null; private set
    /** Rate at which it beats now; null with no recent data. */
    var bpm: Int? = null; private set

    fun heartbeat(currentBpm: Int?, dt: Float) {
        bpm = currentBpm
        if (currentBpm == null) {
            beatPhase = null
            return
        }
        beatPhase = ((beatPhase ?: 0f) + dt * currentBpm / 60f) % 1f
    }

    /** Activation [0, 1] per muscle group and side (index: group * 2 + side). */
    private val activation = FloatArray(10)

    fun step(m: MotionInput, dt: Float) {
        walkAmp = ease(walkAmp, if (m.walking) 1f else 0f, dt, 0.35f)
        squat = ease(squat, if (m.squatting) 1f else 0f, dt, 0.45f)
        walkPhase = (walkPhase + 2 * PI.toFloat() * (m.cadence / 2f) * dt) % (2 * PI.toFloat())

        // Continuous rotation; while there is movement it turns to profile, where it is seen best.
        val active = max(walkAmp, squat)
        angle = (angle + 2 * PI.toFloat() / ROTATION_PERIOD_S * dt * (1f - active)) % (2 * PI.toFloat())
        if (active > 0.01f) {
            val target = nearest(angle, if (sin(angle) >= 0f) PI.toFloat() / 2f else -PI.toFloat() / 2f)
            angle += (target - angle) * min(1f, dt * 3f) * active
        }

        // Walking: hips in opposition and knee flexed during each leg's swing.
        val s = sin(walkPhase); val c = cos(walkPhase)
        val hipWalk = rad(24f) * walkAmp
        val kneeWalk = rad(45f) * walkAmp
        // Squat: thighs forward, shins and trunk leaning; the body is lowered so the feet stay on the floor.
        val a = rad(68f) * squat
        val g = rad(28f) * squat
        val lt = BodyRig.HIP_Y - BodyRig.KNEE_Y
        val ls = BodyRig.KNEE_Y - BodyRig.ANKLE_Y
        pose.hip[0] = hipWalk * s + a
        pose.hip[1] = -hipWalk * s + a
        pose.knee[0] = kneeWalk * max(0f, c) + a + g
        pose.knee[1] = kneeWalk * max(0f, -c) + a + g
        pose.lean = rad(4f) * walkAmp + rad(26f) * squat
        pose.drop = (lt + ls) - (lt * cos(a) + ls * cos(g))
        pose.shift = -(lt * sin(a) - ls * sin(g))

        // Muscle activation (what the garment would record): walking alternates sides; squatting loads both.
        for (side in 0..1) {
            val swing = max(0f, if (side == 0) c else -c)
            val stance = max(0f, if (side == 0) -c else c)
            set(Muscle.QUADS, side, max(walkAmp * (0.35f + 0.4f * swing), squat))
            set(Muscle.GLUTES_HAMSTRINGS, side, max(walkAmp * (0.25f + 0.45f * stance), 0.9f * squat))
            set(Muscle.CALVES, side, max(walkAmp * (0.2f + 0.6f * stance), 0.35f * squat))
            set(Muscle.ABDOMEN, side, max(0.2f * walkAmp, 0.55f * squat))
        }
    }

    fun level(muscle: Byte, side: Int): Float = if (muscle == Muscle.NONE) 0f else activation[muscle * 2 + side]

    /** Text of the SIM label: which movement and which groups are working; null at rest. */
    fun label(): String? = when {
        squat > 0.3f -> str(R.string.motion_squat)
        walkAmp > 0.3f -> str(R.string.motion_walk)
        else -> null
    }

    private fun set(muscle: Byte, side: Int, v: Float) { activation[muscle * 2 + side] = v }

    private fun ease(current: Float, target: Float, dt: Float, tau: Float) =
        current + (target - current) * (1f - exp(-dt / tau))

    private fun nearest(from: Float, target: Float): Float {
        val twoPi = 2 * PI.toFloat()
        var t = target
        while (t - from > PI) t -= twoPi
        while (from - t > PI) t += twoPi
        return t
    }
}

// ---------------------------------------------------------------- Drawing

private fun DrawScope.drawHologram(rig: BodyRig, anim: SilhouetteAnimator) {
    val m = rig.mesh
    val n = m.vertexCount
    val pos = FloatArray(n * 3)
    rig.deform(anim.pose, pos)

    // Fixed maximum size: the silhouette does not grow when the transition notice above disappears.
    val scale = minOf(size.height * 0.94f, size.width * 0.9f / 0.42f, MAX_BODY_HEIGHT.toPx()) * SIZE_FACTOR
    val originX = size.width * CENTER_X
    val groundY = size.height * 0.98f
    val ca = cos(anim.angle)
    val sa = sin(anim.angle)
    val stroke = 1.dp.toPx()

    val sx = FloatArray(n)
    val sy = FloatArray(n)
    for (i in 0 until n) {
        val x = pos[i * 3]; val y = pos[i * 3 + 1]; val z = pos[i * 3 + 2]
        val xr = x * ca + z * sa
        val zr = -x * sa + z * ca
        val p = CAMERA_DISTANCE / (CAMERA_DISTANCE - zr)
        sx[i] = originX + xr * scale * p
        sy[i] = groundY - (y - 0.5f) * scale * p - 0.5f * scale
    }
    // Orientation of each face relative to the camera (normal after deforming and rotating).
    val facing = FloatArray(m.triCount)
    for (t in 0 until m.triCount) {
        val a = m.tri[t * 3]; val b = m.tri[t * 3 + 1]; val c = m.tri[t * 3 + 2]
        val ux = pos[b * 3] - pos[a * 3]; val uy = pos[b * 3 + 1] - pos[a * 3 + 1]; val uz = pos[b * 3 + 2] - pos[a * 3 + 2]
        val vx = pos[c * 3] - pos[a * 3]; val vy = pos[c * 3 + 1] - pos[a * 3 + 1]; val vz = pos[c * 3 + 2] - pos[a * 3 + 2]
        val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 0f } ?: 1f
        facing[t] = (-nx * sa + nz * ca) / len
    }

    val outline = Path()
    val inner = Array(INNER_ALPHAS.size) { Path() }
    val glow = Array(3) { Path() }
    val e = m.edges
    var k = 0
    while (k < e.size) {
        val a = e[k]; val b = e[k + 1]; val f1 = e[k + 2]; val f2 = e[k + 3]
        k += 4
        val d1 = facing[f1]
        val d2 = if (f2 >= 0) facing[f2] else -d1
        val silhouette = (d1 > 0f) != (d2 > 0f)
        if (!silhouette && d1 <= 0f) continue
        // Active muscle: the edge lights up if both ends are in the same working group.
        val ma = rig.muscle[a]
        val act = if (ma != 0.toByte() && ma == rig.muscle[b]) anim.level(ma, if (rig.side[a] < 0) 0 else 1) else 0f
        val path = when {
            act > 0.15f -> glow[min(2, ((act - 0.15f) / 0.85f * 3f).toInt())]
            silhouette -> outline
            else -> {
                val rim = 1f - min(1f, (abs(d1) + abs(d2)) / 2f)
                inner[min(INNER_ALPHAS.size - 1, (rim * INNER_ALPHAS.size).toInt())]
            }
        }
        path.moveTo(sx[a], sy[a])
        path.lineTo(sx[b], sy[b])
    }

    // Heart and aura: behind the mesh, which stays on top like the skin of a hologram.
    anim.beatPhase?.let { phase ->
        val h = rig.heart(anim.pose)
        val hx = h[0] * ca + h[2] * sa
        val hz = -h[0] * sa + h[2] * ca
        val p = CAMERA_DISTANCE / (CAMERA_DISTANCE - hz)
        val center = Offset(originX + hx * scale * p, groundY - (h[1] - 0.5f) * scale * p - 0.5f * scale)
        drawAura(center, scale, phase)
    }

    val base = HudColors.Cyan
    inner.forEachIndexed { i, p -> if (INNER_ALPHAS[i] > 0f) drawPath(p, base.copy(alpha = INNER_ALPHAS[i]), style = Stroke(stroke)) }
    drawPath(outline, base.copy(alpha = 0.6f), style = Stroke(stroke * 1.2f))
    // Active muscles: wide faint halo underneath and a light stroke on top, brighter the harder they work.
    glow.forEachIndexed { i, p ->
        val level = (i + 1) / 3f
        drawPath(p, ACTIVE_COLOR.copy(alpha = 0.10f * level), style = Stroke(stroke * 5f, cap = StrokeCap.Round))
        drawPath(p, ACTIVE_COLOR.copy(alpha = 0.25f + 0.45f * level), style = Stroke(stroke * 1.3f))
    }

    drawOval(
        base.copy(alpha = 0.14f),
        topLeft = Offset(originX - 0.22f * scale, groundY - 0.03f * scale),
        size = Size(0.44f * scale, 0.06f * scale),
        style = Stroke(stroke),
    )
}

/**
 * Heart aura: a core that contracts on every beat and two rings of light leaving the chest
 * and fading out. The pulse shape imitates the double beat of the heartbeat (systole and its rebound).
 */
private fun DrawScope.drawAura(center: Offset, scale: Float, phase: Float) {
    val beat = exp(-phase * 9f) + 0.45f * exp(-((phase - 0.18f) * (phase - 0.18f)) / 0.002f)
    val core = 0.022f * scale * (1f + 0.45f * beat)
    for (k in 0..1) {
        val t = (phase + k * 0.5f) % 1f
        val radius = core + (0.5f * scale - core) * t
        val alpha = 0.45f * (1f - t) * (1f - t) * (if (k == 0) 1f else 0.6f)
        drawCircle(
            Brush.radialGradient(
                0f to HEART_COLOR.copy(alpha = 0f),
                0.72f to HEART_COLOR.copy(alpha = alpha * 0.35f),
                0.92f to HEART_COLOR.copy(alpha = alpha),
                1f to HEART_COLOR.copy(alpha = 0f),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
    // Glow and core
    drawCircle(
        Brush.radialGradient(
            0f to HEART_COLOR.copy(alpha = 0.55f + 0.35f * beat),
            0.45f to HEART_COLOR.copy(alpha = 0.25f * (0.5f + beat)),
            1f to HEART_COLOR.copy(alpha = 0f),
            center = center,
            radius = core * 3.2f,
        ),
        radius = core * 3.2f,
        center = center,
    )
}
