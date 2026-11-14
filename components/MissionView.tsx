"use client";

import { formatFlightDay, formatMet } from "@/lib/met";
import type {
  AppState,
  EventKind,
  FlightDay,
  Gravity,
  Issue,
  LoadLevel,
  MissionEvent,
} from "@/lib/types";
import { EVENT_KIND_LABEL, GRAVITY_SHORT, LOAD_LABEL, flightDayOf } from "@/lib/types";
import MetInput from "./MetInput";

interface Props {
  state: AppState;
  issues: Issue[];
  selectedDay: number;
  onSelect: (day: number) => void;
  update: (fn: (s: AppState) => AppState) => void;
}

const GRAVITIES: Gravity[] = ["tierra", "micro", "luna", "marte"];
const LOADS: LoadLevel[] = ["baja", "media", "alta", "critica"];
const EVENT_KINDS: EventKind[] = ["maniobra", "hito", "prueba", "operacion", "eva"];

export default function MissionView({ state, issues, selectedDay, onSelect, update }: Props) {
  const patchDay = (day: number, patch: Partial<FlightDay>) =>
    update((s) => ({
      ...s,
      days: s.days.map((d) => (d.day === day ? { ...d, ...patch } : d)),
    }));

  const setTransition = (day: number, value: string) =>
    update((s) => ({
      ...s,
      days: s.days.map((d) => {
        if (d.day !== day) return d;
        const next = { ...d };
        if (value) next.transitionFrom = value as Gravity;
        else delete next.transitionFrom;
        return next;
      }),
    }));

  const patchEvent = (id: string, patch: Partial<MissionEvent>) =>
    update((s) => ({
      ...s,
      events: s.events.map((e) => (e.id === id ? { ...e, ...patch } : e)),
    }));

  const addEvent = () =>
    update((s) => {
      const day = s.days.find((d) => d.day === selectedDay) ?? s.days[0];
      const event: MissionEvent = {
        id: crypto.randomUUID(),
        met: day.startMet,
        name: "Nuevo evento",
        kind: "hito",
        approx: false,
      };
      return { ...s, events: [...s.events, event] };
    });

  const removeEvent = (id: string) =>
    update((s) => ({ ...s, events: s.events.filter((e) => e.id !== id) }));

  const issueCount = (day: number) =>
    issues.filter((i) => i.day === day && i.severity !== "info").length;

  const events = [...state.events].sort((a, b) => a.met - b.met);

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>Misión</h2>
          <span className="muted">Cronograma: {state.mission.timelineSource}</span>
        </div>
        <div className="form-grid">
          <label>
            Nombre
            <input
              value={state.mission.name}
              onChange={(e) =>
                update((s) => ({ ...s, mission: { ...s.mission, name: e.target.value } }))
              }
            />
          </label>
          <label>
            Destino
            <input
              value={state.mission.destination}
              onChange={(e) =>
                update((s) => ({ ...s, mission: { ...s.mission, destination: e.target.value } }))
              }
            />
          </label>
          <label>
            Despegue en UTC (MET 00/00:00), opcional
            <input
              placeholder="AAAA-MM-DDTHH:MM:SSZ"
              value={state.mission.launchUtc ?? ""}
              onChange={(e) =>
                update((s) => ({
                  ...s,
                  mission: { ...s.mission, launchUtc: e.target.value.trim() || null },
                }))
              }
            />
          </label>
          <label className="wide">
            Descripción
            <textarea
              rows={2}
              value={state.mission.description}
              onChange={(e) =>
                update((s) => ({ ...s, mission: { ...s.mission, description: e.target.value } }))
              }
            />
          </label>
        </div>
        <h3>Tripulación</h3>
        <div className="crew-grid">
          {state.crew.map((member) => (
            <div key={member.id} className="crew-card">
              <input
                aria-label="Nombre"
                value={member.name}
                onChange={(e) =>
                  update((s) => ({
                    ...s,
                    crew: s.crew.map((c) =>
                      c.id === member.id ? { ...c, name: e.target.value } : c,
                    ),
                  }))
                }
              />
              <input
                aria-label="Función"
                value={member.role}
                onChange={(e) =>
                  update((s) => ({
                    ...s,
                    crew: s.crew.map((c) =>
                      c.id === member.id ? { ...c, role: e.target.value } : c,
                    ),
                  }))
                }
              />
            </div>
          ))}
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Días de vuelo · {state.days.length}</h2>
          <span className="muted">
            Límites en MET según el cronograma; fase, gravedad y carga operativa (CO) editables
          </span>
        </div>
        <div className="table-wrap">
          <table className="days-table">
            <thead>
              <tr>
                <th>FD</th>
                <th>MET inicio</th>
                <th>MET fin</th>
                <th>Fase</th>
                <th>Hito principal</th>
                <th>Transición desde</th>
                <th>Gravedad</th>
                <th>CO</th>
                <th>Avisos</th>
              </tr>
            </thead>
            <tbody>
              {state.days.map((d) => (
                <tr
                  key={d.day}
                  className={d.day === selectedDay ? "selected" : ""}
                  onClick={() => onSelect(d.day)}
                >
                  <td className="num">{formatFlightDay(d.day)}</td>
                  <td className="num">{formatMet(d.startMet)}</td>
                  <td className="num">{formatMet(d.endMet)}</td>
                  <td>
                    <input value={d.phase} onChange={(e) => patchDay(d.day, { phase: e.target.value })} />
                  </td>
                  <td>
                    <input
                      value={d.milestone}
                      onChange={(e) => patchDay(d.day, { milestone: e.target.value })}
                    />
                  </td>
                  <td>
                    <select
                      value={d.transitionFrom ?? ""}
                      onChange={(e) => setTransition(d.day, e.target.value)}
                    >
                      <option value="">—</option>
                      {GRAVITIES.filter((g) => g !== d.gravity).map((g) => (
                        <option key={g} value={g}>
                          {GRAVITY_SHORT[g]}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <span className="cell-with-swatch">
                      <i className={`swatch g-${d.gravity}`} />
                      <select
                        value={d.gravity}
                        onChange={(e) => patchDay(d.day, { gravity: e.target.value as Gravity })}
                      >
                        {GRAVITIES.map((g) => (
                          <option key={g} value={g}>
                            {GRAVITY_SHORT[g]}
                          </option>
                        ))}
                      </select>
                    </span>
                  </td>
                  <td>
                    <span className="cell-with-swatch">
                      <i className={`swatch co-${d.co}`} />
                      <select
                        value={d.co}
                        onChange={(e) => patchDay(d.day, { co: e.target.value as LoadLevel })}
                      >
                        {LOADS.map((l) => (
                          <option key={l} value={l}>
                            {LOAD_LABEL[l]}
                          </option>
                        ))}
                      </select>
                    </span>
                  </td>
                  <td className="num">{issueCount(d.day) || ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Eventos de misión · {events.length}</h2>
          <button type="button" className="btn" onClick={addEvent}>
            + Añadir evento en {formatFlightDay(selectedDay)}
          </button>
        </div>
        <div className="table-wrap">
          <table className="events-table">
            <thead>
              <tr>
                <th>MET</th>
                <th>FD</th>
                <th>Evento</th>
                <th>Tipo</th>
                <th title="El cronograma lo da como aproximado">~</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {events.map((e) => {
                const fd = flightDayOf(state.days, e.met);
                return (
                  <tr key={e.id} className={fd === selectedDay ? "selected" : ""}>
                    <td>
                      <MetInput
                        key={e.met}
                        value={e.met}
                        ariaLabel={`MET de ${e.name}`}
                        onCommit={(met) => patchEvent(e.id, { met })}
                      />
                    </td>
                    <td className="num">{fd === null ? "—" : formatFlightDay(fd)}</td>
                    <td>
                      <input
                        value={e.name}
                        aria-label="Nombre del evento"
                        onChange={(ev) => patchEvent(e.id, { name: ev.target.value })}
                      />
                    </td>
                    <td>
                      <select
                        value={e.kind}
                        aria-label="Tipo de evento"
                        onChange={(ev) => patchEvent(e.id, { kind: ev.target.value as EventKind })}
                      >
                        {EVENT_KINDS.map((k) => (
                          <option key={k} value={k}>
                            {EVENT_KIND_LABEL[k]}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        checked={e.approx}
                        aria-label="MET aproximado"
                        onChange={(ev) => patchEvent(e.id, { approx: ev.target.checked })}
                      />
                    </td>
                    <td>
                      <button
                        type="button"
                        className="icon-btn"
                        onClick={() => removeEvent(e.id)}
                        aria-label={`Eliminar ${e.name}`}
                      >
                        ×
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
