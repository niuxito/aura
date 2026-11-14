"use client";

import { useEffect, useState } from "react";
import { formatFlightDay, formatMet } from "@/lib/met";
import type { Adaptation, AppState, CatalogItem, ReviewStatus } from "@/lib/types";
import { flightDayOf } from "@/lib/types";
import { useI18n } from "./I18n";

interface Props {
  state: AppState;
  adaptations: Adaptation[];
  latestPackageVersion: number | null;
  refresh: () => Promise<void>;
}

export default function AdaptationsView({ state, adaptations, latestPackageVersion, refresh }: Props) {
  const { t, tx, formatDate } = useI18n();
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const items = new Map<string, CatalogItem>(state.catalog.map((c) => [c.id, c]));
  const crewName = (id: string) => state.crew.find((c) => c.id === id)?.name ?? id;

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const review = async (a: Adaptation, status: ReviewStatus) => {
    setError(null);
    const res = await fetch("/api/adaptations", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id: a.id, status, note: notes[a.id] ?? a.review.note }),
    });
    if (!res.ok) setError(t.adaptations.saveError);
    await refresh();
  };

  const ordered = [...adaptations].sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  const count = (s: ReviewStatus) => adaptations.filter((a) => a.review.status === s).length;
  const outOfPolicy = adaptations.filter((a) => !a.policy.withinPolicy).length;

  const describe = (itemId: string | undefined) => {
    const item = itemId ? items.get(itemId) : undefined;
    return item ? (
      <>
        <span className={`code cat-${item.category}`}>{item.code}</span> {tx(item.name)}
      </>
    ) : (
      <em>{itemId ?? t.common.unknown}</em>
    );
  };

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>{t.adaptations.title}</h2>
          <div className="counts">
            <span className="count sev-warning">{t.adaptations.pending(count("pending"))}</span>
            <span className="count">{t.adaptations.validated(count("validated"))}</span>
            <span className="count sev-blocker">{t.adaptations.objected(count("objected"))}</span>
            <span className="count sev-blocker">{t.adaptations.outOfPolicy(outOfPolicy)}</span>
          </div>
        </div>
        <p className="muted">{t.adaptations.intro}</p>
        {error ? <p className="error">{error}</p> : null}

        {ordered.length === 0 ? (
          <p className="muted">{t.adaptations.none}</p>
        ) : (
          <ul className="adaptations">
            {ordered.map((a) => {
              const fd = flightDayOf(state.days, a.decidedAtMet);
              return (
                <li key={a.id} className={`adaptation review-${a.review.status}`}>
                  <div className="adaptation-head">
                    <span className="num">
                      {fd === null ? "—" : formatFlightDay(fd)} · MET {formatMet(a.decidedAtMet)}
                    </span>
                    <strong>{crewName(a.crewId)}</strong>
                    <span className="badge">{t.labels.adaptationType[a.type]}</span>
                    <span className={`badge ${a.policy.withinPolicy ? "policy-ok" : "policy-out"}`}>
                      {a.policy.withinPolicy ? t.adaptations.withinPolicy : t.adaptations.outOfPolicyBadge}
                    </span>
                    {a.source === "simulated" ? <span className="badge">{t.common.simulated}</span> : null}
                    <span className="muted adaptation-meta">
                      {t.common.received(
                        a.packageVersion,
                        formatDate(a.receivedAt, { dateStyle: "short", timeStyle: "medium" }),
                      )}
                    </span>
                  </div>

                  <div className="adaptation-change">
                    <div>
                      <span className="eyebrow">{t.adaptations.originalPlan}</span>
                      <p>
                        {a.original ? (
                          <>
                            {describe(a.original.itemId)}{" "}
                            <span className="num muted">MET {formatMet(a.original.met)}</span>
                          </>
                        ) : (
                          <em>{t.adaptations.notFound}</em>
                        )}
                      </p>
                    </div>
                    <span className="arrow">→</span>
                    <div>
                      <span className="eyebrow">{t.adaptations.decision}</span>
                      <p>
                        {a.type === "substitution" ? describe(a.replacementItemId) : null}
                        {a.type === "reschedule" && a.newMet !== undefined ? (
                          <>
                            {t.adaptations.rescheduledTo} <span className="num">MET {formatMet(a.newMet)}</span>
                          </>
                        ) : null}
                        {a.type === "omission" ? t.adaptations.omitted : null}
                      </p>
                    </div>
                  </div>

                  <p>
                    <span className="eyebrow">{t.adaptations.reason}</span> {a.reason}
                  </p>
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
                  {a.policy.violations.map((v, i) => (
                    <p key={i} className="error">
                      {tx(v)}
                    </p>
                  ))}

                  <div className="adaptation-review">
                    <span className={`review-status review-${a.review.status}`}>
                      {t.labels.reviewStatus[a.review.status]}
                    </span>
                    <input
                      placeholder={t.adaptations.notePlaceholder}
                      aria-label={t.adaptations.noteLabel}
                      value={notes[a.id] ?? a.review.note}
                      onChange={(e) => setNotes({ ...notes, [a.id]: e.target.value })}
                    />
                    <button
                      type="button"
                      className="btn primary"
                      disabled={a.review.status === "validated"}
                      onClick={() => review(a, "validated")}
                    >
                      {t.adaptations.validate}
                    </button>
                    <button
                      type="button"
                      className="btn"
                      disabled={a.review.status === "objected"}
                      onClick={() => review(a, "objected")}
                    >
                      {t.adaptations.object}
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <SimulationForm
        state={state}
        latestPackageVersion={latestPackageVersion}
        refresh={refresh}
      />
    </div>
  );
}

/** Sends a test adaptation through the same endpoint the interface uses. */
function SimulationForm({
  state,
  latestPackageVersion,
  refresh,
}: Pick<Props, "state" | "latestPackageVersion" | "refresh">) {
  const { t, tx } = useI18n();
  const [crewId, setCrewId] = useState(state.crew[0]?.id ?? "");
  const [entryId, setEntryId] = useState("");
  const [type, setType] = useState<"substitution" | "omission">("substitution");
  const [replacementId, setReplacementId] = useState("");
  const [reason, setReason] = useState(t.adaptations.sim.defaultReason);
  const [message, setMessage] = useState<string | null>(null);

  const items = new Map<string, CatalogItem>(state.catalog.map((c) => [c.id, c]));
  const entries = state.plan
    .filter((e) => e.crewId === crewId)
    .sort((a, b) => a.met - b.met);
  const entry = entries.find((e) => e.id === entryId);
  const entryItem = entry ? items.get(entry.itemId) : undefined;
  const replacements = state.catalog.filter(
    (c) => entryItem && c.category === entryItem.category && c.id !== entryItem.id,
  );
  const ready =
    latestPackageVersion !== null &&
    entry !== undefined &&
    reason.trim() !== "" &&
    (type === "omission" || replacementId !== "");

  const send = async () => {
    if (!entry || latestPackageVersion === null) return;
    setMessage(null);
    const res = await fetch("/api/adaptations", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        id: crypto.randomUUID(),
        packageVersion: latestPackageVersion,
        crewId,
        entryId: entry.id,
        type,
        ...(type === "substitution" ? { replacementItemId: replacementId } : {}),
        decidedAtMet: entry.met,
        reason,
        evidence: [
          { indicator: t.adaptations.sim.evidenceIndicator, value: "8", unit: "0–10", source: "simulated" },
        ],
        source: "simulated",
      }),
    });
    setMessage(res.ok ? t.adaptations.sim.sent : t.adaptations.sim.sendError);
    await refresh();
  };

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>{t.adaptations.sim.title}</h2>
        <span className="muted">{t.adaptations.sim.hint}</span>
      </div>
      {latestPackageVersion === null ? (
        <p className="muted">{t.adaptations.sim.publishFirst}</p>
      ) : (
        <>
          <div className="form-grid">
            <label>
              {t.adaptations.sim.crewMember}
              <select
                value={crewId}
                onChange={(e) => {
                  setCrewId(e.target.value);
                  setEntryId("");
                  setReplacementId("");
                }}
              >
                {state.crew.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {t.adaptations.sim.activity}
              <select
                value={entryId}
                onChange={(e) => {
                  setEntryId(e.target.value);
                  setReplacementId("");
                }}
              >
                <option value="">{t.common.choose}</option>
                {entries.map((e) => {
                  const item = items.get(e.itemId);
                  return (
                    <option key={e.id} value={e.id}>
                      {formatMet(e.met)} · {item?.code} {tx(item?.name)} · {e.adaptable ? t.common.adaptable : t.common.fixed}
                    </option>
                  );
                })}
              </select>
            </label>
            <label>
              {t.adaptations.sim.decision}
              <select value={type} onChange={(e) => setType(e.target.value as typeof type)}>
                <option value="substitution">{t.labels.adaptationType.substitution}</option>
                <option value="omission">{t.labels.adaptationType.omission}</option>
              </select>
            </label>
            {type === "substitution" ? (
              <label>
                {t.adaptations.sim.replaceWith}
                <select value={replacementId} onChange={(e) => setReplacementId(e.target.value)}>
                  <option value="">{t.common.choose}</option>
                  {replacements.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.code} · {tx(c.name)}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            <label className="wide">
              {t.adaptations.sim.reason}
              <input value={reason} onChange={(e) => setReason(e.target.value)} />
            </label>
          </div>
          <div className="form-actions">
            <button type="button" className="btn primary" disabled={!ready} onClick={send}>
              {t.adaptations.sim.send}
            </button>
            <span className="muted">{t.adaptations.sim.sendHint(latestPackageVersion)}</span>
          </div>
          {message ? <p className="muted">{message}</p> : null}
        </>
      )}
    </section>
  );
}
