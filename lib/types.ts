// Modelo de datos de AURA Manager (consola de control de misión).
// Todos los tiempos son MET (Mission Elapsed Time) en segundos; ver lib/met.ts.

/** Cambia cuando el estado guardado deja de ser compatible. */
export const SCHEMA_VERSION = 4;

export type Gravity = "tierra" | "micro" | "luna" | "marte";
export type LoadLevel = "baja" | "media" | "alta" | "critica";
export type Category =
  | "ejercicio"
  | "nutricion"
  | "evaluacion"
  | "suplemento"
  | "recuperacion"
  | "preparacion";
/** CA = carga de acondicionamiento, RE = recuperación. */
export type LoadType = "CA" | "RE" | "ninguna";
export type EntryStatus = "autorizado" | "condicional";
/** eva = actividad extravehicular (salida al exterior o a la superficie). */
export type EventKind = "maniobra" | "hito" | "prueba" | "operacion" | "eva";
export type BlockKind =
  | "sueno"
  | "presueno"
  | "comida"
  | "ejercicio"
  | "maniobra"
  | "prueba"
  | "operacion"
  | "comunicacion"
  | "salud"
  | "libre";

export interface Mission {
  id: string;
  name: string;
  destination: string;
  description: string;
  /** Instante UTC de MET 00/00:00:00 (despegue). null = sin fijar. */
  launchUtc: string | null;
  /** Documento del que procede el cronograma. */
  timelineSource: string;
  /** true mientras el plan de prevención sea un ejemplo del equipo. */
  simulated: boolean;
}

export interface CrewMember {
  id: string;
  name: string;
  role: string;
}

/** Día de vuelo (FD). No coincide con el día natural: lo fija el cronograma. */
export interface FlightDay {
  day: number;
  startMet: number;
  endMet: number;
  phase: string;
  milestone: string;
  /** Entorno gravitatorio al terminar el día de vuelo. */
  gravity: Gravity;
  /** Si existe, ese día hay una transición gravitatoria desde este entorno. */
  transitionFrom?: Gravity;
  /** CO: carga operativa estimada por el equipo. */
  co: LoadLevel;
}

/** Evento puntual del cronograma de misión. */
export interface MissionEvent {
  id: string;
  met: number;
  name: string;
  kind: EventKind;
  /** El cronograma lo da como aproximado (~). */
  approx: boolean;
}

/** Bloque de actividad de la tripulación en el cronograma de misión. */
export interface TimelineBlock {
  id: string;
  startMet: number;
  endMet: number;
  name: string;
  kind: BlockKind;
}

export interface CatalogItem {
  id: string;
  code: string;
  name: string;
  category: Category;
  loadType: LoadType;
  /** Carga exigente: no debe coincidir con días críticos ni con sus vísperas. */
  intense: boolean;
  /** Prepara una transición gravitatoria (aterrizaje, regreso a Tierra). */
  transitionPrep: boolean;
  /** Autoinforme de fatiga/sueño/síntomas; sin él, el monitor muestra gris. */
  selfReport: boolean;
  capabilities: string[];
  /** Qué registra el monitor del astronauta. */
  record: string;
  /** Límite de uso o de interpretación. */
  limit: string;
  /** Dosis o rango prescrito. null = pendiente de protocolo autorizado. */
  dose: string | null;
  source: string;
  sourceUrl?: string;
  experimental: boolean;
}

/** Actividad de prevención programada en un MET para un tripulante. */
export interface PlanEntry {
  id: string;
  crewId: string;
  itemId: string;
  /** MET de inicio. El día de vuelo se deduce de él. */
  met: number;
  status: EntryStatus;
  /**
   * true = la interfaz del astronauta puede adaptarla (sustituir, mover dentro
   * de su periodo u omitir). false = fija: solo la cambia control de misión.
   * Solo tiene efecto en categorías adaptables; ver ADAPTABLE_WINDOW.
   */
  adaptable: boolean;
  note?: string;
}

