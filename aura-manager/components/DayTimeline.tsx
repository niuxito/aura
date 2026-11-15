"use client";

import { useEffect, useRef, useState } from "react";
import { DAY, HOUR, formatMet } from "@/lib/met";
import type { AppState, CatalogItem, FlightDay } from "@/lib/types";
import { adaptableCategoriesOf, flightDayOf } from "@/lib/types";
import { useI18n } from "./I18n";

interface Props {
  state: AppState;
  day: FlightDay;
}

// Activity chip sizes, used to place them without overlapping.
const CHIP_CHAR_PX = 6.2;
const CHIP_PADDING_PX = 16;
const CHIP_GAP_PX = 3;
const LANE_HEIGHT_PX = 20;
const ROW_PADDING_PX = 4;

interface PlacedChip<T> {
  entry: T;
  x: number;
  lane: number;
}

/**
 * Spreads the chips of a row over lanes: each chip starts at its MET and, if
 * it would collide with the previous one there, drops to the first free lane.
 */
function placeChips<T>(
  entries: { entry: T; x: number; label: string }[],
  trackWidth: number,
): { chips: PlacedChip<T>[]; lanes: number } {
  const laneEnds: number[] = [];
  const chips = [...entries]
    .sort((a, b) => a.x - b.x)
    .map(({ entry, x, label }) => {
      const width = label.length * CHIP_CHAR_PX + CHIP_PADDING_PX;
      const left = Math.max(0, Math.min(x, trackWidth - width));
      let lane = laneEnds.findIndex((end) => end + CHIP_GAP_PX <= left);
      if (lane === -1) lane = laneEnds.length;
      laneEnds[lane] = left + width;
      return { entry, x: left, lane };
    });
  return { chips, lanes: Math.max(1, laneEnds.length) };
}

/** Timeline of a flight day on a MET axis, in the style of the Overview Timeline. */
export default function DayTimeline({ state, day }: Props) {
  const { t, tx } = useI18n();
  const trackRef = useRef<HTMLDivElement>(null);
  const [trackWidth, setTrackWidth] = useState(800);

  useEffect(() => {
    const track = trackRef.current;
    if (!track) return;
    const observer = new ResizeObserver(([entry]) => setTrackWidth(entry.contentRect.width));
    observer.observe(track);
    return () => observer.disconnect();
  }, []);

  const span = day.endMet - day.startMet;
  const pct = (m: number) => ((m - day.startMet) / span) * 100;
  const items = new Map<string, CatalogItem>(state.catalog.map((c) => [c.id, c]));

  const ticks: number[] = [];
  for (let t = Math.ceil(day.startMet / HOUR) * HOUR; t <= day.endMet; t += HOUR) ticks.push(t);
  const tickLabel = (t: number) => {
    const hour = (t % DAY) / HOUR;
    return hour === 0 ? `${String(Math.floor(t / DAY)).padStart(2, "0")}/0` : String(hour);
  };

  const blocks = state.blocks.filter((b) => b.endMet > day.startMet && b.startMet < day.endMet);
  const events = state.events
    .filter((e) => flightDayOf(state.days, e.met) === day.day)
    .sort((a, b) => a.met - b.met);
  const kinds = [...new Set(blocks.map((b) => b.kind))];

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>{t.timeline.title}</h2>
        <div className="legend">
          {kinds.map((k) => (
            <span key={k} className="legend-item">
              <i className={`swatch bk-${k}`} />
              {t.labels.blockKind[k]}
            </span>
          ))}
          <span className="legend-item">
            <i className="swatch adaptable" />
            {t.timeline.adaptableLegend}
          </span>
        </div>
      </div>

      <div className="tl-scroll">
        <div className="tl">
          <div className="tl-row tl-axis">
            <span className="tl-label">{t.timeline.axis}</span>
            <div className="tl-track" ref={trackRef}>
              {ticks.map((tick) => (
                <span key={tick} className="tl-tick" style={{ left: `${pct(tick)}%` }}>
                  {tickLabel(tick)}
                </span>
              ))}
            </div>
          </div>

          <div className="tl-row tl-blocks">
            <span className="tl-label">{t.timeline.crewRow}</span>
            <div className="tl-track">
              {ticks.map((tick) => (
                <i key={tick} className="tl-grid" style={{ left: `${pct(tick)}%` }} />
              ))}
              {blocks.map((b) => {
                const start = Math.max(b.startMet, day.startMet);
                const end = Math.min(b.endMet, day.endMet);
                const adaptable = adaptableCategoriesOf(b.kind).length > 0;
                return (
                  <span
                    key={b.id}
                    className={`tl-block bk-${b.kind}${adaptable ? " adaptable" : ""}`}
                    style={{ left: `${pct(start)}%`, width: `${pct(end) - pct(start)}%` }}
                    title={t.timeline.blockTitle(tx(b.name), formatMet(b.startMet), formatMet(b.endMet), adaptable)}
                  >
                    {tx(b.name)}
                  </span>
                );
              })}
            </div>
          </div>

          <div className="tl-row tl-events">
            <span className="tl-label">{t.timeline.eventsRow}</span>
            <div className="tl-track">
              {events.map((e) => (
                <span
                  key={e.id}
                  className={`tl-event ev-${e.kind}`}
                  style={{ left: `${pct(e.met)}%` }}
                  title={`${tx(e.name)} · MET ${e.approx ? "~" : ""}${formatMet(e.met)}`}
                >
                  ▲
                </span>
              ))}
            </div>
          </div>

          {state.crew.map((member) => {
            const { chips, lanes } = placeChips(
              state.plan
                .filter(
                  (e) => e.crewId === member.id && flightDayOf(state.days, e.met) === day.day,
                )
                .flatMap((e) => {
                  const item = items.get(e.itemId);
                  return item
                    ? [{ entry: { ...e, item }, x: (pct(e.met) / 100) * trackWidth, label: item.code }]
                    : [];
                }),
              trackWidth,
            );
            return (
              <div key={member.id} className="tl-row tl-crew">
                <span className="tl-label">{member.name.split(" · ")[0]}</span>
                <div
                  className="tl-track"
                  style={{ height: lanes * LANE_HEIGHT_PX + ROW_PADDING_PX }}
                >
                  {chips.map(({ entry, x, lane }) => (
                    <span
                      key={entry.id}
                      className={`tl-chip cat-${entry.item.category} ${entry.status}${entry.adaptable ? " adaptable" : ""}`}
                      style={{ left: x, top: lane * LANE_HEIGHT_PX + ROW_PADDING_PX / 2 }}
                      title={t.timeline.chipTitle(
                        tx(entry.item.name),
                        formatMet(entry.met),
                        t.labels.entryStatus[entry.status],
                        entry.adaptable,
                      )}
                    >
                      {entry.item.code}
                    </span>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {events.length > 0 ? (
        <ul className="event-list">
          {events.map((e) => (
            <li key={e.id}>
              <span className={`num ev-${e.kind}`}>
                {e.approx ? "~" : ""}
                {formatMet(e.met)}
              </span>
              {tx(e.name)}
            </li>
          ))}
        </ul>
      ) : null}
      <p className="hint">{t.timeline.hint}</p>
    </section>
  );
}
