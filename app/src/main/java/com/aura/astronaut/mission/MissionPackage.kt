package com.aura.astronaut.mission

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/*
 * Modelos del paquete de misión `aura.mission-package/v5` (códigos en inglés y textos LocalizedText).
 * Fuente de verdad: ../aura-manager/lib/package.ts y ../aura-manager/lib/types.ts.
 * Si cambian allí, hay que actualizar estos modelos y contract/mission-package.example.json.
 * Todos los MET van en segundos (Long); las etiquetas *Label son solo para mostrar.
 */

/** Esquema del Manager. Los paquetes v3 y v4 ya no se aceptan (contract/v4-to-v5.md). */
const val SUPPORTED_SCHEMA = "aura.mission-package/v5"

@Serializable
data class MissionPackage(
    val schema: String,
    val packageVersion: Int,
    val publishedAt: String,
    /** Idiomas en los que vienen los textos y el de respaldo. */
    val languages: List<String> = listOf("en"),
    val defaultLanguage: String = "en",
    /** `sha256:<hex>` del JSON canónico del paquete sin este campo. */
    val checksum: String? = null,
    /** true si la misión o el plan de prevención son un ejemplo, no una prescripción. */
    val simulated: Boolean,
    val mission: Mission,
    val gravityReference: Map<Gravity, Double>,
    val crew: List<CrewMember>,
    val days: List<FlightDay>,
    val events: List<MissionEvent>,
    val blocks: List<TimelineBlock>,
    val catalog: List<CatalogItem>,
    val adaptationPolicy: AdaptationPolicy,
    /** crewId → un elemento por día de vuelo, con las actividades ordenadas por MET. */
    val plans: Map<String, List<DayPlan>>,
    /** Capacidades con semáforo en la interfaz y qué hacer en ámbar o rojo. */
    val capacities: List<Capacity>,
    /** Señales objetivas; las simuladas las genera la interfaz y se etiquetan así. */
    val signals: List<Signal>,
    /** Cuestionario diario: bloque fijo, banco con disparadores y umbrales. */
    val questionnaire: Questionnaire,
    /** Todos los umbrales de capacities, signals y questionnaire son de demostración. */
    val thresholdsAreDemo: Boolean = true,
    val validation: List<ValidationIssue>,
)

@Serializable
data class Mission(
    val id: String,
    val name: LocalizedText,
    val destination: LocalizedText,
    val description: LocalizedText,
    /** Instante UTC de MET 0 (despegue), o null si no está fijado. */
    val launchUtc: String? = null,
    val timelineSource: String,
    val simulated: Boolean,
)

@Serializable
data class CrewMember(
    val id: String,
    val name: String,
    val role: LocalizedText,
    /** FC en reposo de referencia (lpm). Valor de demostración; null = desconocida. */
    val restingHrBpm: Int? = null,
)

@Serializable
data class FlightDay(
    val day: Int,
    val label: String,
    val startMet: Long,
    val endMet: Long,
    val startMetLabel: String,
    val endMetLabel: String,
    val phase: LocalizedText,
    val milestone: LocalizedText,
    /** Entorno gravitatorio al acabar el día de vuelo. */
    val gravity: Gravity,
    /** Solo si ese día hay una transición gravitatoria: entorno de partida. */
    val transitionFrom: Gravity? = null,
    /** MET exacto de la transición (despegue, amerizaje), si el paquete lo trae. */
    val transitionMet: Long? = null,
    /** Carga operativa estimada. */
    val opLoad: LoadLevel,
)

@Serializable
data class MissionEvent(
    val id: String,
    val met: Long,
    val metLabel: String,
    val day: Int? = null,
    val name: LocalizedText,
    val kind: EventKind,
    /** El cronograma lo da como aproximado: se muestra con «~». */
    val approx: Boolean,
)

@Serializable
data class TimelineBlock(
    val id: String,
    val startMet: Long,
    val endMet: Long,
    val startMetLabel: String,
    val endMetLabel: String,
    val name: LocalizedText,
    val kind: BlockKind,
    /** true = periodo cuyo contenido puede adaptar la interfaz. */
    val adaptable: Boolean,
    val adaptableCategories: List<Category>,
)

@Serializable
data class CatalogItem(
    val id: String,
    val code: String,
    val name: LocalizedText,
    val category: Category,
    val loadType: LoadType,
    val intense: Boolean,
    val transitionPrep: Boolean,
    val selfReport: Boolean,
    val capabilities: List<LocalizedText>,
    /** Qué debe registrar el monitor. */
    val record: LocalizedText,
    /** Límite de uso o de interpretación: hay que mostrarlo. */
    val limit: LocalizedText,
    /** null = sin dosis prescrita. La interfaz nunca inventa ni cambia dosis. */
    val dose: LocalizedText? = null,
    val source: LocalizedText,
    val sourceUrl: String? = null,
    val experimental: Boolean = false,
    /** Duración prevista en minutos (estimación del equipo); null = desconocida. */
    val durationMin: Int? = null,
)

