import { createHash } from "crypto";
import { buildPackageBody, canonicalJson, type MissionPackage } from "./package";
import { createSeedState } from "./seed";
import { checkPolicy } from "./adaptations";
import { isLocalizedText } from "./i18n";
import {
  legacyAdaptationType,
  legacyAlertKind,
  legacyAlertLevel,
  legacyAlertStatus,
  legacyEvidenceSource,
  legacyReportSource,
  legacyReviewStatus,
} from "./legacy";
import { getDoc, listDocs, setDoc } from "./storage";
import {
  SCHEMA_VERSION,
  type Adaptation,
  type AdaptationInput,
  type Alert,
  type AlertInput,
  type AlertStatus,
  type AppState,
  type PackageSummary,
  type ReviewStatus,
} from "./types";

// AURA Manager state, packages, adaptations and alerts. Where they are stored
// is decided by lib/storage.ts: files locally, Postgres when deployed.

const readJson = <T>(collection: string, name: string) => getDoc<T>(collection, name);
const STATE = ["state", "current"] as const;

/**
 * Saved state of an older schema. v5 changed every code to English and every
 * text to a multilingual one, so an older state is not migrated: it is
 * replaced by the demo mission (as was already the rule before v4).
 */
function migrate(saved: AppState): AppState | null {
  return saved.schemaVersion === SCHEMA_VERSION ? backfill(saved, createSeedState()) : null;
}

/**
 * Optional fields added to v5 after its release (transition MET, durations,
 * notes of conditional activities, amber actions, critical capacities,
 * resting heart rate). A saved state that lacks them gets the demo value of
 * the same id; anything mission control already set is left untouched.
 * Returns the same object when nothing was missing.
 */
function backfill(saved: AppState, seed: AppState): AppState {
  let changed = false;
  const fill = <T extends object>(list: T[], fn: (x: T) => T): T[] =>
    list.map((x) => {
      const next = fn(x);
      if (next !== x) changed = true;
      return next;
    });

  const seedCrew = new Map(seed.crew.map((c) => [c.id, c]));
  const seedDays = new Map(seed.days.map((d) => [d.day, d]));
  const seedItems = new Map(seed.catalog.map((c) => [c.id, c]));
  const seedCaps = new Map(seed.capacities.map((c) => [c.id, c]));
  const seedPlan = new Map(seed.plan.map((e) => [e.id, e]));

  const next: AppState = {
    ...saved,
    crew: fill(saved.crew, (c) =>
      c.restingHrBpm !== undefined
        ? c
        : { ...c, restingHrBpm: seedCrew.get(c.id)?.restingHrBpm ?? null },
    ),
    days: fill(saved.days, (d) => {
      const s = seedDays.get(d.day);
      if (!d.transitionFrom || d.transitionMet !== undefined || s?.transitionMet === undefined) return d;
      if (s.transitionFrom !== d.transitionFrom || s.transitionMet < d.startMet || s.transitionMet > d.endMet) return d;
      return { ...d, transitionMet: s.transitionMet };
    }),
    catalog: fill(saved.catalog, (c) =>
      c.durationMin !== undefined ? c : { ...c, durationMin: seedItems.get(c.id)?.durationMin ?? null },
    ),
    capacities: fill(saved.capacities, (c) =>
      c.onAmberActions !== undefined && c.criticalFor !== undefined
        ? c
        : {
            ...c,
            onAmberActions: c.onAmberActions ?? seedCaps.get(c.id)?.onAmberActions ?? [],
            criticalFor: c.criticalFor ?? seedCaps.get(c.id)?.criticalFor ?? [],
          },
    ),
    plan: fill(saved.plan, (e) => {
      const note = seedPlan.get(e.id)?.note;
      return e.status === "conditional" && !e.note && note ? { ...e, note } : e;
    }),
  };
  return changed ? next : saved;
}

export async function readState(): Promise<AppState> {
  const saved = await readJson<AppState>(...STATE);
  const migrated = saved ? migrate(saved) : null;
  if (migrated) {
    if (migrated !== saved) await setDoc(...STATE, migrated);
    return migrated;
  }
  // State of an older schema is not migrated: it is replaced by the demo.
  const seed = createSeedState();
  await setDoc(...STATE, seed);
  return seed;
}

export async function writeState(state: AppState): Promise<AppState> {
  const next = { ...state, updatedAt: new Date().toISOString() };
  await setDoc(...STATE, next);
  return next;
}

export async function resetState(): Promise<AppState> {
  const seed = createSeedState();
  await setDoc(...STATE, seed);
  return seed;
}

async function packageVersions(): Promise<number[]> {
  return (await listDocs("packages"))
    .map((name) => /^v(\d+)$/.exec(name)?.[1])
    .filter((v): v is string => v !== undefined)
    .map(Number)
    .sort((a, b) => a - b);
}

export async function readPackage(version?: number): Promise<MissionPackage | null> {
  const versions = await packageVersions();
  const target = version ?? versions.at(-1);
  if (target === undefined || !versions.includes(target)) return null;
  return readJson<MissionPackage>("packages", `v${target}`);
}

export async function listPackages(): Promise<PackageSummary[]> {
  const versions = await packageVersions();
  const packages = await Promise.all(versions.map((v) => readPackage(v)));
  return packages
    .filter((p): p is MissionPackage => p !== null)
    .map((p) => ({
      packageVersion: p.packageVersion,
      publishedAt: p.publishedAt,
      checksum: p.checksum,
      missionName: p.mission.name,
      schema: p.schema,
    }))
    .reverse();
}

