"use client";

import { useEffect, useState } from "react";
import { formatFlightDay, formatMet } from "@/lib/met";
import type { Alert, AlertStatus, AppState } from "@/lib/types";
import { flightDayOf } from "@/lib/types";
import { useI18n } from "./I18n";

interface Props {
  state: AppState;
  alerts: Alert[];
  refresh: () => Promise<void>;
}

/** Unhandled red ones first; within each group, the most recent on top. */
function order(a: Alert, b: Alert): number {
  const rank = (x: Alert) => (x.ack.status === "handled" ? 2 : x.level === "red" ? 0 : 1);
  return rank(a) - rank(b) || b.receivedAt.localeCompare(a.receivedAt);
}

export default function AlertsView({ state, alerts, refresh }: Props) {
  const { t, tx, formatDate } = useI18n();
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const crewName = (id: string) => state.crew.find((c) => c.id === id)?.name ?? id;
  const capacityName = (id?: string) => {
    const capacity = state.capacities.find((c) => c.id === id);
    return capacity ? tx(capacity.name) : id;
  };

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const acknowledge = async (a: Alert, status: AlertStatus) => {
    setError(null);
    const res = await fetch("/api/alerts", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id: a.id, status, note: notes[a.id] ?? a.ack.note }),
    });
    if (!res.ok) setError(t.alerts.saveError);
    await refresh();
  };

  const count = (s: AlertStatus) => alerts.filter((a) => a.ack.status === s).length;
  const openRed = alerts.filter((a) => a.level === "red" && a.ack.status !== "handled").length;

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>{t.alerts.title}</h2>
          <div className="counts">
            <span className="count sev-blocker">{t.alerts.openRed(openRed)}</span>
            <span className="count sev-warning">{t.alerts.new(count("new"))}</span>
            <span className="count">{t.alerts.seen(count("seen"))}</span>
            <span className="count">{t.alerts.handled(count("handled"))}</span>
          </div>
        </div>
        <p className="muted">{t.alerts.intro}</p>
        {error ? <p className="error">{error}</p> : null}

        {alerts.length === 0 ? (
          <p className="muted">{t.alerts.none}</p>
        ) : (
          <ul className="alerts">
            {[...alerts].sort(order).map((a) => {
              const fd = flightDayOf(state.days, a.met);
              return (
                <li key={a.id} className={`alert-item level-${a.level} ack-${a.ack.status}`}>
                  <div className="adaptation-head">
                    <span className={`badge alert-level-${a.level}`}>
                      {t.labels.alertLevel[a.level]}
                    </span>
                    <strong>{crewName(a.crewId)}</strong>
                    <span className="badge">{t.labels.alertKind[a.kind]}</span>
                    {a.capacityId ? <span className="badge">{capacityName(a.capacityId)}</span> : null}
                    <span className="num">
                      {fd === null ? "—" : formatFlightDay(fd)} · MET {formatMet(a.met)}
                    </span>
                    {a.source === "simulated" ? <span className="badge">{t.common.simulated}</span> : null}
                    <span className="muted adaptation-meta">
                      {t.common.received(
                        a.packageVersion,
                        formatDate(a.receivedAt, { dateStyle: "short", timeStyle: "medium" }),
                      )}
                    </span>
                  </div>
                  <p>{a.message}</p>
                  {a.evidence.length > 0 ? (
                    <ul className="evidence">
                      {a.evidence.map((e, i) => (
                        <li key={i}>
                          {e.indicator}:{" "}
                          <span className="num">
                            {e.value}
                            {e.unit ? ` ${e.unit}` : ""}
                          </span>{" "}
                          <span className="muted">
                            ({t.labels.evidenceSource[e.source]}
                            {e.met !== undefined ? `, MET ${formatMet(e.met)}` : ""})
                          </span>
                        </li>
                      ))}
                    </ul>
                  ) : null}
                  <div className="adaptation-review">
                    <span className={`review-status ack-${a.ack.status}`}>{t.labels.alertStatus[a.ack.status]}</span>
                    <input
                      placeholder={t.alerts.notePlaceholder}
                      aria-label={t.alerts.noteLabel}
                      value={notes[a.id] ?? a.ack.note}
                      onChange={(e) => setNotes({ ...notes, [a.id]: e.target.value })}
                    />
                    <button
                      type="button"
                      className="btn"
                      disabled={a.ack.status !== "new"}
                      onClick={() => acknowledge(a, "seen")}
                    >
                      {t.alerts.markSeen}
                    </button>
                    <button
                      type="button"
                      className="btn primary"
                      disabled={a.ack.status === "handled"}
                      onClick={() => acknowledge(a, "handled")}
                    >
                      {t.alerts.markHandled}
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
