"use client";

import { useEffect, useState } from "react";
import { formatFlightDay, formatMet } from "@/lib/met";
import type { Alert, AlertStatus, AppState } from "@/lib/types";
import { ALERT_KIND_LABEL, flightDayOf } from "@/lib/types";

interface Props {
  state: AppState;
  alerts: Alert[];
  refresh: () => Promise<void>;
}

const dateFormat = new Intl.DateTimeFormat("es-ES", { dateStyle: "short", timeStyle: "medium" });

const STATUS_LABEL: Record<AlertStatus, string> = {
  nueva: "Nueva",
  vista: "Vista",
  atendida: "Atendida",
};

/** Rojas sin atender primero; dentro de cada grupo, las más recientes arriba. */
function order(a: Alert, b: Alert): number {
  const rank = (x: Alert) => (x.ack.status === "atendida" ? 2 : x.level === "rojo" ? 0 : 1);
  return rank(a) - rank(b) || b.receivedAt.localeCompare(a.receivedAt);
}

export default function AlertsView({ state, alerts, refresh }: Props) {
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const crewName = (id: string) => state.crew.find((c) => c.id === id)?.name ?? id;
  const capacityName = (id?: string) => state.capacities.find((c) => c.id === id)?.name ?? id;

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
    if (!res.ok) setError("No se pudo guardar el acuse.");
    await refresh();
  };

  const count = (s: AlertStatus) => alerts.filter((a) => a.ack.status === s).length;
  const openRed = alerts.filter((a) => a.level === "rojo" && a.ack.status !== "atendida").length;

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>Alertas de la interfaz del astronauta</h2>
          <div className="counts">
            <span className="count sev-bloqueo">{openRed} en rojo sin atender</span>
            <span className="count sev-aviso">{count("nueva")} nuevas</span>
            <span className="count">{count("vista")} vistas</span>
            <span className="count">{count("atendida")} atendidas</span>
          </div>
        </div>
        <p className="muted">
          Lo que la interfaz no resuelve adaptando el plan: una capacidad en rojo (la interfaz no
          decide más y remite al procedimiento de misión), una adaptación que quita una medida de
          preparación de la transición o una incoherencia entre lo que declara el astronauta y lo
          que mide el sensor. Los umbrales son de demostración; la interfaz no diagnostica. Marcar
          una alerta como vista o atendida se lo comunica al astronauta.
        </p>
        {error ? <p className="error">{error}</p> : null}

        {alerts.length === 0 ? (
          <p className="muted">Todavía no se ha recibido ninguna alerta.</p>
        ) : (
          <ul className="alerts">
            {[...alerts].sort(order).map((a) => {
              const fd = flightDayOf(state.days, a.met);
              return (
                <li key={a.id} className={`alert-item level-${a.level} ack-${a.ack.status}`}>
                  <div className="adaptation-head">
                    <span className={`badge alert-level-${a.level}`}>
                      {a.level === "rojo" ? "Rojo" : "Aviso"}
                    </span>
                    <strong>{crewName(a.crewId)}</strong>
                    <span className="badge">{ALERT_KIND_LABEL[a.kind]}</span>
                    {a.capacityId ? <span className="badge">{capacityName(a.capacityId)}</span> : null}
                    <span className="num">
                      {fd === null ? "—" : formatFlightDay(fd)} · MET {formatMet(a.met)}
                    </span>
                    {a.source === "simulada" ? <span className="badge">Simulada</span> : null}
                    <span className="muted adaptation-meta">
                      Paquete v{a.packageVersion} · recibida {dateFormat.format(new Date(a.receivedAt))}
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
                            ({e.source}
                            {e.met !== undefined ? `, MET ${formatMet(e.met)}` : ""})
                          </span>
                        </li>
                      ))}
                    </ul>
                  ) : null}
                  <div className="adaptation-review">
                    <span className={`review-status ack-${a.ack.status}`}>{STATUS_LABEL[a.ack.status]}</span>
                    <input
                      placeholder="Nota para el astronauta (opcional)"
                      aria-label="Nota de control de misión"
                      value={notes[a.id] ?? a.ack.note}
                      onChange={(e) => setNotes({ ...notes, [a.id]: e.target.value })}
                    />
                    <button
                      type="button"
                      className="btn"
                      disabled={a.ack.status !== "nueva"}
                      onClick={() => acknowledge(a, "vista")}
                    >
                      Marcar vista
                    </button>
                    <button
                      type="button"
                      className="btn primary"
                      disabled={a.ack.status === "atendida"}
                      onClick={() => acknowledge(a, "atendida")}
                    >
                      Atendida
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