export async function publishPackage(state: AppState): Promise<MissionPackage> {
  const versions = await packageVersions();
  const version = (versions.at(-1) ?? 0) + 1;
  const body = buildPackageBody(state, version, new Date().toISOString());
  const checksum =
    "sha256:" + createHash("sha256").update(canonicalJson(body)).digest("hex");
  const pkg: MissionPackage = { ...body, checksum };
  await setDoc("packages", `v${version}`, pkg);
  return pkg;
}

// ---------- Adaptations received from the astronaut interface ----------

export async function latestPackageVersion(): Promise<number | null> {
  return (await packageVersions()).at(-1) ?? null;
}

/**
 * Adaptations recorded before v5 carry Spanish codes and single-language
 * violations: convert the codes and recompute the policy check so its
 * messages exist in every language. Saved back once converted.
 */
async function upgradeAdaptation(a: Adaptation): Promise<Adaptation> {
  const legacy = a.policy.violations.some((v) => !isLocalizedText(v));
  const upgraded: Adaptation = {
    ...a,
    type: legacyAdaptationType(a.type) as Adaptation["type"],
    source: legacyReportSource(a.source) as Adaptation["source"],
    evidence: a.evidence.map((e) => ({
      ...e,
      source: legacyEvidenceSource(e.source) as typeof e.source,
    })),
    review: { ...a.review, status: legacyReviewStatus(a.review.status) as ReviewStatus },
  };
  if (legacy) {
    const { original, policy } = checkPolicy(await readPackage(a.packageVersion), upgraded);
    upgraded.original = original;
    upgraded.policy = policy;
  }
  if (JSON.stringify(upgraded) !== JSON.stringify(a)) await setDoc("adaptations", a.id, upgraded);
  return upgraded;
}

export async function listAdaptations(): Promise<Adaptation[]> {
  const names = await listDocs("adaptations");
  const all = await Promise.all(names.map((n) => readJson<Adaptation>("adaptations", n)));
  const found = all.filter((a): a is Adaptation => a !== null);
  return (await Promise.all(found.map(upgradeAdaptation))).sort((a, b) =>
    a.receivedAt.localeCompare(b.receivedAt),
  );
}

/**
 * Records adaptations. Never rejects them for their content: those outside the
 * policy are flagged for mission control to review.
 * Resending an already recorded adaptation (same id) does not duplicate it.
 */
export async function receiveAdaptations(inputs: AdaptationInput[]): Promise<Adaptation[]> {
  const all = await listAdaptations();
  const result: Adaptation[] = [];
  for (const input of inputs) {
    const existing = all.find((a) => a.id === input.id);
    if (existing) {
      result.push(existing);
      continue;
    }
    const pkg = await readPackage(input.packageVersion);
    const { original, policy } = checkPolicy(pkg, input);
    const adaptation: Adaptation = {
      ...input,
      receivedAt: new Date().toISOString(),
      original,
      policy,
      review: { status: "pending", note: "", reviewedAt: null },
    };
    await setDoc("adaptations", adaptation.id, adaptation);
    all.push(adaptation);
    result.push(adaptation);
  }
  return result;
}

export async function reviewAdaptation(
  id: string,
  status: ReviewStatus,
  note: string,
): Promise<Adaptation | null> {
  const all = await listAdaptations();
  const target = all.find((a) => a.id === id);
  if (!target) return null;
  target.review = {
    status,
    note,
    reviewedAt: status === "pending" ? null : new Date().toISOString(),
  };
  await setDoc("adaptations", target.id, target);
  return target;
}

// ---------- Alerts from the astronaut interface ----------

export async function listAlerts(): Promise<Alert[]> {
  const names = await listDocs("alerts");
  const all = await Promise.all(names.map((n) => readJson<Alert>("alerts", n)));
  const found = all.filter((a): a is Alert => a !== null);
  return (await Promise.all(found.map(upgradeAlert))).sort((a, b) =>
    a.receivedAt.localeCompare(b.receivedAt),
  );
}

/** Alerts recorded before v5 carry Spanish codes: convert and save them back. */
async function upgradeAlert(a: Alert): Promise<Alert> {
  const upgraded: Alert = {
    ...a,
    kind: legacyAlertKind(a.kind) as Alert["kind"],
    level: legacyAlertLevel(a.level) as Alert["level"],
    source: legacyReportSource(a.source) as Alert["source"],
    evidence: a.evidence.map((e) => ({
      ...e,
      source: legacyEvidenceSource(e.source) as typeof e.source,
    })),
    ack: { ...a.ack, status: legacyAlertStatus(a.ack.status) as AlertStatus },
  };
  if (JSON.stringify(upgraded) !== JSON.stringify(a)) await setDoc("alerts", a.id, upgraded);
  return upgraded;
}

/**
 * Records alerts. Never rejects them for their content.
 * Resending an already recorded alert (same id) does not duplicate it.
 */
export async function receiveAlerts(inputs: AlertInput[]): Promise<Alert[]> {
  const all = await listAlerts();
  const result: Alert[] = [];
  for (const input of inputs) {
    const existing = all.find((a) => a.id === input.id);
    if (existing) {
      result.push(existing);
      continue;
    }
    const alert: Alert = {
      ...input,
      receivedAt: new Date().toISOString(),
      ack: { status: "new", note: "", at: null },
    };
    await setDoc("alerts", alert.id, alert);
    all.push(alert);
    result.push(alert);
  }
  return result;
}

export async function acknowledgeAlert(
  id: string,
  status: AlertStatus,
  note: string,
): Promise<Alert | null> {
  const all = await listAlerts();
  const target = all.find((a) => a.id === id);
  if (!target) return null;
  target.ack = { status, note, at: status === "new" ? null : new Date().toISOString() };
  await setDoc("alerts", target.id, target);
  return target;
}

