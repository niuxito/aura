import { formatFlightDay, formatMet } from "./met";
import { validate } from "./rules";
import type {
  AdaptationType,
  AppState,
  Category,
  EntryStatus,
  FlightDay,
  Issue,
  MissionEvent,
  TimelineBlock,
} from "./types";
import {
  ADAPTABLE_WINDOW,
  GRAVITY_G,
  adaptableCategoriesOf,
  flightDayOf,
  isAdaptableCategory,
} from "./types";

export const PACKAGE_SCHEMA = "aura.mission-package/v3";

// En el paquete, cada MET viaja dos veces: en segundos (para calcular) y como
// etiqueta DD/HH:MM[:SS] (para mostrar).

export interface PackageActivity {
  /** Referencia estable de la actividad; es la que citan las adaptaciones. */
  entryId: string;
  itemId: string;
  met: number;
  metLabel: string;
  status: EntryStatus;
  /** true = la interfaz puede adaptarla según adaptationPolicy. */
  adaptable: boolean;
  note?: string;
}

/** Qué puede cambiar la interfaz por su cuenta y cómo debe informarlo. */
export interface AdaptationPolicy {
  /** Categorías adaptables → tipo de bloque en el que pueden moverse. */
  windows: Partial<Record<Category, string>>;
  allowed: AdaptationType[];
  rules: string[];
  report: { endpoint: string; method: "POST"; blocking: false };
}

export const ADAPTATION_RULES = [
  "Solo se adaptan las actividades con adaptable: true. Las demás, y todos los bloques con adaptable: false (maniobras, pruebas, sueño, operaciones), no se modifican nunca desde la interfaz.",
  "Sustitución: solo por otra acción del catálogo de la misma categoría.",
  "Una sustitución no puede aumentar la carga: no se cambia una acción por otra con intense: true si la original no lo era.",
  "Reprogramación: solo a un MET dentro de un bloque adaptable de su categoría y en el mismo día de vuelo.",
  "Omisión: permitida en actividades adaptables, siempre con motivo.",
  "Cada adaptación se aplica a bordo sin esperar respuesta y se envía a control de misión para su registro y validación. Si no hay conexión, se guarda y se envía después.",
];

/** Contrato que consume la interfaz del astronauta (offline). */
export interface MissionPackage {
  schema: typeof PACKAGE_SCHEMA;
  packageVersion: number;
  publishedAt: string;
  /** sha256 del JSON canónico del paquete sin este campo. */
  checksum: string;
  /** true si el plan de prevención es un ejemplo, no una prescripción. */
  simulated: boolean;
  mission: AppState["mission"];
  /** Gravedad relativa a la Tierra de cada entorno. */
  gravityReference: typeof GRAVITY_G;
  crew: AppState["crew"];
  days: (FlightDay & { label: string; startMetLabel: string; endMetLabel: string })[];
  events: (MissionEvent & { metLabel: string; day: number | null })[];
  blocks: (TimelineBlock & {
    startMetLabel: string;
    endMetLabel: string;
    /** true = periodo cuyo contenido puede adaptar la interfaz. */
    adaptable: boolean;
    adaptableCategories: Category[];
  })[];
  catalog: AppState["catalog"];
  adaptationPolicy: AdaptationPolicy;
  /** crewId → un elemento por día de vuelo, con actividades ordenadas por MET. */
  plans: Record<string, { day: number; activities: PackageActivity[] }[]>;
  /** Avisos e informaciones vigentes al publicar (nunca bloqueos). */
  validation: Issue[];
}

export type PackageBody = Omit<MissionPackage, "checksum">;

export function buildPackageBody(
  state: AppState,
  packageVersion: number,
  publishedAt: string,
): PackageBody {
  const categoryOf = new Map(state.catalog.map((c) => [c.id, c.category]));
  const plans: PackageBody["plans"] = {};
  for (const member of state.crew) {
    plans[member.id] = state.days.map((d) => ({
      day: d.day,
      activities: state.plan
        .filter((e) => e.crewId === member.id && flightDayOf(state.days, e.met) === d.day)
        .sort((a, b) => a.met - b.met)
        .map((e) => ({
          entryId: e.id,
          itemId: e.itemId,
          met: e.met,
          metLabel: formatMet(e.met),
          status: e.status,
          adaptable: e.adaptable && isAdaptableCategory(categoryOf.get(e.itemId) ?? "evaluacion"),
          ...(e.note ? { note: e.note } : {}),
        })),
    }));
  }
  return {
    schema: PACKAGE_SCHEMA,
    packageVersion,
    publishedAt,
    simulated: state.mission.simulated,
    mission: state.mission,
    gravityReference: GRAVITY_G,
    crew: state.crew,
    days: state.days.map((d) => ({
      ...d,
      label: formatFlightDay(d.day),
      startMetLabel: formatMet(d.startMet),
      endMetLabel: formatMet(d.endMet),
    })),
    events: [...state.events]
      .sort((a, b) => a.met - b.met)
      .map((e) => ({
        ...e,
        metLabel: formatMet(e.met),
        day: flightDayOf(state.days, e.met),
      })),
    blocks: [...state.blocks]
      .sort((a, b) => a.startMet - b.startMet)
      .map((b) => ({
        ...b,
        startMetLabel: formatMet(b.startMet),
        endMetLabel: formatMet(b.endMet),
        adaptable: adaptableCategoriesOf(b.kind).length > 0,
        adaptableCategories: adaptableCategoriesOf(b.kind),
      })),
    catalog: state.catalog,
    adaptationPolicy: {
      windows: ADAPTABLE_WINDOW,
      allowed: ["sustitucion", "reprogramacion", "omision"],
      rules: ADAPTATION_RULES,
      report: { endpoint: "/api/adaptations", method: "POST", blocking: false },
    },
    plans,
    validation: validate(state),
  };
}

/** JSON con claves ordenadas y sin espacios, para un checksum reproducible. */
export function canonicalJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(",")}]`;
  if (value && typeof value === "object") {
    const obj = value as Record<string, unknown>;
    return `{${Object.keys(obj)
      .filter((k) => obj[k] !== undefined)
      .sort()
      .map((k) => `${JSON.stringify(k)}:${canonicalJson(obj[k])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value);
}
