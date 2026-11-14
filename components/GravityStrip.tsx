"use client";

import { formatFlightDay, formatMet } from "@/lib/met";
import type { FlightDay, Issue } from "@/lib/types";
import { GRAVITY_G, LOAD_LEVELS } from "@/lib/types";
import { useI18n } from "./I18n";

interface Props {
  days: FlightDay[];
  issues: Issue[];
  selectedDay: number;
  onSelect: (day: number) => void;
}

/** Height of the gravity bar, with a visible minimum for microgravity. */
const barHeight = (g: number) => `${Math.max(4, g * 100)}%`;

export default function GravityStrip({ days, issues, selectedDay, onSelect }: Props) {
  const { t, tx } = useI18n();
  const worst = new Map<number, Issue["severity"]>();
  for (const issue of issues) {
    if (issue.day === null || issue.severity === "info") continue;
    if (issue.severity === "blocker" || !worst.has(issue.day)) {
      worst.set(issue.day, issue.severity);
    }
  }
  const present = new Set(days.flatMap((d) => [d.gravity, d.transitionFrom]));

  return (
    <section className="panel strip-panel">
      <div className="panel-head">
        <h2>{t.strip.title}</h2>
        <div className="legend">
          {(["earth", "mars", "moon", "micro"] as const)
            .filter((g) => present.has(g))
            .map((g) => (
              <span key={g} className="legend-item">
                <i className={`swatch g-${g}`} />
                {t.labels.gravity[g]}
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
              title={t.strip.dayTitle(
                formatFlightDay(d.day),
                formatMet(d.startMet),
                formatMet(d.endMet),
                tx(d.milestone),
                t.labels.gravityShort[d.gravity],
                t.labels.load[d.opLoad],
              )}
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
              <span className={`strip-ol ol-${d.opLoad}`} />
              <span className="strip-num">
                <strong>{formatFlightDay(d.day)}</strong>
                <small>{formatMet(d.startMet)}</small>
              </span>
            </button>
          );
        })}
      </div>
      <p className="hint">
        {t.strip.hint} (
        {LOAD_LEVELS.map((l, i) => (
          <span key={l}>
            {i > 0 ? ", " : ""}
            <span className={`ol-text ol-${l}`}>{t.labels.load[l].toLowerCase()}</span>
          </span>
        ))}
        )
      </p>
    </section>
  );
}
