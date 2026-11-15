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
 * Silueta humana en 3D que gira y reproduce el movimiento detectado (caminar, agacharse),
 * iluminando los grupos musculares que trabajan, como lo mostraría la ropa inteligente.
 * SIMULADO: el movimiento sale del acelerómetro de la tablet o de los botones del modo demo;
 * no representa el estado de salud ni se guarda. (AGENTS.md, «Silueta y actividad simulada».)
 *
 * Malla: piel de BodyParts3D (DBCLS, CC BY-SA) vía anatomia.alexisparron.com (assets/models/LICENSE-body.txt).
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
            if (frame >= 0L) drawHologram(r, anim) // leer `frame` hace que se redibuje en cada fotograma
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

/** Valor de FC, fuente y antigüedad del dato. Sin dato reciente: gris, «sin datos del reloj». */
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
            // U+FE0E: el corazón como símbolo de texto en nuestro color, no como emoji rojo (el rojo es del semáforo).
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
/** Altura máxima del cuerpo (antes de SIZE_FACTOR): la que tiene con el aviso de transición visible en la tablet. */
private val MAX_BODY_HEIGHT = 440.dp
/** Color del corazón y su aura: cálido y suave, distinto de los colores del semáforo (rojo y ámbar). */
private val HEART_COLOR = Color(0xFFFF9EC4)
/** Tamaño relativo al hueco disponible y posición horizontal del eje (0 = izquierda, 1 = derecha). */
private const val SIZE_FACTOR = 0.9f
private const val CENTER_X = 0.62f
private const val ROTATION_PERIOD_S = 16f
private const val CAMERA_DISTANCE = 4f
private val INNER_ALPHAS = floatArrayOf(0f, 0.025f, 0.06f, 0.12f)
/** Color de la actividad muscular: más claro que el cian del HUD, distinto de los colores del semáforo. */
private val ACTIVE_COLOR = Color(0xFFCFF8FF)

private fun rad(deg: Float) = deg * PI.toFloat() / 180f

// ---------------------------------------------------------------- Malla

/** Malla normalizada: altura 1, pies en y = 0, centrada en x/z. */
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
            // Aristas con sus dos caras vecinas: a, b, cara 1, cara 2 (−1 si es borde).
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

// ---------------------------------------------------------------- Esqueleto aproximado

/** Grupos musculares que se iluminan. */
private object Muscle {
    const val NONE: Byte = 0
    const val QUADS: Byte = 1
    const val GLUTES_HAMSTRINGS: Byte = 2
    const val CALVES: Byte = 3
    const val ABDOMEN: Byte = 4
}

/**
 * Asigna cada vértice a pierna (izquierda/derecha), tronco o brazo según su posición en la malla
 * en reposo (proporciones humanas normalizadas a altura 1) y calcula pivotes de cadera y rodilla.
 */
class BodyRig(val mesh: BodyMesh) {
    private val n = mesh.vertexCount
    private val rest = mesh.xyz
    /** −1 izquierda, +1 derecha (por el signo de x); solo cuenta en las piernas. */
    val side = IntArray(n)
    val wHip = FloatArray(n)
    val wKnee = FloatArray(n)
    val wTorso = FloatArray(n)
    val muscle = ByteArray(n)
    /** Sentido de «delante» en z: hacia donde apuntan los dedos de los pies. */
    val front: Float
    val hipZ: Float
    val kneeZ: Float
    /** Posición del corazón en reposo: pecho, algo a la izquierda del esternón. */
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
        // Mirando hacia +z con y hacia arriba, la izquierda del cuerpo es +x: el lado izquierdo es `front`.
        val chestZ = meanZ { y(it) in HEART_Y - 0.02f..HEART_Y + 0.02f && abs(x(it)) < 0.1f }
        heartRest = floatArrayOf(0.014f * front, HEART_Y, chestZ + 0.012f * front)

        // Centro en z de cada pierna por franjas de altura, para saber qué es la cara delantera del muslo.
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

    /** Corazón en la postura actual: va con el tronco. */
    fun heart(p: Pose): FloatArray {
        val r = rotate(heartRest[1], heartRest[2], HIP_Y, hipZ, front * p.lean)
        return floatArrayOf(heartRest[0], r.first - p.drop, r.second + front * p.shift)
    }

