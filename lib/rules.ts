import { DEFAULT_LOCALE, LOCALES, collectTexts, msg, tr, type Messages } from "./i18n";
import { formatFlightDay, formatMet } from "./met";
import type { AppState, CatalogItem, Issue, LocalizedText, PlanEntry } from "./types";
import { ADAPTABLE_WINDOW, flightDayOf } from "./types";

// Planning rules derived from specification v0.4. They are design rules for
// the demonstrator, not clinical criteria. Every message is a LocalizedText:
// validation warnings travel in the package and are shown in any language.

/** Previous flight days in which a transition preparation counts. */
const TRANSITION_PREP_WINDOW = 2;

export function validate(state: AppState): Issue[] {
  const issues: Issue[] = [];
  const items = new Map<string, CatalogItem>(state.catalog.map((c) => [c.id, c]));
  const dayByNumber = new Map(state.days.map((d) => [d.day, d]));
  const sleepBlocks = state.blocks.filter((b) => b.kind === "sleep");
  const nameOf = (entry: PlanEntry): LocalizedText =>
    items.get(entry.itemId)?.name ?? { [DEFAULT_LOCALE]: entry.itemId };

  const byCrewDay = new Map<string, PlanEntry[]>();
  for (const entry of state.plan) {
    const day = flightDayOf(state.days, entry.met);
    if (day === null) {
      issues.push({
        id: `outside-mission-${entry.id}`,
        rule: "outside-mission",
        severity: "warning",
        day: null,
        crewId: entry.crewId,
        message: msg((m, l) => m.rules.outsideMission(tr(nameOf(entry), l), formatMet(entry.met))),
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
        message: LocalizedText,
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
        push("no-plan", "warning", msg((m) => m.rules.noPlan));
        continue;
      }

      const next = dayByNumber.get(day.day + 1);
      const prev = dayByNumber.get(day.day - 1);

      for (const entry of entries) {
        const item = items.get(entry.itemId);
        if (!item) continue;
        const at = formatMet(entry.met);

        const sleep = sleepBlocks.find((b) => entry.met >= b.startMet && entry.met < b.endMet);
        if (sleep && item.loadType !== "RE") {
          push(
            "during-sleep",
            "warning",
            msg((m, l) =>
              m.rules.inSleep(tr(item.name, l), at, formatMet(sleep.startMet), formatMet(sleep.endMet)),
            ),
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
            "adaptable-outside-window",
            "info",
            msg((m, l) =>
              m.rules.adaptableOutsideWindow(tr(item.name, l), at, m.labels.blockKind[windowKind]),
            ),
            entry.id,
          );
        }

        if (!item.intense) continue;
        const authorized = entry.status === "authorized";
        if (day.opLoad === "critical") {
          push(
            "intense-on-critical",
            authorized ? "blocker" : "warning",
            msg((m, l) => m.rules.intenseOnCritical(tr(item.name, l), tr(day.milestone, l))),
            entry.id,
          );
        } else if (next?.opLoad === "critical") {
          push(
            "intense-on-eve",
            authorized ? "warning" : "info",
            msg((m, l) =>
              m.rules.intenseOnEve(tr(item.name, l), formatFlightDay(next.day), tr(next.milestone, l)),
            ),
            entry.id,
          );
        }
      }

      if (
        prev?.opLoad === "critical" &&
        !entries.some((e) => items.get(e.itemId)?.loadType === "RE")
      ) {
        push(
          "no-recovery",
          "warning",
          msg((m, l) => m.rules.noRecovery(formatFlightDay(prev.day), tr(prev.milestone, l))),
        );
      }

      const from = day.transitionFrom;
      if (from && day.gravity !== "micro") {
        let prepared = false;
        for (let d = day.day; d >= day.day - TRANSITION_PREP_WINDOW; d--) {
          if (entriesFor(member.id, d).some((e) => items.get(e.itemId)?.transitionPrep)) {
            prepared = true;
            break;
          }
        }
        if (!prepared) {
          push(
            "unprepared-transition",
            "warning",
            msg((m) =>
              m.rules.unpreparedTransition(
                m.labels.gravityShort[from],
                m.labels.gravityShort[day.gravity],
                TRANSITION_PREP_WINDOW,
              ),
            ),
          );
        }
      }

      if (!entries.some((e) => items.get(e.itemId)?.selfReport)) {
        push("no-self-report", "info", msg((m) => m.rules.noSelfReport));
      }
    }
  }

  const usedItemIds = new Set(state.plan.map((e) => e.itemId));
  for (const item of state.catalog) {
    if (item.category === "supplement" && item.dose === null && usedItemIds.has(item.id)) {
      issues.push({
        id: `supplement-without-dose-${item.id}`,
        rule: "supplement-without-dose",
        severity: "info",
        day: null,
        crewId: null,
        message: msg((m, l) => m.rules.supplementNoDose(tr(item.name, l))),
      });
    }
  }

  const conditional = state.plan.filter((e) => e.status === "conditional").length;
  if (conditional > 0) {
    issues.push({
      id: "conditionals",
      rule: "conditionals",
      severity: "info",
      day: null,
      crewId: null,
      message: msg((m) => m.rules.conditionals(conditional)),
    });
  }

  issues.push(...validateQuestionnaire(state));
  issues.push(...validateTranslations(state));
  return issues;
}

/**
 * Questionnaire consistency: what the interface could not apply blocks
 * publishing; questions that will never be selected are reported.
 */
function validateQuestionnaire(state: AppState): Issue[] {
  const issues: Issue[] = [];
  const q = state.questionnaire;
  const add = (rule: string, severity: Issue["severity"], message: LocalizedText, suffix: string) =>
    issues.push({ id: `${rule}-${suffix}`, rule, severity, day: null, crewId: null, message });

  const capacityIds = new Set(state.capacities.map((c) => c.id));
  const signalIds = new Set(state.signals.map((s) => s.id));
  const itemIds = new Set(q.items.map((i) => i.id));

  if (!state.catalog.some((c) => c.id === q.catalogItemId)) {
    add("questionnaire", "blocker", msg((m) => m.rules.q.catalogMissing(q.catalogItemId)), "catalog");
  }
  for (const s of state.signals) {
    if (!capacityIds.has(s.capacityId)) {
      add("questionnaire", "blocker", msg((m) => m.rules.q.signalCapacity(s.id, s.capacityId)), `signal-${s.id}`);
    }
  }
  for (const item of q.items) {
    const where = (m: Messages) => m.rules.q.question(item.id);
    if (!capacityIds.has(item.capacityId)) {
      add("questionnaire", "blocker", msg((m) => m.rules.q.capacityMissing(where(m), item.capacityId)), `cap-${item.id}`);
    }
    if (item.variants.length === 0) {
      add("questionnaire", "blocker", msg((m) => m.rules.q.noVariants(where(m))), `var-${item.id}`);
    }
    const inScale = (n: number | null) => n === null || (n >= item.scale.min && n <= item.scale.max);
    if (
      !inScale(item.amber) ||
      !inScale(item.red) ||
      (item.amber !== null && item.red !== null && item.red < item.amber)
    ) {
      add(
        "questionnaire",
        "blocker",
        msg((m) => m.rules.q.thresholds(where(m), item.scale.min, item.scale.max)),
        `threshold-${item.id}`,
      );
    }
    for (const t of item.triggers) {
      if (t.kind === "signal" && !signalIds.has(t.signalId)) {
        add(
          "questionnaire",
          "blocker",
          msg((m) => m.rules.q.triggerSignal(where(m), t.signalId)),
          `trig-${item.id}-${t.signalId}`,
        );
      }
    }
    if (item.block === "bank" && item.triggers.length === 0) {
      add(
        "questionnaire",
        "info",
        msg((m, l) => m.rules.q.neverSelected(where(m), tr(item.domain, l))),
        `never-${item.id}`,
      );
    }
  }
  for (const c of q.consistencyChecks) {
    if (!itemIds.has(c.questionId) || !signalIds.has(c.signalId)) {
      add("questionnaire", "blocker", msg((m) => m.rules.q.checkMissing(c.id)), `check-${c.id}`);
    }
  }
  const fixed = q.items.filter((i) => i.block === "fixed").length;
  if (fixed < q.fixedPerDay) {
    add("questionnaire", "blocker", msg((m) => m.rules.q.fixedShort(q.fixedPerDay, fixed)), "fixed");
  }
  const fillers = q.items.filter((i) => i.triggers.some((t) => t.kind === "filler")).length;
  if (fillers < q.bankPerDay) {
    add("questionnaire", "warning", msg((m) => m.rules.q.fillers(fillers, q.bankPerDay)), "filler");
  }
  return issues;
}

/** Texts with no version in a supported language: informational, never blocks. */
function validateTranslations(state: AppState): Issue[] {
  const texts = collectTexts({ ...state, updatedAt: undefined });
  const issues: Issue[] = [];
  for (const locale of LOCALES) {
    if (locale === DEFAULT_LOCALE) continue;
    const missing = texts.filter((t) => !t[locale] && Object.values(t).some((v) => v)).length;
    if (missing === 0) continue;
    issues.push({
      id: `missing-translation-${locale}`,
      rule: "missing-translation",
      severity: "info",
      day: null,
      crewId: null,
      message: msg((m) => m.rules.missingTranslation(missing, m.languageName[locale])),
    });
  }
  const missingEnglish = texts.filter((t) => !t[DEFAULT_LOCALE] && Object.values(t).some((v) => v)).length;
  if (missingEnglish > 0) {
    issues.push({
      id: `missing-translation-${DEFAULT_LOCALE}`,
      rule: "missing-translation",
      severity: "warning",
      day: null,
      crewId: null,
      message: msg((m) => m.rules.missingEnglish(missingEnglish)),
    });
  }
  return issues;
}

export function countBySeverity(issues: Issue[]) {
  return {
    blocker: issues.filter((i) => i.severity === "blocker").length,
    warning: issues.filter((i) => i.severity === "warning").length,
    info: issues.filter((i) => i.severity === "info").length,
  };
}
