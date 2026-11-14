"use client";

import { useEffect, useState } from "react";
import { formatFlightDay, formatMet } from "@/lib/met";
import type { Adaptation, AppState, CatalogItem, ReviewStatus } from "@/lib/types";
import { ADAPTATION_TYPE_LABEL, flightDayOf } from "@/lib/types";

interface Props {
  state: AppState;
  adaptations: Adaptation[];
  latestPackageVersion: number | null;
  refresh: () => Promise<void>;
}

const dateFormat = new Intl.DateTimeFormat("es-ES", { dateStyle: "short", timeStyle: "medium" });

const STATUS_LABEL: Record<ReviewStatus, string> = {
  pendiente: "Pendiente de validar",
  validada: "Validada",
  objetada: "Objetada",
};

export default function AdaptationsView({ state, adaptations, latestPackageVersion, refresh }: Props) {
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
    if (!res.ok) setError("No se pudo guardar la revisión.");
    await refresh();
  };

  const ordered = [...adaptations].sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  const count = (s: ReviewStatus) => adaptations.filter((a) => a.review.status === s).length;
  const outOfPolicy = adaptations.filter((a) => !a.policy.withinPolicy).length;

  const describe = (itemId: string | undefined) => {
    const item = itemId ? items.get(itemId) : undefined;
    return item ? (
      <>
        <span className={`code cat-${item.category}`}>{item.code}</span> {item.name}
      </>
    ) : (
      <em>{itemId ?? "desconocida"}</em>
    );
  };

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>Adaptaciones recibidas de la interfaz del astronauta</h2>
          <div className="counts">
            <span className="count sev-aviso">{count("pendiente")} pendientes</span>
            <span className="count">{count("validada")} validadas</span>
            <span className="count sev-bloqueo">{count("objetada")} objetadas</span>
            <span className="count sev-bloqueo">{outOfPolicy} fuera de política</span>
          </div>
        </div>
        <p className="muted">
          La interfaz adapta por su cuenta las actividades marcadas como adaptables (ejercicios
          dentro de los periodos de ejercicio y menús en las comidas) y las aplica a bordo sin
          esperar respuesta. Aquí quedan registradas para su validación. Validar u objetar no
          cambia lo ya hecho: deja constancia y la interfaz puede consultar el resultado.
        </p>
        {error ? <p className="error">{error}</p> : null}

        {ordered.length === 0 ? (
          <p className="muted">Todavía no se ha recibido ninguna adaptación.</p>
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
                    <span className="badge">{ADAPTATION_TYPE_LABEL[a.type]}</span>
                    <span className={`badge ${a.policy.withinPolicy ? "policy-ok" : "policy-out"}`}>
                      {a.policy.withinPolicy ? "Dentro de política" : "Fuera de política"}
                    </span>
                    {a.source === "simulada" ? <span className="badge">Simulada</span> : null}
                    <span className="muted adaptation-meta">
                      Paquete v{a.packageVersion} · recibida {dateFormat.format(new Date(a.receivedAt))}
                    </span>
                  </div>

                  <div className="adaptation-change">
                    <div>
                      <span className="eyebrow">Plan original</span>
                      <p>
                        {a.original ? (
                          <>
                            {describe(a.original.itemId)}{" "}
                            <span className="num muted">MET {formatMet(a.original.met)}</span>
                          </>
                        ) : (
                          <em>No localizada en el paquete</em>
                        )}
                      </p>
                    </div>
                    <span className="arrow">→</span>
                    <div>
                      <span className="eyebrow">Decisión de la interfaz</span>
                      <p>
                        {a.type === "sustitucion" ? describe(a.replacementItemId) : null}
                        {a.type === "reprogramacion" && a.newMet !== undefined ? (
                          <>
                            Reprogramada a <span className="num">MET {formatMet(a.newMet)}</span>
                          </>
                        ) : null}
                        {a.type === "omision" ? "Actividad omitida" : null}
                      </p>
                    </div>
                  </div>

                  <p>
                    <span className="eyebrow">Motivo</span> {a.reason}
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
                            ({e.source}
                            {e.met !== undefined ? `, MET ${formatMet(e.met)}` : ""})
                          </span>
                        </li>
                      ))}
                    </ul>
                  ) : null}
                  {a.policy.violations.map((v) => (
                    <p key={v} className="error">
                      {v}
                    </p>
                  ))}

                  <div className="adaptation-review">
                    <span className={`review-status review-${a.review.status}`}>
                      {STATUS_LABEL[a.review.status]}
                    </span>
                    <input
                      placeholder="Nota de control de misión (opcional)"
                      aria-label="Nota de control de misión"
                      value={notes[a.id] ?? a.review.note}
                      onChange={(e) => setNotes({ ...notes, [a.id]: e.target.value })}
                    />
                    <button
                      type="button"
                      className="btn primary"
                      disabled={a.review.status === "validada"}
                      onClick={() => review(a, "validada")}
                    >
                      Validar
                    </button>
                    <button
                      type="button"
                      className="btn"
                      disabled={a.review.status === "objetada"}
                      onClick={() => review(a, "objetada")}
                    >
                      Objetar
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