@Serializable
data class AdaptationPolicy(
    /** Categoría adaptable → tipo de bloque dentro del que puede moverse. */
    val windows: Map<Category, BlockKind>,
    val allowed: List<AdaptationType>,
    val rules: List<LocalizedText>,
    val report: ReportPolicy,
)

@Serializable
data class ReportPolicy(val endpoint: String, val method: String, val blocking: Boolean)

@Serializable
data class DayPlan(val day: Int, val activities: List<PlanActivity>)

@Serializable
data class PlanActivity(
    /** Referencia estable que citan las adaptaciones. */
    val entryId: String,
    /** Apunta a [CatalogItem.id]. */
    val itemId: String,
    val met: Long,
    val metLabel: String,
    val status: EntryStatus,
    /** true = la interfaz puede adaptarla según [AdaptationPolicy]. */
    val adaptable: Boolean,
    /** Fin previsto (duración del catálogo); null si el paquete no lo trae. */
    val endMet: Long? = null,
    val note: LocalizedText? = null,
)

@Serializable
data class ValidationIssue(
    val id: String,
    val rule: String,
    val severity: Severity,
    /** Día de vuelo; null = toda la misión. */
    val day: Int? = null,
    val crewId: String? = null,
    val message: LocalizedText,
)

@Serializable
enum class Gravity {
    @SerialName("earth") EARTH,
    @SerialName("micro") MICRO,
    @SerialName("moon") MOON,
    @SerialName("mars") MARS,
}

@Serializable
enum class LoadLevel {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
    @SerialName("critical") CRITICAL,
}

@Serializable
enum class Category {
    @SerialName("exercise") EXERCISE,
    @SerialName("nutrition") NUTRITION,
    @SerialName("assessment") ASSESSMENT,
    @SerialName("supplement") SUPPLEMENT,
    @SerialName("recovery") RECOVERY,
    @SerialName("preparation") PREPARATION,
}

/** CL = carga de acondicionamiento, RE = recuperación. */
@Serializable
enum class LoadType {
    @SerialName("CL") CONDITIONING,
    @SerialName("RE") RECOVERY,
    @SerialName("none") NONE,
}

@Serializable
enum class EntryStatus {
    @SerialName("authorized") AUTHORIZED,
    /** No se presenta como obligatoria: depende de algo que hay que indicar. */
    @SerialName("conditional") CONDITIONAL,
}

@Serializable
enum class EventKind {
    @SerialName("maneuver") MANEUVER,
    @SerialName("milestone") MILESTONE,
    @SerialName("test") TEST,
    @SerialName("operation") OPERATION,
    /** Actividad extravehicular: salida al exterior o a la superficie. */
    @SerialName("eva") EVA,
}

@Serializable
enum class BlockKind {
    @SerialName("sleep") SLEEP,
    @SerialName("presleep") PRE_SLEEP,
    @SerialName("meal") MEAL,
    @SerialName("exercise") EXERCISE,
    @SerialName("maneuver") MANEUVER,
    @SerialName("test") TEST,
    @SerialName("operation") OPERATION,
    @SerialName("communication") COMMUNICATION,
    @SerialName("health") HEALTH,
    @SerialName("off-duty") FREE,
}

@Serializable
enum class AdaptationType {
    @SerialName("substitution") SUBSTITUTION,
    @SerialName("reschedule") RESCHEDULE,
    @SerialName("omission") OMISSION,
}

@Serializable
enum class Severity {
    @SerialName("blocker") BLOCKING,
    @SerialName("warning") WARNING,
    @SerialName("info") INFO,
}

// ---------- Autoevaluación (v4): capacidades, señales y cuestionario diario ----------
// Los define control de misión; la interfaz los aplica. Todos los umbrales son de demostración.

/** Estado de una capacidad. GRAY = datos insuficientes; nunca se muestra verde por defecto. */
@Serializable
enum class CapacityLevel {
    @SerialName("green") GREEN,
    @SerialName("amber") AMBER,
    @SerialName("red") RED,
    @SerialName("gray") GRAY,
}

@Serializable
data class Capacity(
    val id: String,
    val name: LocalizedText,
    val description: LocalizedText,
    /** Qué hace la interfaz en ámbar, siempre dentro de adaptationPolicy. */
    val onAmber: LocalizedText,
    /** Qué hace la interfaz en rojo: remite y avisa; no decide por su cuenta. */
    val onRed: LocalizedText,
    /** Sustituciones concretas que aplica la interfaz en ámbar (por ejemplo, el menú con hidratación). */
    val onAmberActions: List<AmberAction> = emptyList(),
    /**
     * Entornos de llegada en los que esta capacidad es crítica para salir de la nave.
     * null = el paquete no lo declara (paquetes anteriores).
     */
    val criticalFor: List<Gravity>? = null,
)

/** En ámbar, la actividad adaptable de hoy de [category] pasa a ser [replacementItemId]. */
@Serializable
data class AmberAction(val category: Category, val replacementItemId: String)

@Serializable
enum class SignalSource {
    @SerialName("watch") WATCH,
    @SerialName("garment") GARMENT,
    @SerialName("test") TEST,
}

@Serializable
enum class SignalKind {
    @SerialName("number") NUMBER,
    @SerialName("boolean") BOOLEAN,
}

