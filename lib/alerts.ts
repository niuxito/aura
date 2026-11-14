import type { AdaptationEvidence, AlertInput, AlertKind, AlertLevel } from "./types";

const KINDS: AlertKind[] = ["capacidad-roja", "preparacion-perdida", "incoherencia"];
const LEVELS: AlertLevel[] = ["rojo", "aviso"];
const EVIDENCE_SOURCES = ["manual", "sensor", "simulado"];

const isString = (v: unknown): v is string => typeof v === "string" && v.trim() !== "";
const isNumber = (v: unknown): v is number => typeof v === "number" && Number.isFinite(v);

/** Valida la forma de una alerta recibida. Devuelve el motivo si no es válida. */
export function parseAlertInput(raw: unknown): AlertInput | string {
  if (!raw || typeof raw !== "object") return "La alerta debe ser un objeto";
  const r = raw as Record<string, unknown>;
  if (!isString(r.id)) return "Falta id";
  if (!isNumber(r.packageVersion)) return "Falta packageVersion";
  if (!isString(r.crewId)) return "Falta crewId";
  if (!KINDS.includes(r.kind as AlertKind)) return "kind no válido";
  if (!LEVELS.includes(r.level as AlertLevel)) return "level no válido";
  if (!isNumber(r.met)) return "Falta met";
  if (!isString(r.message)) return "Falta message: toda alerta dice qué ha pasado";

  const evidence: AdaptationEvidence[] = [];
  for (const item of Array.isArray(r.evidence) ? r.evidence : []) {
    const e = item as Record<string, unknown>;
    if (!isString(e?.indicator) || e.value === undefined || e.value === null) {
      return "Cada evidencia necesita indicator y value";
    }
    evidence.push({
      indicator: e.indicator,
      value: String(e.value),
      ...(isString(e.unit) ? { unit: e.unit } : {}),
      source: EVIDENCE_SOURCES.includes(e.source as string)
        ? (e.source as AdaptationEvidence["source"])
        : "manual",
      ...(isNumber(e.met) ? { met: e.met } : {}),
    });
  }

  return {
    id: r.id,
    packageVersion: r.packageVersion,
    crewId: r.crewId,
    kind: r.kind as AlertKind,
    level: r.level as AlertLevel,
    ...(isString(r.capacityId) ? { capacityId: r.capacityId } : {}),
    ...(isString(r.entryId) ? { entryId: r.entryId } : {}),
    met: r.met,
    message: r.message.trim(),
    evidence,
    source: r.source === "simulada" ? "simulada" : "interfaz",
  };
}
