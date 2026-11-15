"use client";

import type { Messages } from "@/lib/i18n";
import type { AppState, LocalizedText, QuestionItem, QuestionTrigger, Signal } from "@/lib/types";
import { useI18n } from "./I18n";

interface Props {
  state: AppState;
}

const threshold = (value: number | null | undefined) =>
  value === null || value === undefined ? "—" : String(value);

function describeTrigger(
  trigger: QuestionTrigger,
  signals: Map<string, Signal>,
  t: Messages,
  tx: (text: LocalizedText) => string,
): string {
  const m = t.questionnaire.trigger;
  switch (trigger.kind) {
    case "transition": {
      const to = trigger.toGravity.length
        ? m.transitionTo(trigger.toGravity.map((g) => t.labels.gravityShort[g]).join(", "))
        : "";
      const micro = trigger.minDaysInMicro ? m.transitionMicro(trigger.minDaysInMicro) : "";
      return m.transition(to, trigger.daysBefore, trigger.daysAfter, micro);
    }
    case "environment":
      return m.environment(trigger.gravity.map((g) => t.labels.gravityShort[g]).join(", "));
    case "micro-days":
      return m.microDays(trigger.min);
    case "event":
      return m.event(t.labels.eventKind[trigger.eventKind], trigger.withinDays);
    case "signal": {
      const s = signals.get(trigger.signalId);
      const name = s ? tx(s.name) : trigger.signalId;
      if (s?.kind === "boolean") return name;
      return `${name} ${trigger.op} ${trigger.value}${s?.unit ? ` ${s.unit}` : ""}`;
    }
    case "filler":
      return m.filler;
  }
}

function Variants({ item }: { item: QuestionItem }) {
  const { t, tx } = useI18n();
  return (
    <ol className="q-variants">
      {item.variants.map((v) => (
        <li key={v.id}>
          {tx(v.text)}
          <small>
            {item.scale.min} = {tx(v.minLabel)} · {item.scale.max} = {tx(v.maxLabel)}
            {v.inverted ? t.questionnaire.inverted : ""}
          </small>
        </li>
      ))}
    </ol>
  );
}

