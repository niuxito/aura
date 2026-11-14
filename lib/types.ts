// AURA Manager data model (mission control console).
// All times are MET (Mission Elapsed Time) in seconds; see lib/met.ts.
// Every human-readable text is a LocalizedText (see lib/i18n/text.ts); codes
// and identifiers are English and never translated.

import type { LocalizedText } from "./i18n/text";

export type { LocalizedText } from "./i18n/text";

/** Changes when the saved state is no longer compatible. */
export const SCHEMA_VERSION = 5;

export type Gravity = "earth" | "micro" | "moon" | "mars";
export type LoadLevel = "low" | "medium" | "high" | "critical";
export type Category =
  | "exercise"
  | "nutrition"
  | "assessment"
  | "supplement"
  | "recovery"
  | "preparation";
/** CL = conditioning load, RE = recovery. */
export type LoadType = "CL" | "RE" | "none";
export type EntryStatus = "authorized" | "conditional";
/** eva = extravehicular activity (going outside or onto the surface). */
export type EventKind = "maneuver" | "milestone" | "test" | "operation" | "eva";
export type BlockKind =
  | "sleep"
  | "presleep"
  | "meal"
  | "exercise"
  | "maneuver"
  | "test"
  | "operation"
  | "communication"
  | "health"
  | "off-duty";

export interface Mission {
  id: string;
  name: LocalizedText;
  destination: LocalizedText;
  description: LocalizedText;
  /** UTC instant of MET 00/00:00:00 (launch). null = not set. */
  launchUtc: string | null;
  /** Document the timeline comes from (a citation, not translated). */
  timelineSource: string;
  /** true while the prevention plan is a team example. */
  simulated: boolean;
}

export interface CrewMember {
  id: string;
  /** Person or call sign: not translated. */
  name: string;
  role: LocalizedText;
}

/** Flight day (FD). Not a calendar day: the timeline defines it. */
export interface FlightDay {
  day: number;
  startMet: number;
  endMet: number;
  phase: LocalizedText;
  milestone: LocalizedText;
  /** Gravity environment at the end of the flight day. */
  gravity: Gravity;
  /** If present, there is a gravity transition from this environment that day. */
  transitionFrom?: Gravity;
  /** OL: operational load estimated by the team. */
  opLoad: LoadLevel;
}

/** Point event in the mission timeline. */
export interface MissionEvent {
  id: string;
  met: number;
  name: LocalizedText;
  kind: EventKind;
  /** The timeline gives it as approximate (~). */
  approx: boolean;
}

/** Crew activity block in the mission timeline. */
export interface TimelineBlock {
  id: string;
  startMet: number;
  endMet: number;
  name: LocalizedText;
  kind: BlockKind;
}

export interface CatalogItem {
  id: string;
  code: string;
  name: LocalizedText;
  category: Category;
  loadType: LoadType;
  /** Demanding load: must not fall on critical days or on the day before one. */
  intense: boolean;
  /** Prepares a gravity transition (landing, return to Earth). */
  transitionPrep: boolean;
  /** Fatigue/sleep/symptom self-report; without it the monitor shows gray. */
  selfReport: boolean;
  capabilities: LocalizedText[];
  /** What the astronaut monitor records. */
  record: LocalizedText;
  /** Limit of use or interpretation. */
  limit: LocalizedText;
  /** Prescribed dose or range. null = pending an authorized protocol. */
  dose: LocalizedText | null;
  source: LocalizedText;
  sourceUrl?: string;
  experimental: boolean;
}

/** Prevention activity scheduled at a MET for a crew member. */
export interface PlanEntry {
  id: string;
  crewId: string;
  itemId: string;
  /** Start MET. The flight day is derived from it. */
  met: number;
  status: EntryStatus;
  /**
   * true = the astronaut interface may adapt it (substitute it, move it within
   * its window or omit it). false = fixed: only mission control changes it.
   * Only has an effect in adaptable categories; see ADAPTABLE_WINDOW.
   */
  adaptable: boolean;
  note?: LocalizedText;
}

/**
 * What the astronaut interface may adapt and in which kind of timeline block:
 * exercise within exercise periods and diet at meals. Everything else
 * (maneuvers, tests, sleep, operations) is fixed.
 */
