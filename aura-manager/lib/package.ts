import { DEFAULT_LOCALE, DICTIONARIES, LOCALES, localized, type Locale, type LocalizedText } from "./i18n";
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

export const PACKAGE_SCHEMA = "aura.mission-package/v5";
/**
 * Schemas that carry an adaptation policy (v4 added self-assessment; v5 uses
 * English codes and multilingual texts).
 */
export const ADAPTATION_SCHEMAS = [
  "aura.mission-package/v3",
  "aura.mission-package/v4",
  PACKAGE_SCHEMA,
];

// In the package every MET travels twice: in seconds (to compute with) and as
// a DD/HH:MM[:SS] label (to display). Every human-readable text is a
// LocalizedText ({ "en": "…", "es": "…" }); the interface picks its language
// and falls back to defaultLanguage.

export interface PackageActivity {
  /** Stable reference of the activity; adaptations cite it. */
  entryId: string;
  itemId: string;
  met: number;
  metLabel: string;
  status: EntryStatus;
  /** true = the interface may adapt it according to adaptationPolicy. */
  adaptable: boolean;
  /** Planned end, from the catalog duration; absent if unknown. */
  endMet?: number;
  endMetLabel?: string;
  note?: LocalizedText;
}

/** What the interface may change on its own and how it must report it. */
export interface AdaptationPolicy {
  /** Adaptable categories → block kind within which they may move. */
  windows: Partial<Record<Category, string>>;
  allowed: AdaptationType[];
  rules: LocalizedText[];
  report: { endpoint: string; method: "POST"; blocking: false };
}

/** Adaptation rules, one LocalizedText per rule. */
export function adaptationRules(): LocalizedText[] {
  return DICTIONARIES[DEFAULT_LOCALE].policy.rules.map((_, i) =>
    localized((l) => DICTIONARIES[l].policy.rules[i] ?? DICTIONARIES[DEFAULT_LOCALE].policy.rules[i]),
  );
}

/** Contract consumed by the astronaut interface (offline). */
export interface MissionPackage {
  schema: typeof PACKAGE_SCHEMA;
  packageVersion: number;
  publishedAt: string;
  /** sha256 of the canonical JSON of the package without this field. */
  checksum: string;
  /** Languages every LocalizedText is written in (some texts may lack one). */
  languages: Locale[];
  /** Language to fall back to when a text is missing in the requested one. */
  defaultLanguage: Locale;
  /** true if the prevention plan is an example, not a prescription. */
  simulated: boolean;
  mission: AppState["mission"];
  /** Gravity of each environment relative to Earth. */
  gravityReference: typeof GRAVITY_G;
  crew: AppState["crew"];
  days: (FlightDay & {
    label: string;
    startMetLabel: string;
    endMetLabel: string;
    transitionMetLabel?: string;
  })[];
  events: (MissionEvent & { metLabel: string; day: number | null })[];
  blocks: (TimelineBlock & {
    startMetLabel: string;
    endMetLabel: string;
    /** true = period whose content the interface may adapt. */
    adaptable: boolean;
    adaptableCategories: Category[];
  })[];
  catalog: AppState["catalog"];
  adaptationPolicy: AdaptationPolicy;
  /** crewId → one element per flight day, with activities sorted by MET. */
  plans: Record<string, { day: number; activities: PackageActivity[] }[]>;
  /** Traffic-light capacities in the interface and what to do in amber or red. */
  capacities: AppState["capacities"];
  /** Objective signals; simulated = the interface generates simulated values. */
  signals: AppState["signals"];
  /** Daily questionnaire: fixed block, triggered bank and thresholds. */
  questionnaire: AppState["questionnaire"];
  /** Every threshold in capacities, signals and questionnaire is for demonstration. */
  thresholdsAreDemo: true;
  /** Warnings and information in force when publishing (never blockers). */
  validation: Issue[];
}

export type PackageBody = Omit<MissionPackage, "checksum">;

export function buildPackageBody(
  state: AppState,
  packageVersion: number,
  publishedAt: string,
): PackageBody {
  const categoryOf = new Map(state.catalog.map((c) => [c.id, c.category]));
  const durationOf = new Map(state.catalog.map((c) => [c.id, c.durationMin ?? null]));
  const endOf = (itemId: string, met: number) => {
    const minutes = durationOf.get(itemId);
    return minutes ? { endMet: met + minutes * 60, endMetLabel: formatMet(met + minutes * 60) } : {};
  };
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
          adaptable: e.adaptable && isAdaptableCategory(categoryOf.get(e.itemId) ?? "assessment"),
          ...endOf(e.itemId, e.met),
          ...(e.note ? { note: e.note } : {}),
        })),
    }));
  }
  return {
    schema: PACKAGE_SCHEMA,
    packageVersion,
    publishedAt,
    languages: [...LOCALES],
    defaultLanguage: DEFAULT_LOCALE,
    simulated: state.mission.simulated,
    mission: state.mission,
    gravityReference: GRAVITY_G,
    crew: state.crew,
    days: state.days.map((d) => ({
      ...d,
      label: formatFlightDay(d.day),
      startMetLabel: formatMet(d.startMet),
      endMetLabel: formatMet(d.endMet),
      ...(d.transitionMet !== undefined ? { transitionMetLabel: formatMet(d.transitionMet) } : {}),
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
      allowed: ["substitution", "reschedule", "omission"],
      rules: adaptationRules(),
      report: { endpoint: "/api/adaptations", method: "POST", blocking: false },
    },
    plans,
    capacities: state.capacities,
    signals: state.signals,
    questionnaire: state.questionnaire,
    thresholdsAreDemo: true,
    validation: validate(state),
  };
}

/** JSON with sorted keys and no whitespace, for a reproducible checksum. */
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