/** En qué sentido empeora una señal numérica. */
@Serializable
enum class WorseWhen {
    @SerialName("higher") HIGHER,
    @SerialName("lower") LOWER,
}

@Serializable
enum class ComparisonOp {
    @SerialName(">=") AT_LEAST,
    @SerialName("<=") AT_MOST,
}

@Serializable
data class Signal(
    val id: String,
    val name: LocalizedText,
    val unit: String,
    val source: SignalSource,
    /** true = no hay dispositivo real en la demo: la interfaz genera valores marcados como simulados. */
    val simulated: Boolean,
    val capacityId: String,
    val kind: SignalKind,
    /** Solo numéricas. */
    val worseWhen: WorseWhen? = null,
    /** Solo numéricas: a partir de qué valor (incluido) pasa a ámbar o rojo; null = no aplica. */
    val amber: Double? = null,
    val red: Double? = null,
    /** Solo booleanas: nivel cuando la señal es verdadera. */
    val whenTrue: CapacityLevel? = null,
    val description: LocalizedText,
)

@Serializable
data class QuestionScale(val min: Int, val max: Int)

@Serializable
data class QuestionVariant(
    val id: String,
    val text: LocalizedText,
    val minLabel: LocalizedText,
    val maxLabel: LocalizedText,
    /** true = formulada en positivo (alto = mejor): se normaliza con (max + min) − valor para que alto = peor. */
    val inverted: Boolean,
)

@Serializable
enum class QuestionBlock {
    /** Bloque A: todos los días. */
    @SerialName("fixed") FIXED,
    /** Bloque B: según disparadores. */
    @SerialName("bank") BANK,
}

/** Cuándo entra una pregunta del banco en el cuestionario del día (campo `kind` en el JSON). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface QuestionTrigger {
    val priority: Int
    val reason: LocalizedText

    /** Días de vuelo antes o después de uno con transición gravitatoria. */
    @Serializable
    @SerialName("transition")
    data class Transition(
        override val priority: Int,
        override val reason: LocalizedText,
        val daysBefore: Int,
        val daysAfter: Int,
        /** Solo transiciones hacia estos entornos; vacío = cualquiera. */
        val toGravity: List<Gravity> = emptyList(),
        val minDaysInMicro: Int? = null,
    ) : QuestionTrigger

    @Serializable
    @SerialName("environment")
    data class Environment(
        override val priority: Int,
        override val reason: LocalizedText,
        val gravity: List<Gravity>,
    ) : QuestionTrigger

    @Serializable
    @SerialName("micro-days")
    data class DaysInMicro(
        override val priority: Int,
        override val reason: LocalizedText,
        val min: Int,
    ) : QuestionTrigger

    @Serializable
    @SerialName("event")
    data class Event(
        override val priority: Int,
        override val reason: LocalizedText,
        val eventKind: EventKind,
        val withinDays: Int,
    ) : QuestionTrigger

    /** Para señales booleanas dispara cuando es verdadera; [op] y [value] se ignoran. */
    @Serializable
    @SerialName("signal")
    data class SignalValue(
        override val priority: Int,
        override val reason: LocalizedText,
        val signalId: String,
        val op: ComparisonOp? = null,
        val value: Double? = null,
    ) : QuestionTrigger

    /** Relleno cuando no hay suficientes preguntas disparadas. */
    @Serializable
    @SerialName("filler")
    data class Filler(
        override val priority: Int,
        override val reason: LocalizedText,
    ) : QuestionTrigger
}

@Serializable
data class QuestionItem(
    val id: String,
    val block: QuestionBlock,
    val domain: LocalizedText,
    val capacityId: String,
    val scale: QuestionScale,
    /** Instrumento en el que se inspira; una pregunta suelta no es el instrumento validado. */
    val inspiredBy: LocalizedText,
    val variants: List<QuestionVariant>,
    /** Sobre el valor normalizado (alto = peor), incluido; null = no aplica. */
    val amber: Double? = null,
    val red: Double? = null,
    val triggers: List<QuestionTrigger>,
)

/** Coherencia entre autoinforme y sensor: detecta infrarreporte. */
@Serializable
data class ConsistencyCheck(
    val id: String,
    val questionId: String,
    /** Respuesta normalizada igual o menor que esta = el astronauta dice estar bien. */
    val answerAtMost: Double,
    val signalId: String,
    val op: ComparisonOp,
    val value: Double,
    val message: LocalizedText,
)

@Serializable
data class Questionnaire(
    /** Actividad del catálogo que lo programa en el plan. */
    val catalogItemId: String,
    val name: LocalizedText,
    val source: LocalizedText,
    val fixedPerDay: Int,
    val bankPerDay: Int,
    val items: List<QuestionItem>,
    val consistencyChecks: List<ConsistencyCheck>,
)

/** Valor de [AdaptationType] tal como viaja en el JSON. */
val AdaptationType.wire: String
    get() = when (this) {
        AdaptationType.SUBSTITUTION -> "substitution"
        AdaptationType.RESCHEDULE -> "reschedule"
        AdaptationType.OMISSION -> "omission"
    }
