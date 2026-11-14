import { legacyEvidenceSource } from "./legacy";
import type { AdaptationEvidence, EvidenceSource } from "./types";

const EVIDENCE_SOURCES: EvidenceSource[] = ["manual", "sensor", "simulated"];

export const isString = (v: unknown): v is string => typeof v === "string" && v.trim() !== "";
export const isNumber = (v: unknown): v is number => typeof v === "number" && Number.isFinite(v);

/** Validates the evidence list of an adaptation or alert. Returns the reason if invalid. */
export function parseEvidence(raw: unknown): AdaptationEvidence[] | string {
  const evidence: AdaptationEvidence[] = [];
  for (const item of Array.isArray(raw) ? raw : []) {
    const e = item as Record<string, unknown>;
    if (!isString(e?.indicator) || e.value === undefined || e.value === null) {
      return "Each evidence item needs indicator and value";
    }
    const source = legacyEvidenceSource(e.source);
    evidence.push({
      indicator: e.indicator,
      value: String(e.value),
      ...(isString(e.unit) ? { unit: e.unit } : {}),
      source: EVIDENCE_SOURCES.includes(source as EvidenceSource)
        ? (source as EvidenceSource)
        : "manual",
      ...(isNumber(e.met) ? { met: e.met } : {}),
    });
  }
  return evidence;
}
