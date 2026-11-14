import { formatFlightDay, formatMet } from "./met";
import type { AppState, CatalogItem, Issue, PlanEntry } from "./types";
import { ADAPTABLE_WINDOW, BLOCK_KIND_LABEL, GRAVITY_SHORT, flightDayOf } from "./types";

// Reglas de planificación derivadas de la especificación v0.4. Son reglas de
// diseño para el demostrador, no criterios clínicos.

/** Días de vuelo previos en los que una preparación de transición cuenta. */
const TRANSITION_PREP_WINDOW = 2;

export function validate(state: AppState): Issue[] {
  const issues: Issue[] = [];
  const items = new Map<string, CatalogItem>(state.catalog.map((c) => [c.id, c]));
  const dayByNumber = new Map(state.days.map((d) => [d.day, d]));
  const sleepBlocks = state.blocks.filter((b) => b.kind === "sueno");

  const byCrewDay = new Map<string, PlanEntry[]>();
  for (const entry of state.plan) {
    const day = flightDayOf(state.days, entry.met);
    if (day === null) {
      issues.push({
        id: `fuera-de-mision-${entry.id}`,
        rule: "fuera-de-mision",
        severity: "aviso",
        day: null,
        crewId: entry.crewId,
        message: `${items.get(entry.itemId)?.name ?? entry.itemId} en MET ${formatMet(entry.met)}: fuera de los días de vuelo de la misión.`,
      });
      continue;
    }
    const key = `${entry.crewId}:${day}`;
    const list = byCrewDay.get(key);
    if (list) list.push(entry);
    else byCrewDay.set(key, [entry]);
  }
  const entriesFor = (crewId: string, day: number) =>
    byCrewDay.get(`${crewId}:${day}`) ?? [];

  for (const member of state.crew) {
    for (const day of state.days) {
      const entries = entriesFor(member.id, day.day);
      const push = (
        rule: string,
        severity: Issue["severity"],
        message: string,
        suffix = "",
      ) =>
        issues.push({
          id: `${rule}-${day.day}-${member.id}-${suffix}`,
          rule,
          severity,
          day: day.day,
          crewId: member.id,
          message,
        });

      if (entries.length === 0) {
        push("sin-plan", "aviso", "Sin plan de prevención asignado para este día de vuelo.");
        continue;
      }

      const next = dayByNumber.get(day.day + 1);
      const prev = dayByNumber.get(day.day - 1);

      for (const entry of entries) {
        const item = items.get(entry.itemId);
        if (!item) continue;

        const sleep = sleepBlocks.find((b) => entry.met >= b.startMet && entry.met < b.endMet);
        if (sleep && item.loadType !== "RE") {
          push(
            "en-sueno",
            "aviso",
            `${item.name} en MET ${formatMet(entry.met)}: cae dentro del periodo de sueño (${formatMet(sleep.startMet)} – ${formatMet(sleep.endMet)}).`,
            entry.id,
          );
        }

        const windowKind = ADAPTABLE_WINDOW[item.category];
        if (
          entry.adaptable &&
          windowKind &&
          !state.blocks.some(
            (b) => b.kind === windowKind && entry.met >= b.startMet && entry.met < b.endMet,
          )
        ) {
          push(
            "adaptable-fuera-de-periodo",
            "info",
            `${item.name} en MET ${formatMet(entry.met)}: es adaptable pero no está dentro de un bloque de «${BLOCK_KIND_LABEL[windowKind]}»; la interfaz podrá sustituirla u omitirla, no moverla.`,
            entry.id,
          );
        }

        if (!item.intense) continue;
        const authorized = entry.status === "autorizado";
        if (day.co === "critica") {
          push(
            "intenso-en-critico",
            authorized ? "bloqueo" : "aviso",
            `${item.name}: no se añade carga exigente a un día de carga operativa crítica (${day.milestone}).`,
            entry.id,
          );
        } else if (next?.co === "critica") {
          push(
            "intenso-en-vispera",
            authorized ? "aviso" : "info",
            `${item.name} en la víspera de ${formatFlightDay(next.day)} («${next.milestone}»): proteger el descanso previo.`,
            entry.id,
          );
        }
      }

      if (
        prev?.co === "critica" &&
        !entries.some((e) => items.get(e.itemId)?.loadType === "RE")
      ) {
        push(
          "sin-recuperacion",
          "aviso",
          `Sin ventana de recuperación tras ${formatFlightDay(prev.day)} («${prev.milestone}»).`,
        );
      }

      if (day.transitionFrom && day.gravity !== "micro") {
        let prepared = false;
        for (let d = day.day; d >= day.day - TRANSITION_PREP_WINDOW; d--) {
          if (entriesFor(member.id, d).some((e) => items.get(e.itemId)?.transitionPrep)) {
            prepared = true;
            break;
          }
        }
        if (!prepared) {
          push(
            "transicion-sin-preparar",
            "aviso",
            `Transición ${GRAVITY_SHORT[day.transitionFrom]} → ${GRAVITY_SHORT[day.gravity]} sin preparación gravitatoria en los ${TRANSITION_PREP_WINDOW} días de vuelo previos.`,
          );
        }
      }

      if (!entries.some((e) => items.get(e.itemId)?.selfReport)) {
        push(
          "sin-autoinforme",
          "info",
          "Sin cuestionario de fatiga: el monitor mostrará gris (datos insuficientes).",
        );
      }
    }
  }

  const usedItemIds = new Set(state.plan.map((e) => e.itemId));
  for (const item of state.catalog) {
    if (item.category === "suplemento" && item.dose === null && usedItemIds.has(item.id)) {
      issues.push({
        id: `suplemento-sin-dosis-${item.id}`,
        rule: "suplemento-sin-dosis",
        severity: "info",
        day: null,
        crewId: null,
        message: `${item.name}: dosis pendiente de protocolo médico; el paquete lo publica sin dosis.`,
      });
    }
  }

  const conditional = state.plan.filter((e) => e.status === "condicional").length;
  if (conditional > 0) {
    issues.push({
      id: "condicionales",
      rule: "condicionales",
      severity: "info",
      day: null,
      crewId: null,
      message: `${conditional} actividades condicionales: dependen de confirmar equipo, protocolo o recuperación.`,
    });
  }

  return issues;
}

export function countBySeverity(issues: Issue[]) {
  return {
    bloqueo: issues.filter((i) => i.severity === "bloqueo").length,
    aviso: issues.filter((i) => i.severity === "aviso").length,
    info: issues.filter((i) => i.severity === "info").length,
  };
}