/**
 * Qué puede adaptar la interfaz del astronauta y en qué tipo de bloque del
 * cronograma: los ejercicios dentro de los periodos de ejercicio y la dieta en
 * las comidas. Todo lo demás (maniobras, pruebas, sueño, operaciones) es fijo.
 */
export const ADAPTABLE_WINDOW: Partial<Record<Category, BlockKind>> = {
  ejercicio: "ejercicio",
  nutricion: "comida",
};

export function isAdaptableCategory(category: Category): boolean {
  return category in ADAPTABLE_WINDOW;
}

/** Categorías que la interfaz puede adaptar dentro de un bloque de este tipo. */
export function adaptableCategoriesOf(kind: BlockKind): Category[] {
  return (Object.keys(ADAPTABLE_WINDOW) as Category[]).filter(
    (c) => ADAPTABLE_WINDOW[c] === kind,
  );
}

// ---------- Adaptaciones decididas por la interfaz del astronauta ----------

export type AdaptationType = "sustitucion" | "reprogramacion" | "omision";
export type ReviewStatus = "pendiente" | "validada" | "objetada";

export interface AdaptationEvidence {
  indicator: string;
  value: string;
  unit?: string;
  source: "manual" | "sensor" | "simulado";
  met?: number;
}

/** Lo que envía la interfaz. Ya está aplicado a bordo: el envío no bloquea. */
export interface AdaptationInput {
  /** Generado por la interfaz; permite reenviar sin duplicar. */
  id: string;
  packageVersion: number;
  crewId: string;
  /** Actividad del plan a la que afecta (activities[].entryId del paquete). */
  entryId: string;
  type: AdaptationType;
  /** Solo en sustituciones. */
  replacementItemId?: string;
  /** Solo en reprogramaciones. */
  newMet?: number;
  /** MET en el que la interfaz tomó la decisión. */
  decidedAtMet: number;
  reason: string;
  evidence: AdaptationEvidence[];
  source: "interfaz" | "simulada";
}

/** Adaptación registrada en control de misión. */
export interface Adaptation extends AdaptationInput {
  receivedAt: string;
  /** Actividad original según el paquete; null si no se encontró. */
  original: { itemId: string; met: number } | null;
  /** Comprobación automática contra la política del paquete. No rechaza. */
  policy: { withinPolicy: boolean; violations: string[] };
  review: { status: ReviewStatus; note: string; reviewedAt: string | null };
}

// ---------- Alertas de la interfaz del astronauta ----------
//
// Lo que la interfaz no resuelve adaptando el plan y debe conocer control de misión:
// una capacidad en rojo, una adaptación que quita una medida de preparación de la
// transición o una incoherencia entre autoinforme y sensor. Como las adaptaciones,
// se envían sin bloquear y el servidor nunca las rechaza por su contenido.

export type AlertKind = "capacidad-roja" | "preparacion-perdida" | "incoherencia";
export type AlertLevel = "rojo" | "aviso";
export type AlertStatus = "nueva" | "vista" | "atendida";

export interface AlertInput {
  /** Generado por la interfaz; permite reenviar sin duplicar. */
  id: string;
  packageVersion: number;
  crewId: string;
  kind: AlertKind;
  level: AlertLevel;
  /** Capacidad afectada (capacities[].id), si aplica. */
  capacityId?: string;
  /** Actividad del plan afectada (activities[].entryId), si aplica. */
  entryId?: string;
  /** MET en el que la interfaz generó la alerta. */
  met: number;
  /** Texto que vio el astronauta. */
  message: string;
  evidence: AdaptationEvidence[];
  source: "interfaz" | "simulada";
}

/** Alerta registrada en control de misión, con su acuse. */
export interface Alert extends AlertInput {
  receivedAt: string;
  ack: { status: AlertStatus; note: string; at: string | null };
}

export const ALERT_KIND_LABEL: Record<AlertKind, string> = {
  "capacidad-roja": "Capacidad en rojo",
  "preparacion-perdida": "Medida de preparación perdida",
  incoherencia: "Incoherencia autoinforme/sensor",
};

