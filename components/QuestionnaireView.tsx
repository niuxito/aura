"use client";

import type { AppState, QuestionItem, QuestionTrigger, Signal } from "@/lib/types";
import { EVENT_KIND_LABEL, GRAVITY_SHORT } from "@/lib/types";

interface Props {
  state: AppState;
}

const SOURCE_LABEL: Record<Signal["source"], string> = {
  reloj: "Reloj",
  ropa: "Ropa inteligente",
  prueba: "Prueba de coordinación",
};

const threshold = (value: number | null | undefined) =>
  value === null || value === undefined ? "—" : String(value);

function describeTrigger(t: QuestionTrigger, signals: Map<string, Signal>): string {
  switch (t.kind) {
    case "transicion": {
      const to = t.toGravity.length
        ? ` hacia ${t.toGravity.map((g) => GRAVITY_SHORT[g]).join(", ")}`
        : "";
      const micro = t.minDaysInMicro ? `, tras ≥ ${t.minDaysInMicro} FD en micro-g` : "";
      return `Cambio de gravedad${to}: ${t.daysBefore} FD antes a ${t.daysAfter} FD después${micro}`;
    }
    case "entorno":
      return `En ${t.gravity.map((g) => GRAVITY_SHORT[g]).join(", ")}`;
    case "dias-micro":
      return `≥ ${t.min} FD seguidos en micro-g`;
    case "evento":
      return `${EVENT_KIND_LABEL[t.eventKind]} en los últimos ${t.withinDays} FD`;
    case "senal": {
      const s = signals.get(t.signalId);
      const name = s?.name ?? t.signalId;
      if (s?.kind === "boolean") return name;
      return `${name} ${t.op} ${t.value}${s?.unit ? ` ${s.unit}` : ""}`;
    }
    case "relleno":
      return "Relleno si faltan preguntas";
  }
}

function Variants({ item }: { item: QuestionItem }) {
  return (
    <ol className="q-variants">
      {item.variants.map((v) => (
        <li key={v.id}>
          {v.text}
          <small>
            {item.scale.min} = {v.minLabel} · {item.scale.max} = {v.maxLabel}
            {v.inverted ? " · invertida (alto = mejor)" : ""}
          </small>
        </li>
      ))}
    </ol>
  );
}