/** Envía una adaptación de prueba por el mismo punto de entrada que la interfaz. */
function SimulationForm({
  state,
  latestPackageVersion,
  refresh,
}: Pick<Props, "state" | "latestPackageVersion" | "refresh">) {
  const [crewId, setCrewId] = useState(state.crew[0]?.id ?? "");
  const [entryId, setEntryId] = useState("");
  const [type, setType] = useState<"sustitucion" | "omision">("sustitucion");
  const [replacementId, setReplacementId] = useState("");
  const [reason, setReason] = useState("Fatiga alta declarada tras una mala noche de sueño.");
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
    (type === "omision" || replacementId !== "");

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
        ...(type === "sustitucion" ? { replacementItemId: replacementId } : {}),
        decidedAtMet: entry.met,
        reason,
        evidence: [
          { indicator: "Fatiga autodeclarada", value: "8", unit: "0–10", source: "simulado" },
        ],
        source: "simulada",
      }),
    });
    setMessage(res.ok ? "Adaptación de prueba registrada." : "No se pudo registrar.");
    await refresh();
  };

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Simular una adaptación</h2>
        <span className="muted">
          Para probar el circuito mientras la interfaz del astronauta no esté conectada
        </span>
      </div>
      {latestPackageVersion === null ? (
        <p className="muted">Publica antes un paquete: las adaptaciones se refieren a una versión publicada.</p>
      ) : (
        <>
          <div className="form-grid">
            <label>
              Tripulante
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
              Actividad del plan
              <select
                value={entryId}
                onChange={(e) => {
                  setEntryId(e.target.value);
                  setReplacementId("");
                }}
              >
                <option value="">Elegir…</option>
                {entries.map((e) => {
                  const item = items.get(e.itemId);
                  return (
                    <option key={e.id} value={e.id}>
                      {formatMet(e.met)} · {item?.code} {item?.name} · {e.adaptable ? "adaptable" : "fija"}
                    </option>
                  );
                })}
              </select>
            </label>
            <label>
              Decisión
              <select value={type} onChange={(e) => setType(e.target.value as typeof type)}>
                <option value="sustitucion">Sustitución</option>
                <option value="omision">Omisión</option>
              </select>
            </label>
            {type === "sustitucion" ? (
              <label>
                Sustituir por
                <select value={replacementId} onChange={(e) => setReplacementId(e.target.value)}>
                  <option value="">Elegir…</option>
                  {replacements.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.code} · {c.name}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            <label className="wide">
              Motivo
              <input value={reason} onChange={(e) => setReason(e.target.value)} />
            </label>
          </div>
          <div className="form-actions">
            <button type="button" className="btn primary" disabled={!ready} onClick={send}>
              Enviar adaptación de prueba
            </button>
            <span className="muted">
              Se envía contra el paquete v{latestPackageVersion}. Elegir una actividad fija sirve
              para comprobar que queda señalada como fuera de política.
            </span>
          </div>
          {message ? <p className="muted">{message}</p> : null}
        </>
      )}
    </section>
  );
}