export const ADAPTATION_TYPE_LABEL: Record<AdaptationType, string> = {
  sustitucion: "Sustitución",
  reprogramacion: "Reprogramación",
  omision: "Omisión",
};

// ---------- Autoevaluación: capacidades, señales y cuestionario diario ----------
//
// Definido por control de misión y publicado en el paquete; la interfaz del
// astronauta lo aplica. No depende de la misión: los disparadores se expresan
// con entornos gravitatorios, días de vuelo y tipos de evento.
// Todos los umbrales son de demostración, no criterios clínicos.

/** Estado de una capacidad en la interfaz. gris = datos insuficientes. */
export type CapacityLevel = "verde" | "ambar" | "rojo" | "gris";

/** Capacidad que lleva semáforo en la interfaz del astronauta. */
export interface Capacity {
  id: string;
  name: string;
  description: string;
  /** Qué hace la interfaz en ámbar, siempre dentro de adaptationPolicy. */
  onAmbar: string;
  /** Qué hace la interfaz en rojo: remite y avisa; no decide por su cuenta. */
  onRojo: string;
}

/** De dónde sale una señal objetiva. */
export type SignalSource = "reloj" | "ropa" | "prueba";

/** Medida objetiva que la interfaz puede tener y cómo se gradúa. */
export interface Signal {
  id: string;
  name: string;
  unit: string;
  source: SignalSource;
  /**
   * true = no hay dispositivo real en la demo: la interfaz genera valores
   * simulados, guardados y mostrados como «simulado».
   */
  simulated: boolean;
  capacityId: string;
  kind: "number" | "boolean";
  /** Solo numéricas: qué sentido es peor. */
  worseWhen?: "mayor" | "menor";
  /** Solo numéricas: a partir de qué valor (incluido) pasa a ámbar o rojo. null = no aplica. */
  ambar?: number | null;
  rojo?: number | null;
  /** Solo booleanas: nivel cuando la señal es verdadera. */
  whenTrue?: "ambar" | "rojo";
  description: string;
}

export interface QuestionScale {
  min: number;
  max: number;
}

export interface QuestionVariant {
  id: string;
  text: string;
  /** Rótulos de los extremos de la escala tal como se muestran. */
  minLabel: string;
  maxLabel: string;
  /**
   * true = formulada en positivo (alto = mejor). La interfaz la normaliza con
   * (max + min) − valor para que, en todo el sistema, alto = peor.
   */
  inverted: boolean;
}

/** Cuándo entra una pregunta del banco en el cuestionario del día. */
export type QuestionTrigger = { priority: number; reason: string } & (
  | {
      /** Días de vuelo antes o después de uno con transición gravitatoria. */
      kind: "transicion";
      daysBefore: number;
      daysAfter: number;
      /** Solo transiciones hacia estos entornos. Vacío = cualquiera. */
      toGravity: Gravity[];
      /** Solo si se llevan al menos estos días de vuelo seguidos en microgravedad. */
      minDaysInMicro?: number;
    }
  | { kind: "entorno"; gravity: Gravity[] }
  | { kind: "dias-micro"; min: number }
  | { kind: "evento"; eventKind: EventKind; withinDays: number }
  | {
      kind: "senal";
      signalId: string;
      /** Para booleanas: dispara cuando es verdadera; op y value se ignoran. */
      op?: ">=" | "<=";
      value?: number;
    }
  /** Relleno cuando no hay suficientes preguntas disparadas. */
  | { kind: "relleno" }
);

export interface QuestionItem {
  id: string;
  /** fijo = bloque A, todos los días; banco = bloque B, según disparadores. */
  block: "fijo" | "banco";
  domain: string;
  capacityId: string;
  scale: QuestionScale;
  /** Instrumento en el que se inspira. Una pregunta suelta no es el instrumento validado. */
  inspiredBy: string;
  variants: QuestionVariant[];
  /** Sobre el valor normalizado (alto = peor), incluido. null = no aplica. */
  ambar: number | null;
  rojo: number | null;
  triggers: QuestionTrigger[];
}