export default function QuestionnaireView({ state }: Props) {
  const q = state.questionnaire;
  const capacityName = new Map(state.capacities.map((c) => [c.id, c.name]));
  const signals = new Map(state.signals.map((s) => [s.id, s]));
  const fixed = q.items.filter((i) => i.block === "fijo");
  const bank = q.items.filter((i) => i.block === "banco");
  const catalogItem = state.catalog.find((c) => c.id === q.catalogItemId);

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>{q.name}</h2>
          <span className="muted">
            Se publica en el paquete y lo aplica la interfaz del astronauta cada vez que el plan
            programa {catalogItem ? `«${catalogItem.code} · ${catalogItem.name}»` : q.catalogItemId}.
          </span>
        </div>
        <p className="q-summary">
          Cada día: <strong>{q.fixedPerDay} preguntas fijas</strong> (una redacción en rotación) y{" "}
          <strong>{q.bankPerDay} del banco</strong>, elegidas por prioridad según los disparadores.
          Sin respuesta, la capacidad se muestra en gris. Procedencia: {q.source}.
        </p>
        <p className="q-demo">
          Umbrales de demostración, no criterios clínicos. Cada pregunta se inspira en un
          instrumento publicado, pero una pregunta suelta no equivale a ese instrumento validado.
        </p>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Capacidades con semáforo</h2>
          <span className="muted">Qué hace la interfaz en cada nivel, siempre dentro de la política de adaptación.</span>
        </div>
        <div className="table-wrap">
          <table className="q-table">
            <thead>
              <tr>
                <th>Capacidad</th>
                <th>En ámbar</th>
                <th>En rojo</th>
              </tr>
            </thead>
            <tbody>
              {state.capacities.map((c) => (
                <tr key={c.id}>
                  <td>
                    <strong>{c.name}</strong>
                    <small>{c.description}</small>
                  </td>
                  <td>{c.onAmbar}</td>
                  <td>{c.onRojo}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {[
        { title: "Bloque A · preguntas fijas", items: fixed, showTriggers: false },
        { title: "Bloque B · banco de preguntas", items: bank, showTriggers: true },
      ].map(({ title, items, showTriggers }) => (
        <section className="panel" key={title}>
          <div className="panel-head">
            <h2>{title}</h2>
            <span className="muted">
              Umbrales sobre el valor normalizado (alto = peor), incluidos.
            </span>
          </div>
          <div className="table-wrap">
            <table className="q-table">
              <thead>
                <tr>
                  <th>Pregunta</th>
                  <th>Redacciones</th>
                  {showTriggers ? <th>Disparadores (prioridad)</th> : null}
                  <th>Ámbar</th>
                  <th>Rojo</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <span className="code cat-evaluacion">{item.id}</span>
                      <strong>{item.domain}</strong>
                      <small>
                        {capacityName.get(item.capacityId) ?? item.capacityId} · escala{" "}
                        {item.scale.min}–{item.scale.max} · inspirada en {item.inspiredBy}
                      </small>
                    </td>
                    <td>
                      <Variants item={item} />
                    </td>
                    {showTriggers ? (
                      <td>
                        {item.triggers.length === 0 ? (
                          <span className="muted">Sin disparador disponible</span>
                        ) : (
                          <ul className="q-triggers">
                            {item.triggers.map((t, i) => (
                              <li key={i}>
                                {describeTrigger(t, signals)} <span className="muted">({t.priority})</span>
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                    ) : null}
                    <td className="num">{threshold(item.ambar)}</td>
                    <td className="num">{threshold(item.rojo)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}

      <section className="panel">
        <div className="panel-head">
          <h2>Señales objetivas</h2>
          <span className="muted">
            Las de la ropa inteligente se simulan en la demo: la interfaz las guarda y muestra como
            «simulado».
          </span>
        </div>
        <div className="table-wrap">
          <table className="q-table">
            <thead>
              <tr>
                <th>Señal</th>
                <th>Origen</th>
                <th>Capacidad</th>
                <th>Ámbar</th>
                <th>Rojo</th>
              </tr>
            </thead>
            <tbody>
              {state.signals.map((s) => (
                <tr key={s.id}>
                  <td>
                    <strong>{s.name}</strong>
                    <small>{s.description}</small>
                  </td>
                  <td>
                    {SOURCE_LABEL[s.source]}
                    {s.simulated ? <span className="badge q-sim">Simulada</span> : null}
                  </td>
                  <td>{capacityName.get(s.capacityId) ?? s.capacityId}</td>
                  {s.kind === "boolean" ? (
                    <>
                      <td className="num">{s.whenTrue === "ambar" ? "Si ocurre" : "—"}</td>
                      <td className="num">{s.whenTrue === "rojo" ? "Si ocurre" : "—"}</td>
                    </>
                  ) : (
                    <>
                      <td className="num">
                        {threshold(s.ambar)}
                        {s.ambar !== null && s.ambar !== undefined ? ` ${s.unit}` : ""}
                      </td>
                      <td className="num">
                        {threshold(s.rojo)}
                        {s.rojo !== null && s.rojo !== undefined ? ` ${s.unit}` : ""}
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
            <h2>Coherencia entre autoinforme y sensores</h2>
            <span className="muted">Si se contradicen, la interfaz lo explica y usa el peor de los dos.</span>
          </div>
          <ul className="q-checks">
            {q.consistencyChecks.map((c) => {
              const s = signals.get(c.signalId);
              return (
                <li key={c.id}>
                  <strong>
                    {c.questionId} ≤ {c.answerAtMost} y {s?.name ?? c.signalId} {c.op} {c.value}
                    {s?.unit ? ` ${s.unit}` : ""}
                  </strong>
                  <span>{c.message}</span>
                </li>
              );
            })}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
