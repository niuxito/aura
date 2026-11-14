// Modelo de datos de AURA Manager (consola de control de misión).
// Todos los tiempos son MET (Mission Elapsed Time) en segundos; ver lib/met.ts.

/** Cambia cuando el estado guardado deja de ser compatible. */
export const SCHEMA_VERSION = 3;

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
export type EventKind = "maniobra" | "hito" | "prueba" | "operacion";
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

export const ADAPTATION_TYPE_LABEL: Record<AdaptationType, string> = {
  sustitucion: "Sustitución",
  reprogramacion: "Reprogramación",
  omision: "Omisión",
};

export interface AppState {
  schemaVersion: number;
  mission: Mission;
  crew: CrewMember[];
  days: FlightDay[];
  events: MissionEvent[];
  blocks: TimelineBlock[];
  catalog: CatalogItem[];
  plan: PlanEntry[];
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
