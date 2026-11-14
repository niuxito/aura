import { formatMet } from "./met";
import { PACKAGE_SCHEMA, type MissionPackage } from "./package";
import type { Adaptation, AdaptationEvidence, AdaptationInput, AdaptationType } from "./types";

const TYPES: AdaptationType[] = ["sustitucion", "reprogramacion", "omision"];
const EVIDENCE_SOURCES = ["manual", "sensor", "simulado"];

const isString = (v: unknown): v is string => typeof v === "string" && v.trim() !== "";
const isNumber = (v: unknown): v is number => typeof v === "number" && Number.isFinite(v);

/** Valida la forma de una adaptación recibida. Devuelve el motivo si no es válida. */
export function parseAdaptationInput(raw: unknown): AdaptationInput | string {
  if (!raw || typeof raw !== "object") return "La adaptación debe ser un objeto";
  const r = raw as Record<string, unknown>;
  if (!isString(r.id)) return "Falta id";
  if (!isNumber(r.packageVersion)) return "Falta packageVersion";
  if (!isString(r.crewId)) return "Falta crewId";
  if (!isString(r.entryId)) return "Falta entryId";
  if (!TYPES.includes(r.type as AdaptationType)) return "type no válido";
  if (!isNumber(r.decidedAtMet)) return "Falta decidedAtMet";
  if (!isString(r.reason)) return "Falta reason: toda adaptación lleva su motivo";
  const type = r.type as AdaptationType;
  if (type === "sustitucion" && !isString(r.replacementItemId)) {
    return "Una sustitución necesita replacementItemId";
  }
  if (type === "reprogramacion" && !isNumber(r.newMet)) {
    return "Una reprogramación necesita newMet";
  }

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
    entryId: r.entryId,
    type,
    ...(type === "sustitucion" ? { replacementItemId: r.replacementItemId as string } : {}),
    ...(type === "reprogramacion" ? { newMet: r.newMet as number } : {}),
    decidedAtMet: r.decidedAtMet,
    reason: r.reason.trim(),
    evidence,
    source: r.source === "simulada" ? "simulada" : "interfaz",
  };
}

/**
 * Comprueba una adaptación contra la política del paquete con el que trabajaba
 * la interfaz. Es informativa: una adaptación fuera de política se registra
 * igualmente y queda señalada para la revisión de control de misión.
 */
export function checkPolicy(
  pkg: MissionPackage | null,
  input: AdaptationInput,
): Pick<Adaptation, "original" | "policy"> {
  const fail = (violation: string, original: Adaptation["original"] = null) => ({
    original,
    policy: { withinPolicy: false, violations: [violation] },
  });

  if (!pkg) return fail(`No existe el paquete v${input.packageVersion}.`);
  if (pkg.schema !== PACKAGE_SCHEMA) {
    return fail(`El paquete v${input.packageVersion} es de un esquema anterior, sin política de adaptación.`);
  }

  const days = pkg.plans[input.crewId];
  if (!days) return fail("El tripulante no figura en el paquete.");
  const day = days.find((d) => d.activities.some((a) => a.entryId === input.entryId));
  const activity = day?.activities.find((a) => a.entryId === input.entryId);
  if (!day || !activity) return fail("La actividad no figura en el plan de ese tripulante.");

  const original = { itemId: activity.itemId, met: activity.met };
  const item = pkg.catalog.find((c) => c.id === activity.itemId);
  const violations: string[] = [];

  if (!activity.adaptable) {
    violations.push("La actividad está marcada como fija: solo puede cambiarla control de misión.");
  }

  if (input.type === "sustitucion") {
    const replacement = pkg.catalog.find((c) => c.id === input.replacementItemId);
    if (!replacement) {
      violations.push("La acción sustituta no existe en el catálogo del paquete.");
    } else {
      if (item && replacement.category !== item.category) {
        violations.push("La acción sustituta es de otra categoría.");
      }
      if (item && replacement.intense && !item.intense) {
        violations.push("La sustitución aumenta la carga: introduce una acción exigente.");
      }
    }
  }

  if (input.type === "reprogramacion" && input.newMet !== undefined) {
    const newMet = input.newMet;
    const fd = pkg.days.find((d) => d.day === day.day);
    if (!fd || newMet < fd.startMet || newMet >= fd.endMet) {
      violations.push(`El nuevo MET ${formatMet(newMet)} cae fuera del día de vuelo original.`);
    }
    const inWindow = pkg.blocks.some(
      (b) =>
        item !== undefined &&
        b.adaptableCategories.includes(item.category) &&
        newMet >= b.startMet &&
        newMet < b.endMet,
    );
    if (!inWindow) {
      violations.push(
        `El nuevo MET ${formatMet(newMet)} no está dentro de un periodo adaptable de su categoría.`,
      );
    }
  }

  return { original, policy: { withinPolicy: violations.length === 0, violations } };
}
