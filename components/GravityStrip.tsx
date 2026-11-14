"use client";

import { formatFlightDay, formatMet } from "@/lib/met";
import type { FlightDay, Issue } from "@/lib/types";
import { GRAVITY_G, GRAVITY_LABEL, GRAVITY_SHORT, LOAD_LABEL } from "@/lib/types";

interface Props {
  days: FlightDay[];
  issues: Issue[];
  selectedDay: number;
  onSelect: (day: number) => void;
}

/** Altura de la barra de gravedad, con un mínimo visible para microgravedad. */
const barHeight = (g: number) => `${Math.max(4, g * 100)}%`;

export default function GravityStrip({ days, issues, selectedDay, onSelect }: Props) {
  const worst = new Map<number, Issue["severity"]>();
  for (const issue of issues) {
    if (issue.day === null || issue.severity === "info") continue;
    if (issue.severity === "bloqueo" || !worst.has(issue.day)) {
      worst.set(issue.day, issue.severity);
    }
  }
  const present = new Set(days.flatMap((d) => [d.gravity, d.transitionFrom]));

  return (
    <section className="panel strip-panel">
      <div className="panel-head">
        <h2>Perfil gravitatorio y carga operativa por día de vuelo</h2>
        <div className="legend">
          {(["tierra", "marte", "luna", "micro"] as const)
            .filter((g) => present.has(g))
            .map((g) => (
              <span key={g} className="legend-item">
                <i className={`swatch g-${g}`} />
                {GRAVITY_LABEL[g]}
              </span>
            ))}
        </div>
      </div>
      <div className="strip" style={{ gridTemplateColumns: `repeat(${days.length}, 1fr)` }}>
        {days.map((d) => {
          const status = worst.get(d.day);
          return (
            <button
              key={d.day}
              type="button"
              className={`strip-day${d.day === selectedDay ? " selected" : ""}`}
              onClick={() => onSelect(d.day)}
              title={`${formatFlightDay(d.day)} · MET ${formatMet(d.startMet)} – ${formatMet(d.endMet)} · ${d.milestone} · ${GRAVITY_SHORT[d.gravity]} · CO ${LOAD_LABEL[d.co]}`}
            >
              <span className="strip-flag">
                {d.transitionFrom ? "▲" : ""}
                {status ? <i className={`dot sev-${status}`} /> : null}
              </span>
              <span className="strip-bars">
                {d.transitionFrom ? (
                  <i
                    className={`bar g-${d.transitionFrom}`}
                    style={{ height: barHeight(GRAVITY_G[d.transitionFrom]) }}
                  />
                ) : null}
                <i className={`bar g-${d.gravity}`} style={{ height: barHeight(GRAVITY_G[d.gravity]) }} />
              </span>
              <span className={`strip-co co-${d.co}`} />
              <span className="strip-num">
                <strong>{formatFlightDay(d.day)}</strong>
                <small>{formatMet(d.startMet)}</small>
              </span>
            </button>
          );
        })}
      </div>
      <p className="hint">
        Bajo cada día de vuelo, su MET de inicio · ▲ transición gravitatoria · franja: carga
        operativa (<span className="co-text co-baja">baja</span>,{" "}
        <span className="co-text co-media">media</span>, <span className="co-text co-alta">alta</span>,{" "}
        <span className="co-text co-critica">crítica</span>)
      </p>
    </section>
  );
}
