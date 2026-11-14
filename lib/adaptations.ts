import { isNumber, isString, parseEvidence } from "./evidence";
import { msg, type LocalizedText } from "./i18n";
import { legacyAdaptationType, legacyReportSource } from "./legacy";
import { formatMet } from "./met";
import { ADAPTATION_SCHEMAS, type MissionPackage } from "./package";
import type { Adaptation, AdaptationInput, AdaptationType } from "./types";

const TYPES: AdaptationType[] = ["substitution", "reschedule", "omission"];

/**
 * Validates the shape of a received adaptation. Returns the reason if invalid.
 * Accepts v4 (Spanish) codes and converts them to v5.
 */
export function parseAdaptationInput(raw: unknown): AdaptationInput | string {
  if (!raw || typeof raw !== "object") return "The adaptation must be an object";
  const r = raw as Record<string, unknown>;
  const type = legacyAdaptationType(r.type) as AdaptationType;
  if (!isString(r.id)) return "Missing id";
  if (!isNumber(r.packageVersion)) return "Missing packageVersion";
  if (!isString(r.crewId)) return "Missing crewId";
  if (!isString(r.entryId)) return "Missing entryId";
  if (!TYPES.includes(type)) return "Invalid type";
  if (!isNumber(r.decidedAtMet)) return "Missing decidedAtMet";
  if (!isString(r.reason)) return "Missing reason: every adaptation carries its reason";
  if (type === "substitution" && !isString(r.replacementItemId)) {
    return "A substitution needs replacementItemId";
  }
  if (type === "reschedule" && !isNumber(r.newMet)) {
    return "A reschedule needs newMet";
  }

  const evidence = parseEvidence(r.evidence);
  if (typeof evidence === "string") return evidence;

  return {
    id: r.id,
    packageVersion: r.packageVersion,
    crewId: r.crewId,
    entryId: r.entryId,
    type,
    ...(type === "substitution" ? { replacementItemId: r.replacementItemId as string } : {}),
    ...(type === "reschedule" ? { newMet: r.newMet as number } : {}),
    decidedAtMet: r.decidedAtMet,
    reason: r.reason.trim(),
    evidence,
    source: legacyReportSource(r.source) === "simulated" ? "simulated" : "interface",
  };
}

/**
 * Checks an adaptation against the policy of the package the interface was
 * working with. It is informative: an out-of-policy adaptation is recorded
 * anyway and flagged for mission control review. Works with v3–v5 packages:
 * it only compares codes within the same package.
 */
export function checkPolicy(
  pkg: MissionPackage | null,
  input: AdaptationInput,
): Pick<Adaptation, "original" | "policy"> {
  const fail = (violation: LocalizedText, original: Adaptation["original"] = null) => ({
    original,
    policy: { withinPolicy: false, violations: [violation] },
  });

  if (!pkg) return fail(msg((m) => m.policy.packageMissing(input.packageVersion)));
  if (!ADAPTATION_SCHEMAS.includes(pkg.schema)) {
    return fail(msg((m) => m.policy.oldSchema(input.packageVersion)));
  }

  const days = pkg.plans[input.crewId];
  if (!days) return fail(msg((m) => m.policy.crewMissing));
  const day = days.find((d) => d.activities.some((a) => a.entryId === input.entryId));
  const activity = day?.activities.find((a) => a.entryId === input.entryId);
  if (!day || !activity) return fail(msg((m) => m.policy.activityMissing));

  const original = { itemId: activity.itemId, met: activity.met };
  const item = pkg.catalog.find((c) => c.id === activity.itemId);
  const violations: LocalizedText[] = [];

  if (!activity.adaptable) violations.push(msg((m) => m.policy.fixed));

  if (input.type === "substitution") {
    const replacement = pkg.catalog.find((c) => c.id === input.replacementItemId);
    if (!replacement) {
      violations.push(msg((m) => m.policy.replacementMissing));
    } else {
      if (item && replacement.category !== item.category) {
        violations.push(msg((m) => m.policy.otherCategory));
      }
      if (item && replacement.intense && !item.intense) {
        violations.push(msg((m) => m.policy.increasesLoad));
      }
    }
  }

  if (input.type === "reschedule" && input.newMet !== undefined) {
    const newMet = input.newMet;
    const at = formatMet(newMet);
    const fd = pkg.days.find((d) => d.day === day.day);
    if (!fd || newMet < fd.startMet || newMet >= fd.endMet) {
      violations.push(msg((m) => m.policy.outsideDay(at)));
    }
    const inWindow = pkg.blocks.some(
      (b) =>
        item !== undefined &&
        b.adaptableCategories.includes(item.category) &&
        newMet >= b.startMet &&
        newMet < b.endMet,
    );
    if (!inWindow) violations.push(msg((m) => m.policy.outsideWindow(at)));
  }

  return { original, policy: { withinPolicy: violations.length === 0, violations } };
}