    /** Postura en reposo deformada: rodilla, cadera y tronco (ángulos en radianes, adelante positivo). */
    fun deform(p: Pose, out: FloatArray) {
        for (i in 0 until n) {
            var y = rest[i * 3 + 1]
            var z = rest[i * 3 + 2]
            val s = if (side[i] < 0) 0 else 1
            if (wKnee[i] > 0f) {
                // Flexión de rodilla: el pie va hacia atrás.
                val r = rotate(y, z, KNEE_Y, kneeZ, front * p.knee[s])
                y += (r.first - y) * wKnee[i]; z += (r.second - z) * wKnee[i]
            }
            if (wHip[i] > 0f) {
                // Flexión de cadera: el muslo va hacia delante.
                val r = rotate(y, z, HIP_Y, hipZ, -front * p.hip[s])
                y += (r.first - y) * wHip[i]; z += (r.second - z) * wHip[i]
            }
            if (wTorso[i] > 0f) {
                // Inclinación del tronco hacia delante.
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

        /** 0 en `from`, 1 en `to`, suave entre medias (funciona en ambos sentidos). */
        fun smooth(from: Float, to: Float, v: Float): Float {
            val t = ((v - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }

        /** Gira (y, z) alrededor de (py, pz) en el plano sagital. */
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

// ---------------------------------------------------------------- Animación

/** Integra el movimiento detectado en una postura suave y decide qué músculos se iluminan. */
class SilhouetteAnimator {
    var angle = 0f; private set
    private var walkPhase = 0f
    private var walkAmp = 0f
    private var squat = 0f
    val pose = Pose()
    /** Fase del latido [0, 1): 0 = latido. null si no hay FC reciente. */
    var beatPhase: Float? = null; private set
    /** Frecuencia con la que late ahora; null sin dato reciente. */
    var bpm: Int? = null; private set

    fun heartbeat(currentBpm: Int?, dt: Float) {
        bpm = currentBpm
        if (currentBpm == null) {
            beatPhase = null
            return
        }
        beatPhase = ((beatPhase ?: 0f) + dt * currentBpm / 60f) % 1f
    }

    /** Activación [0, 1] por grupo muscular y lado (índice: grupo * 2 + lado). */
    private val activation = FloatArray(10)

    fun step(m: MotionInput, dt: Float) {
        walkAmp = ease(walkAmp, if (m.walking) 1f else 0f, dt, 0.35f)
        squat = ease(squat, if (m.squatting) 1f else 0f, dt, 0.45f)
        walkPhase = (walkPhase + 2 * PI.toFloat() * (m.cadence / 2f) * dt) % (2 * PI.toFloat())

        // Giro continuo; mientras hay movimiento se orienta de perfil, donde se ve mejor.
        val active = max(walkAmp, squat)
        angle = (angle + 2 * PI.toFloat() / ROTATION_PERIOD_S * dt * (1f - active)) % (2 * PI.toFloat())
        if (active > 0.01f) {
            val target = nearest(angle, if (sin(angle) >= 0f) PI.toFloat() / 2f else -PI.toFloat() / 2f)
            angle += (target - angle) * min(1f, dt * 3f) * active
        }

        // Caminar: caderas en oposición y rodilla flexionada durante el balanceo de cada pierna.
        val s = sin(walkPhase); val c = cos(walkPhase)
        val hipWalk = rad(24f) * walkAmp
        val kneeWalk = rad(45f) * walkAmp
        // Sentadilla: muslos adelante, tibias inclinadas y tronco inclinado; se baja el cuerpo para que los pies sigan en el suelo.
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

        // Activación muscular (lo que registraría la prenda): caminar alterna lados; agacharse carga ambos.
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

    /** Texto de la etiqueta SIM: qué movimiento y qué grupos trabajan; null en reposo. */
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

// ---------------------------------------------------------------- Dibujo

private fun DrawScope.drawHologram(rig: BodyRig, anim: SilhouetteAnimator) {
    val m = rig.mesh
    val n = m.vertexCount
    val pos = FloatArray(n * 3)
    rig.deform(anim.pose, pos)

    // Tamaño máximo fijo: la silueta no crece cuando desaparece el aviso de transición de encima.
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
    // Orientación de cada cara respecto a la cámara (normal tras deformar y girar).
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
        // Músculo activo: la arista se ilumina si sus dos extremos están en el mismo grupo trabajando.
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

    // Corazón y aura: detrás de la malla, que queda por encima como la piel de un holograma.
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
    // Músculos activos: halo ancho y tenue debajo y trazo claro encima, más intenso cuanto más trabajan.
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
 * Aura del corazón: un núcleo que se contrae en cada latido y dos anillos de luz que salen del pecho
 * y se desvanecen. La forma del pulso imita el doble golpe del latido (sístole y su rebote).
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
    // Resplandor y núcleo
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