/** Coherencia entre autoinforme y sensor: detecta infrarreporte. */
export interface ConsistencyCheck {
  id: string;
  questionId: string;
  /** Respuesta normalizada igual o menor que esta = el astronauta dice estar bien. */
  answerAtMost: number;
  signalId: string;
  op: ">=" | "<=";
  value: number;
  message: string;
}

export interface Questionnaire {
  /** Actividad del catálogo que lo programa en el plan. */
  catalogItemId: string;
  name: string;
  source: string;
  fixedPerDay: number;
  bankPerDay: number;
  items: QuestionItem[];
  consistencyChecks: ConsistencyCheck[];
}

export interface AppState {
  schemaVersion: number;
  mission: Mission;
  crew: CrewMember[];
  days: FlightDay[];
  events: MissionEvent[];
  blocks: TimelineBlock[];
  catalog: CatalogItem[];
  plan: PlanEntry[];
  capacities: Capacity[];
  signals: Signal[];
  questionnaire: Questionnaire;
  updatedAt: string;
}

export type Severity = "bloqueo" | "aviso" | "info";

export interface Issue {
  id: string;
  rule: string;
  severity: Severity;
  /** Día de vuelo. null = afecta a toda la misión. */
  day: number | null;
  crewId: string | null;
  message: string;
}

export interface PackageSummary {
  packageVersion: number;
  publishedAt: string;
  checksum: string;
  missionName: string;
  schema: string;
}

/** Día de vuelo al que pertenece un MET, o null si cae fuera de la misión. */
export function flightDayOf(days: FlightDay[], metSeconds: number): number | null {
  const last = days.at(-1);
  for (const d of days) {
    const inside =
      metSeconds >= d.startMet &&
      (metSeconds < d.endMet || (d === last && metSeconds === d.endMet));
    if (inside) return d.day;
  }
  return null;
}

export const GRAVITY_G: Record<Gravity, number> = {
  tierra: 1,
  marte: 0.38,
  luna: 0.165,
  micro: 0,
};

export const GRAVITY_LABEL: Record<Gravity, string> = {
  tierra: "Tierra · 1,00 g",
  marte: "Marte · 0,38 g",
  luna: "Luna · 0,165 g",
  micro: "Microgravedad · ≈0 g",
};

export const GRAVITY_SHORT: Record<Gravity, string> = {
  tierra: "Tierra",
  marte: "Marte",
  luna: "Luna",
  micro: "Micro-g",
};

export const LOAD_LABEL: Record<LoadLevel, string> = {
  baja: "Baja",
  media: "Media",
  alta: "Alta",
  critica: "Crítica",
};

export const CATEGORY_LABEL: Record<Category, string> = {
  ejercicio: "Ejercicio",
  nutricion: "Nutrición",
  evaluacion: "Evaluación",
  suplemento: "Suplemento",
  recuperacion: "Recuperación",
  preparacion: "Preparación",
};

export const CATEGORIES: Category[] = [
  "ejercicio",
  "nutricion",
  "evaluacion",
  "preparacion",
  "recuperacion",
  "suplemento",
];

export const EVENT_KIND_LABEL: Record<EventKind, string> = {
  maniobra: "Maniobra",
  hito: "Hito",
  prueba: "Prueba (FTO/DFTO)",
  operacion: "Operación",
  eva: "EVA",
};

export const BLOCK_KIND_LABEL: Record<BlockKind, string> = {
  sueno: "Sueño",
  presueno: "Pre/postsueño",
  comida: "Comida",
  ejercicio: "Ejercicio",
  maniobra: "Maniobra",
  prueba: "Prueba (FTO/DFTO)",
  operacion: "Operación",
  comunicacion: "Conferencias y PAO",
  salud: "Salud",
  libre: "Tiempo libre",
};