export default function QuestionnaireView({ state }: Props) {
  const { t, tx } = useI18n();
  const q = state.questionnaire;
  const capacityName = (id: string) => {
    const capacity = state.capacities.find((c) => c.id === id);
    return capacity ? tx(capacity.name) : id;
  };
  const signals = new Map(state.signals.map((s) => [s.id, s]));
  const fixed = q.items.filter((i) => i.block === "fixed");
  const bank = q.items.filter((i) => i.block === "bank");
  const catalogItem = state.catalog.find((c) => c.id === q.catalogItemId);

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>{tx(q.name)}</h2>
          <span className="muted">
            {t.questionnaire.appliesTo(
              catalogItem ? `“${catalogItem.code} · ${tx(catalogItem.name)}”` : q.catalogItemId,
            )}
          </span>
        </div>
        <p className="q-summary">
          {t.questionnaire.perDayStart} <strong>{t.questionnaire.fixedCount(q.fixedPerDay)}</strong>{" "}
          {t.questionnaire.perDayMid} <strong>{t.questionnaire.bankCount(q.bankPerDay)}</strong>
          {t.questionnaire.perDayEnd(tx(q.source))}
        </p>
        <p className="q-demo">{t.questionnaire.demo}</p>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>{t.questionnaire.capacitiesTitle}</h2>
          <span className="muted">{t.questionnaire.capacitiesHint}</span>
        </div>
        <div className="table-wrap">
          <table className="q-table">
            <thead>
              <tr>
                <th>{t.questionnaire.colCapacity}</th>
                <th>{t.questionnaire.colInAmber}</th>
                <th>{t.questionnaire.colInRed}</th>
              </tr>
            </thead>
            <tbody>
              {state.capacities.map((c) => (
                <tr key={c.id}>
                  <td>
                    <strong>{tx(c.name)}</strong>
                    <small>{tx(c.description)}</small>
                  </td>
                  <td>
                    {tx(c.onAmber)}
                    {(c.onAmberActions ?? []).map((a) => (
                      <small key={`${a.category}-${a.replacementItemId}`}>
                        {t.questionnaire.amberAction(
                          t.labels.category[a.category],
                          tx(state.catalog.find((i) => i.id === a.replacementItemId)?.name) ||
                            a.replacementItemId,
                        )}
                      </small>
                    ))}
                    {c.criticalFor?.length ? (
                      <small>
                        {t.questionnaire.criticalFor(
                          c.criticalFor.map((g) => t.labels.gravityShort[g]).join(", "),
                        )}
                      </small>
                    ) : null}
                  </td>
                  <td>{tx(c.onRed)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {[
        { id: "fixed", title: t.questionnaire.blockA, items: fixed, showTriggers: false },
        { id: "bank", title: t.questionnaire.blockB, items: bank, showTriggers: true },
      ].map(({ id, title, items, showTriggers }) => (
        <section className="panel" key={id}>
          <div className="panel-head">
            <h2>{title}</h2>
            <span className="muted">{t.questionnaire.thresholdsHint}</span>
          </div>
          <div className="table-wrap">
            <table className="q-table">
              <thead>
                <tr>
                  <th>{t.questionnaire.colQuestion}</th>
                  <th>{t.questionnaire.colWordings}</th>
                  {showTriggers ? <th>{t.questionnaire.colTriggers}</th> : null}
                  <th>{t.questionnaire.colAmber}</th>
                  <th>{t.questionnaire.colRed}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <span className="code cat-assessment">{item.id}</span>
                      <strong>{tx(item.domain)}</strong>
                      <small>
                        {t.questionnaire.itemMeta(
                          capacityName(item.capacityId),
                          item.scale.min,
                          item.scale.max,
                          tx(item.inspiredBy),
                        )}
                      </small>
                    </td>
                    <td>
                      <Variants item={item} />
                    </td>
                    {showTriggers ? (
                      <td>
                        {item.triggers.length === 0 ? (
                          <span className="muted">{t.questionnaire.noTrigger}</span>
                        ) : (
                          <ul className="q-triggers">
                            {item.triggers.map((trigger, i) => (
                              <li key={i} title={tx(trigger.reason)}>
                                {describeTrigger(trigger, signals, t, tx)}{" "}
                                <span className="muted">({trigger.priority})</span>
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                    ) : null}
                    <td className="num">{threshold(item.amber)}</td>
                    <td className="num">{threshold(item.red)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}

      <section className="panel">
        <div className="panel-head">
          <h2>{t.questionnaire.signalsTitle}</h2>
          <span className="muted">{t.questionnaire.signalsHint}</span>
        </div>
        <div className="table-wrap">
          <table className="q-table">
            <thead>
              <tr>
                <th>{t.questionnaire.colSignal}</th>
                <th>{t.questionnaire.colOrigin}</th>
                <th>{t.questionnaire.colCapacity}</th>
                <th>{t.questionnaire.colAmber}</th>
                <th>{t.questionnaire.colRed}</th>
              </tr>
            </thead>
            <tbody>
              {state.signals.map((s) => (
                <tr key={s.id}>
                  <td>
                    <strong>{tx(s.name)}</strong>
                    <small>{tx(s.description)}</small>
                  </td>
                  <td>
                    {t.labels.signalSource[s.source]}
                    {s.simulated ? <span className="badge q-sim">{t.common.simulated}</span> : null}
                  </td>
                  <td>{capacityName(s.capacityId)}</td>
                  {s.kind === "boolean" ? (
                    <>
                      <td className="num">{s.whenTrue === "amber" ? t.questionnaire.ifOccurs : "—"}</td>
                      <td className="num">{s.whenTrue === "red" ? t.questionnaire.ifOccurs : "—"}</td>
                    </>
                  ) : (
                    <>
                      <td className="num">
                        {threshold(s.amber)}
                        {s.amber !== null && s.amber !== undefined ? ` ${s.unit}` : ""}
                      </td>
                      <td className="num">
                        {threshold(s.red)}
                        {s.red !== null && s.red !== undefined ? ` ${s.unit}` : ""}
                      </td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {q.consistencyChecks.length > 0 ? (
        <section className="panel">
          <div className="panel-head">
            <h2>{t.questionnaire.consistencyTitle}</h2>
            <span className="muted">{t.questionnaire.consistencyHint}</span>
          </div>
          <ul className="q-checks">
            {q.consistencyChecks.map((c) => {
              const s = signals.get(c.signalId);
              return (
                <li key={c.id}>
                  <strong>
                    {t.questionnaire.consistencyRule(c.questionId, c.answerAtMost, s ? tx(s.name) : c.signalId)}{" "}
                    {c.op} {c.value}
                    {s?.unit ? ` ${s.unit}` : ""}
                  </strong>
                  <span>{tx(c.message)}</span>
                </li>
              );
            })}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