export const ADAPTABLE_WINDOW: Partial<Record<Category, BlockKind>> = {
  exercise: "exercise",
  nutrition: "meal",
};

export function isAdaptableCategory(category: Category): boolean {
  return category in ADAPTABLE_WINDOW;
}

/** Categories the interface may adapt within a block of this kind. */
export function adaptableCategoriesOf(kind: BlockKind): Category[] {
  return (Object.keys(ADAPTABLE_WINDOW) as Category[]).filter(
    (c) => ADAPTABLE_WINDOW[c] === kind,
  );
}

// ---------- Adaptations decided by the astronaut interface ----------

export type AdaptationType = "substitution" | "reschedule" | "omission";
export type ReviewStatus = "pending" | "validated" | "objected";
export type EvidenceSource = "manual" | "sensor" | "simulated";
export type ReportSource = "interface" | "simulated";

/** Free text sent by the interface stays as written (in the astronaut's language). */
export interface AdaptationEvidence {
  indicator: string;
  value: string;
  unit?: string;
  source: EvidenceSource;
  met?: number;
}

/** What the interface sends. Already applied on board: sending does not block. */
export interface AdaptationInput {
  /** Generated by the interface; allows resending without duplicates. */
  id: string;
  packageVersion: number;
  crewId: string;
  /** Plan activity it affects (activities[].entryId in the package). */
  entryId: string;
  type: AdaptationType;
  /** Substitutions only. */
  replacementItemId?: string;
  /** Reschedules only. */
  newMet?: number;
  /** MET at which the interface made the decision. */
  decidedAtMet: number;
  reason: string;
  evidence: AdaptationEvidence[];
  source: ReportSource;
}

/** Adaptation recorded in mission control. */
export interface Adaptation extends AdaptationInput {
  receivedAt: string;
  /** Original activity according to the package; null if not found. */
  original: { itemId: string; met: number } | null;
  /** Automatic check against the package policy. Never rejects. */
  policy: { withinPolicy: boolean; violations: LocalizedText[] };
  review: { status: ReviewStatus; note: string; reviewedAt: string | null };
}

// ---------- Alerts from the astronaut interface ----------
//
// What the interface does not resolve by adapting the plan and mission control
// must know: a capacity in red, an adaptation that removes a transition
// preparation measure, or an inconsistency between self-report and sensor.
// Like adaptations, they are sent without blocking and the server never
// rejects them for their content.

export type AlertKind = "capacity-red" | "preparation-lost" | "inconsistency";
export type AlertLevel = "red" | "warning";
export type AlertStatus = "new" | "seen" | "handled";

export interface AlertInput {
  /** Generated by the interface; allows resending without duplicates. */
  id: string;
  packageVersion: number;
  crewId: string;
  kind: AlertKind;
  level: AlertLevel;
  /** Affected capacity (capacities[].id), if any. */
  capacityId?: string;
  /** Affected plan activity (activities[].entryId), if any. */
  entryId?: string;
  /** MET at which the interface raised the alert. */
  met: number;
  /** Text the astronaut saw, as written. */
  message: string;
  evidence: AdaptationEvidence[];
  source: ReportSource;
}

/** Alert recorded in mission control, with its acknowledgement. */
export interface Alert extends AlertInput {
  receivedAt: string;
  ack: { status: AlertStatus; note: string; at: string | null };
}

// ---------- Self-assessment: capacities, signals and daily questionnaire ----------
//
// Defined by mission control and published in the package; the astronaut
// interface applies it. Mission-agnostic: triggers are expressed with gravity
// environments, flight days and event kinds.
// All thresholds are for demonstration, not clinical criteria.

/** State of a capacity in the interface. gray = insufficient data. */
export type CapacityLevel = "green" | "amber" | "red" | "gray";

/** Capacity shown with a traffic light in the astronaut interface. */
export interface Capacity {
  id: string;
  name: LocalizedText;
  description: LocalizedText;
  /** What the interface does in amber, always within adaptationPolicy. */
  onAmber: LocalizedText;
  /** What the interface does in red: refers and alerts; does not decide on its own. */
  onRed: LocalizedText;
}

/** Where an objective signal comes from. */
export type SignalSource = "watch" | "garment" | "test";

