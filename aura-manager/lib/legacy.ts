// Codes of the v4 contract (Spanish) → v5 codes (English).
//
// The astronaut interface may still send v4 codes while it migrates, and
// adaptations/alerts recorded before v5 are stored with them. The API accepts
// both and always answers and stores v5 codes.

const map = <T extends string>(table: Record<string, T>) =>
  (value: unknown): T | unknown =>
    typeof value === "string" && value in table ? table[value] : value;

export const legacyAdaptationType = map({
  sustitucion: "substitution",
  reprogramacion: "reschedule",
  omision: "omission",
});

export const legacyReviewStatus = map({
  pendiente: "pending",
  validada: "validated",
  objetada: "objected",
});

export const legacyEvidenceSource = map({ simulado: "simulated" });

export const legacyReportSource = map({ interfaz: "interface", simulada: "simulated" });

export const legacyAlertKind = map({
  "capacidad-roja": "capacity-red",
  "preparacion-perdida": "preparation-lost",
  incoherencia: "inconsistency",
});

export const legacyAlertLevel = map({ rojo: "red", aviso: "warning" });

export const legacyAlertStatus = map({ nueva: "new", vista: "seen", atendida: "handled" });
