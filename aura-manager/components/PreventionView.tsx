"use client";

import { MINUTE, formatFlightDay, formatMet } from "@/lib/met";
import type { AppState, CatalogItem, FlightDay, Issue, PlanEntry } from "@/lib/types";
import { CATEGORIES, flightDayOf, isAdaptableCategory } from "@/lib/types";
import DayTimeline from "./DayTimeline";
import { useI18n } from "./I18n";
import MetInput from "./MetInput";

interface Props {
  state: AppState;
  issues: Issue[];
  selectedDay: number;
  onSelect: (day: number) => void;
  update: (fn: (s: AppState) => AppState) => void;
}

/** Proposed MET for a new activity: half an hour after waking up. */
function defaultMet(day: FlightDay, state: AppState): number {
  const wake = state.blocks
    .filter((b) => b.kind === "sleep" && b.startMet <= day.startMet && b.endMet > day.startMet)
    .map((b) => b.endMet)[0];
  const proposed = (wake ?? day.startMet) + 30 * MINUTE;
  return Math.min(proposed, day.endMet - MINUTE);
}

export default function PreventionView({ state, issues, selectedDay, onSelect, update }: Props) {
  const { t, tx } = useI18n();
  const day = state.days.find((d) => d.day === selectedDay) ?? state.days[0];
  const items = new Map<string, CatalogItem>(state.catalog.map((c) => [c.id, c]));
  const dayIssues = issues.filter((i) => i.day === day.day);
  const inDay = (e: PlanEntry) => flightDayOf(state.days, e.met) === day.day;

  const addEntry = (crewId: string, itemId: string) =>
    update((s) => {
      const entry: PlanEntry = {
        id: crypto.randomUUID(),
        crewId,
        itemId,
        met: defaultMet(day, s),
        status: "authorized",
        adaptable: isAdaptableCategory(
          s.catalog.find((c) => c.id === itemId)?.category ?? "assessment",
        ),
      };
      return { ...s, plan: [...s.plan, entry] };
    });

  const patchEntry = (id: string, patch: Partial<PlanEntry>) =>
    update((s) => ({ ...s, plan: s.plan.map((e) => (e.id === id ? { ...e, ...patch } : e)) }));

  const removeEntry = (id: string) =>
    update((s) => ({ ...s, plan: s.plan.filter((e) => e.id !== id) }));

  const copyFrom = (fromCrewId: string, toCrewId: string) =>
    update((s) => {
      const sameDay = (e: PlanEntry) => flightDayOf(s.days, e.met) === day.day;
      const source = s.plan.filter((e) => e.crewId === fromCrewId && sameDay(e));
      const kept = s.plan.filter((e) => !(e.crewId === toCrewId && sameDay(e)));
      const copied = source.map((e) => ({ ...e, id: crypto.randomUUID(), crewId: toCrewId }));
      return { ...s, plan: [...kept, ...copied] };
    });

  return (
    <div className="prevention">
      <aside className="panel day-list">
        {state.days.map((d) => {
          const flagged = issues.some((i) => i.day === d.day && i.severity !== "info");
          return (
            <button
              key={d.day}
              type="button"
              className={`day-row${d.day === day.day ? " selected" : ""}`}
              onClick={() => onSelect(d.day)}
            >
              <span className="num">{formatFlightDay(d.day)}</span>
              <span className="day-row-text">
                <strong>{tx(d.milestone)}</strong>
                <small>
                  MET {formatMet(d.startMet)} · {t.labels.gravityShort[d.gravity]}
                  {d.transitionFrom ? " ▲" : ""}
                </small>
              </span>
              {flagged ? <i className="dot sev-warning" /> : null}
              <i className={`swatch ol-${d.opLoad}`} title={t.prevention.loadBadge(t.labels.load[d.opLoad])} />
            </button>
          );
        })}
      </aside>

      <div className="stack">
        <section className="panel">
          <div className="day-header">
            <div>
              <span className="eyebrow">
                {formatFlightDay(day.day)} · MET {formatMet(day.startMet)} – {formatMet(day.endMet)}{" "}
                · {tx(day.phase)}
              </span>
              <h2>{tx(day.milestone)}</h2>
            </div>
            <div className="badges">
              <span className={`badge ol-${day.opLoad}`}>{t.prevention.loadBadge(t.labels.load[day.opLoad])}</span>
              <span className="badge outline">
                <i className={`swatch g-${day.gravity}`} />
                {t.labels.gravity[day.gravity]}
              </span>
              {day.transitionFrom ? (
                <span className="badge transition">
                  ▲ {t.labels.gravityShort[day.transitionFrom]} → {t.labels.gravityShort[day.gravity]}
                </span>
              ) : null}
            </div>
          </div>
          {dayIssues.length > 0 ? (
            <ul className="issues">
              {dayIssues.map((issue) => (
                <li key={issue.id} className={`issue sev-${issue.severity}`}>
                  <span className="issue-tag">{t.labels.severity[issue.severity]}</span>
                  <span>
                    {state.crew.find((c) => c.id === issue.crewId)?.name}: {tx(issue.message)}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">{t.prevention.noIssues}</p>
          )}
        </section>

        <DayTimeline state={state} day={day} />

        <div className="crew-columns">
          {state.crew.map((member) => {
            const entries = state.plan
              .filter((e) => e.crewId === member.id && inDay(e))
              .sort((a, b) => a.met - b.met);
            const count = (type: "CL" | "RE") =>
              entries.filter((e) => items.get(e.itemId)?.loadType === type).length;
            const others = state.crew.filter((c) => c.id !== member.id);
            return (
              <section key={member.id} className="panel">
                <div className="panel-head">
                  <div>
                    <h2>{member.name}</h2>
                    <span className="muted">{tx(member.role)}</span>
                  </div>
                  <div className="loads">
                    <span title={t.prevention.clTitle}>CL {count("CL")}</span>
                    <span title={t.prevention.reTitle}>RE {count("RE")}</span>
                  </div>
                </div>

                {entries.length === 0 ? (
                  <p className="muted">{t.prevention.noActivities}</p>
                ) : (
                  <ul className="entries">
                    {entries.map((entry) => {
                      const item = items.get(entry.itemId);
                      if (!item) return null;
                      return (
                        <li key={entry.id} className={`entry cat-${item.category}`}>
                          <MetInput
                            key={entry.met}
                            value={entry.met}
                            ariaLabel={t.common.metOf(tx(item.name))}
                            onCommit={(met) => patchEntry(entry.id, { met })}
                          />
                          <span className="code">{item.code}</span>
                          {isAdaptableCategory(item.category) ? (
                            <button
                              type="button"
                              className={`adapt ${entry.adaptable ? "on" : "off"}`}
                              onClick={() => patchEntry(entry.id, { adaptable: !entry.adaptable })}
                              title={
                                entry.adaptable
                                  ? t.prevention.adaptableOnTitle
                                  : t.prevention.adaptableOffTitle
                              }
                            >
                              {entry.adaptable ? t.common.adaptable : t.common.fixed}
                            </button>
                          ) : (
                            <span
                              className="adapt off static"
                              title={t.prevention.staticFixedTitle}
                            >
                              {t.common.fixed}
                            </span>
                          )}
                          <span className="entry-body">
                            <strong>{tx(item.name)}</strong>
                            <small>
                              {t.labels.category[item.category]}
                              {item.experimental ? ` · ${t.common.experimental}` : ""}
                              {item.category === "supplement"
                                ? t.prevention.dose(item.dose ? tx(item.dose) : t.prevention.dosePending)
                                : ""}
                            </small>
                            <small className="limit">{tx(item.limit)}</small>
                          </span>
                          <button
                            type="button"
                            className={`status ${entry.status}`}
                            onClick={() =>
                              patchEntry(entry.id, {
                                status:
                                  entry.status === "authorized" ? "conditional" : "authorized",
                              })
                            }
                            title={t.prevention.toggleStatus}
                          >
                            {t.labels.entryStatus[entry.status]}
                          </button>
                          <button
                            type="button"
                            className="icon-btn"
                            onClick={() => removeEntry(entry.id)}
                            aria-label={t.common.remove(tx(item.name))}
                          >
                            ×
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}

                <div className="entry-actions">
                  <select
                    value=""
                    onChange={(e) => e.target.value && addEntry(member.id, e.target.value)}
                    aria-label={t.prevention.addTo(member.name)}
                  >
                    <option value="">{t.prevention.addFromCatalog}</option>
                    {CATEGORIES.map((category) => (
                      <optgroup key={category} label={t.labels.category[category]}>
                        {state.catalog
                          .filter((c) => c.category === category)
                          .map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.code} · {tx(c.name)}
                            </option>
                          ))}
                      </optgroup>
                    ))}
                  </select>
                  <select
                    value=""
                    onChange={(e) => e.target.value && copyFrom(e.target.value, member.id)}
                    aria-label={t.prevention.copyTo(member.name)}
                  >
                    <option value="">{t.prevention.copyFrom}</option>
                    {others.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
              </section>
            );
          })}
        </div>
      </div>
    </div>
  );
}
