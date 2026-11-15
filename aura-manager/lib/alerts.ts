import { isNumber, isString, parseEvidence } from "./evidence";
import { legacyAlertKind, legacyAlertLevel, legacyReportSource } from "./legacy";
import type { AlertInput, AlertKind, AlertLevel } from "./types";

const KINDS: AlertKind[] = ["capacity-red", "preparation-lost", "inconsistency"];
const LEVELS: AlertLevel[] = ["red", "warning"];

/**
 * Validates the shape of a received alert. Returns the reason if invalid.
 * Accepts v4 (Spanish) codes and converts them to v5.
 */
export function parseAlertInput(raw: unknown): AlertInput | string {
  if (!raw || typeof raw !== "object") return "The alert must be an object";
  const r = raw as Record<string, unknown>;
  const kind = legacyAlertKind(r.kind) as AlertKind;
  const level = legacyAlertLevel(r.level) as AlertLevel;
  if (!isString(r.id)) return "Missing id";
  if (!isNumber(r.packageVersion)) return "Missing packageVersion";
  if (!isString(r.crewId)) return "Missing crewId";
  if (!KINDS.includes(kind)) return "Invalid kind";
  if (!LEVELS.includes(level)) return "Invalid level";
  if (!isNumber(r.met)) return "Missing met";
  if (!isString(r.message)) return "Missing message: every alert says what happened";

  const evidence = parseEvidence(r.evidence);
  if (typeof evidence === "string") return evidence;

  return {
    id: r.id,
    packageVersion: r.packageVersion,
    crewId: r.crewId,
    kind,
    level,
    ...(isString(r.capacityId) ? { capacityId: r.capacityId } : {}),
    ...(isString(r.entryId) ? { entryId: r.entryId } : {}),
    met: r.met,
    message: r.message.trim(),
    evidence,
    source: legacyReportSource(r.source) === "simulated" ? "simulated" : "interface",
  };
}