/** Objective measure the interface may have and how it is graded. */
export interface Signal {
  id: string;
  name: LocalizedText;
  /** Unit symbol (bpm, %): not translated. */
  unit: string;
  source: SignalSource;
  /**
   * true = no real device in the demo: the interface generates simulated
   * values, stored and shown as "simulated".
   */
  simulated: boolean;
  capacityId: string;
  kind: "number" | "boolean";
  /** Numeric only: which direction is worse. */
  worseWhen?: "higher" | "lower";
  /** Numeric only: from which value (inclusive) it turns amber or red. null = n/a. */
  amber?: number | null;
  red?: number | null;
  /** Boolean only: level when the signal is true. */
  whenTrue?: "amber" | "red";
  description: LocalizedText;
}

export interface QuestionScale {
  min: number;
  max: number;
}

export interface QuestionVariant {
  id: string;
  text: LocalizedText;
  /** Labels of the scale ends, as shown. */
  minLabel: LocalizedText;
  maxLabel: LocalizedText;
  /**
   * true = phrased positively (high = better). The interface normalizes it
   * with (max + min) − value so that, across the system, high = worse.
   */
  inverted: boolean;
}

/** When a bank question enters the day's questionnaire. */
export type QuestionTrigger = { priority: number; reason: LocalizedText } & (
  | {
      /** Flight days before or after one with a gravity transition. */
      kind: "transition";
      daysBefore: number;
      daysAfter: number;
      /** Only transitions into these environments. Empty = any. */
      toGravity: Gravity[];
      /** Only after at least this many consecutive flight days in microgravity. */
      minDaysInMicro?: number;
    }
  | { kind: "environment"; gravity: Gravity[] }
  | { kind: "micro-days"; min: number }
  | { kind: "event"; eventKind: EventKind; withinDays: number }
  | {
      kind: "signal";
      signalId: string;
      /** For booleans: fires when true; op and value are ignored. */
      op?: ">=" | "<=";
      value?: number;
    }
  /** Filler when there are not enough triggered questions. */
  | { kind: "filler" }
);

export interface QuestionItem {
  id: string;
  /** fixed = block A, every day; bank = block B, by triggers. */
  block: "fixed" | "bank";
  domain: LocalizedText;
  capacityId: string;
  scale: QuestionScale;
  /** Instrument it is inspired by. A single question is not the validated instrument. */
  inspiredBy: LocalizedText;
  variants: QuestionVariant[];
  /** On the normalized value (high = worse), inclusive. null = n/a. */
  amber: number | null;
  red: number | null;
  triggers: QuestionTrigger[];
}

/** Consistency between self-report and sensor: detects under-reporting. */
export interface ConsistencyCheck {
  id: string;
  questionId: string;
  /** Normalized answer at or below this = the astronaut says they are fine. */
  answerAtMost: number;
  signalId: string;
  op: ">=" | "<=";
  value: number;
  message: LocalizedText;
}

export interface Questionnaire {
  /** Catalog action that schedules it in the plan. */
  catalogItemId: string;
  name: LocalizedText;
  source: LocalizedText;
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

export type Severity = "blocker" | "warning" | "info";

export interface Issue {
  id: string;
  rule: string;
  severity: Severity;
  /** Flight day. null = affects the whole mission. */
  day: number | null;
  crewId: string | null;
  message: LocalizedText;
}

export interface PackageSummary {
  packageVersion: number;
  publishedAt: string;
  checksum: string;
  /** Older packages (schema v4 and earlier) carry a plain string. */
  missionName: LocalizedText | string;
  schema: string;
}

/** Flight day a MET belongs to, or null if it falls outside the mission. */
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
  earth: 1,
  mars: 0.38,
  moon: 0.165,
  micro: 0,
};

export const GRAVITIES: Gravity[] = ["earth", "micro", "moon", "mars"];
export const LOAD_LEVELS: LoadLevel[] = ["low", "medium", "high", "critical"];
export const EVENT_KINDS: EventKind[] = ["maneuver", "milestone", "test", "operation", "eva"];

export const CATEGORIES: Category[] = [
  "exercise",
  "nutrition",
  "assessment",
  "preparation",
  "recovery",
  "supplement",
];
