"use client";

import { withText } from "@/lib/i18n";
import { formatFlightDay, formatMet } from "@/lib/met";
import type { AppState, EventKind, FlightDay, Gravity, Issue, LoadLevel, MissionEvent } from "@/lib/types";
import { EVENT_KINDS, GRAVITIES, LOAD_LEVELS, flightDayOf } from "@/lib/types";
import { useI18n } from "./I18n";
import LocalizedInput from "./LocalizedInput";
import MetInput from "./MetInput";

interface Props {
  state: AppState;
  issues: Issue[];
  selectedDay: number;
  onSelect: (day: number) => void;
  update: (fn: (s: AppState) => AppState) => void;
}

export default function MissionView({ state, issues, selectedDay, onSelect, update }: Props) {
  const { t, tx, locale } = useI18n();

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
        else {
          delete next.transitionFrom;
          delete next.transitionMet;
        }
        return next;
      }),
    }));

  const patchMission = (patch: Partial<AppState["mission"]>) =>
    update((s) => ({ ...s, mission: { ...s.mission, ...patch } }));

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
        name: withText(undefined, locale, t.mission.newEvent),
        kind: "milestone",
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
          <h2>{t.mission.title}</h2>
          <span className="muted">{t.mission.timeline(state.mission.timelineSource)}</span>
        </div>
        <div className="form-grid">
          <label>
            {t.mission.name}
            <LocalizedInput value={state.mission.name} onChange={(name) => patchMission({ name })} />
          </label>
          <label>
            {t.mission.destination}
            <LocalizedInput
              value={state.mission.destination}
              onChange={(destination) => patchMission({ destination })}
            />
          </label>
          <label>
            {t.mission.launchUtc}
            <input
              placeholder={t.mission.launchPlaceholder}
              value={state.mission.launchUtc ?? ""}
              onChange={(e) => patchMission({ launchUtc: e.target.value.trim() || null })}
            />
          </label>
          <label className="wide">
            {t.mission.description}
            <LocalizedInput
              rows={2}
              value={state.mission.description}
              onChange={(description) => patchMission({ description })}
            />
          </label>
        </div>
        <h3>{t.mission.crew}</h3>
        <div className="crew-grid">
          {state.crew.map((member) => (
            <div key={member.id} className="crew-card">
              <input
                aria-label={t.mission.crewName}
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
              <LocalizedInput
                ariaLabel={t.mission.crewRole}
                value={member.role}
                onChange={(role) =>
                  update((s) => ({
                    ...s,
                    crew: s.crew.map((c) => (c.id === member.id ? { ...c, role } : c)),
                  }))
                }
              />
              <label className="inline-number">
                {t.mission.crewRestingHr}
                <input
                  type="number"
                  min={30}
                  max={120}
                  value={member.restingHrBpm ?? ""}
                  onChange={(e) => {
                    const value = e.target.value === "" ? null : Number(e.target.value);
                    update((s) => ({
                      ...s,
                      crew: s.crew.map((c) =>
                        c.id === member.id ? { ...c, restingHrBpm: value } : c,
                      ),
                    }));
                  }}
                />
              </label>
            </div>
          ))}
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>{t.mission.daysTitle(state.days.length)}</h2>
          <span className="muted">{t.mission.daysHint}</span>
        </div>
        <div className="table-wrap">
          <table className="days-table">
            <thead>
              <tr>
                <th>{t.mission.colFd}</th>
                <th>{t.mission.colStart}</th>
                <th>{t.mission.colEnd}</th>
                <th>{t.mission.colPhase}</th>
                <th>{t.mission.colMilestone}</th>
                <th>{t.mission.colTransition}</th>
                <th>{t.mission.colGravity}</th>
                <th>{t.mission.colLoad}</th>
                <th>{t.mission.colWarnings}</th>
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
                    <LocalizedInput value={d.phase} onChange={(phase) => patchDay(d.day, { phase })} />
                  </td>
                  <td>
                    <LocalizedInput
                      value={d.milestone}
                      onChange={(milestone) => patchDay(d.day, { milestone })}
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
                          {t.labels.gravityShort[g]}
                        </option>
                      ))}
                    </select>
                    {d.transitionFrom ? (
                      d.transitionMet !== undefined ? (
                        <MetInput
                          key={d.transitionMet}
                          value={d.transitionMet}
                          ariaLabel={t.mission.transitionMet}
                          onCommit={(transitionMet) => patchDay(d.day, { transitionMet })}
                        />
                      ) : (
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => patchDay(d.day, { transitionMet: d.startMet })}
                        >
                          {t.mission.setTransitionMet}
                        </button>
                      )
                    ) : null}
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
                            {t.labels.gravityShort[g]}
                          </option>
                        ))}
                      </select>
                    </span>
                  </td>
                  <td>
                    <span className="cell-with-swatch">
                      <i className={`swatch ol-${d.opLoad}`} />
                      <select
                        value={d.opLoad}
                        onChange={(e) => patchDay(d.day, { opLoad: e.target.value as LoadLevel })}
                      >
                        {LOAD_LEVELS.map((l) => (
                          <option key={l} value={l}>
                            {t.labels.load[l]}
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
          <h2>{t.mission.eventsTitle(events.length)}</h2>
          <button type="button" className="btn" onClick={addEvent}>
            {t.mission.addEvent(formatFlightDay(selectedDay))}
          </button>
        </div>
        <div className="table-wrap">
          <table className="events-table">
            <thead>
              <tr>
                <th>{t.mission.colMet}</th>
                <th>{t.mission.colFd}</th>
                <th>{t.mission.colEvent}</th>
                <th>{t.mission.colKind}</th>
                <th title={t.mission.approxTitle}>~</th>
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
                        ariaLabel={t.common.metOf(tx(e.name))}
                        onCommit={(met) => patchEvent(e.id, { met })}
                      />
                    </td>
                    <td className="num">{fd === null ? "—" : formatFlightDay(fd)}</td>
                    <td>
                      <LocalizedInput
                        value={e.name}
                        ariaLabel={t.mission.eventName}
                        onChange={(name) => patchEvent(e.id, { name })}
                      />
                    </td>
                    <td>
                      <select
                        value={e.kind}
                        aria-label={t.mission.eventKind}
                        onChange={(ev) => patchEvent(e.id, { kind: ev.target.value as EventKind })}
                      >
                        {EVENT_KINDS.map((k) => (
                          <option key={k} value={k}>
                            {t.labels.eventKind[k]}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        checked={e.approx}
                        aria-label={t.mission.approx}
                        onChange={(ev) => patchEvent(e.id, { approx: ev.target.checked })}
                      />
                    </td>
                    <td>
                      <button
                        type="button"
                        className="icon-btn"
                        onClick={() => removeEvent(e.id)}
                        aria-label={t.common.remove(tx(e.name))}
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
