"use client";

import { useState } from "react";
import type { AppState, CatalogItem, Category, LoadType } from "@/lib/types";
import { CATEGORIES, CATEGORY_LABEL } from "@/lib/types";

interface Props {
  state: AppState;
  update: (fn: (s: AppState) => AppState) => void;
}

const emptyDraft = {
  code: "",
  name: "",
  category: "ejercicio" as Category,
  loadType: "CA" as LoadType,
  intense: false,
  limit: "",
  dose: "",
  source: "",
};

export default function CatalogView({ state, update }: Props) {
  const [draft, setDraft] = useState(emptyDraft);
  const usage = new Map<string, number>();
  for (const entry of state.plan) usage.set(entry.itemId, (usage.get(entry.itemId) ?? 0) + 1);

  const code = draft.code.trim().toUpperCase();
  const duplicate = state.catalog.some((c) => c.code === code);
  const canAdd = code !== "" && draft.name.trim() !== "" && draft.source.trim() !== "" && !duplicate;

  const add = () => {
    if (!canAdd) return;
    const item: CatalogItem = {
      id: code,
      code,
      name: draft.name.trim(),
      category: draft.category,
      loadType: draft.loadType,
      intense: draft.intense,
      transitionPrep: false,
      selfReport: false,
      capabilities: [],
      record: "",
      limit: draft.limit.trim(),
      dose: draft.dose.trim() || null,
      source: draft.source.trim(),
      experimental: false,
    };
    update((s) => ({ ...s, catalog: [...s.catalog, item] }));
    setDraft(emptyDraft);
  };

  const setDose = (id: string, dose: string) =>
    update((s) => ({
      ...s,
      catalog: s.catalog.map((c) => (c.id === id ? { ...c, dose: dose.trim() ? dose : null } : c)),
    }));

  const remove = (id: string) =>
    update((s) => ({ ...s, catalog: s.catalog.filter((c) => c.id !== id) }));

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>Catálogo de prevención</h2>
          <span className="muted">
            Acciones que control de misión puede asignar. La herramienta no inventa dosis: se
            publican tal como se registren aquí.
          </span>
        </div>
        <div className="table-wrap">
          <table className="catalog-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Acción</th>
                <th>Carga</th>
                <th>Límite</th>
                <th>Dosis / rango prescrito</th>
                <th>Procedencia</th>
                <th>Usos</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {CATEGORIES.flatMap((category) =>
                state.catalog
                  .filter((c) => c.category === category)
                  .map((c) => (
                    <tr key={c.id}>
                      <td>
                        <span className={`code cat-${c.category}`}>{c.code}</span>
                      </td>
                      <td>
                        <strong>{c.name}</strong>
                        <small>
                          {CATEGORY_LABEL[c.category]}
                          {c.experimental ? " · experimental" : ""}
                          {c.capabilities.length ? ` · ${c.capabilities.join(", ")}` : ""}
                        </small>
                      </td>
                      <td>
                        {c.loadType === "ninguna" ? "—" : c.loadType}
                        {c.intense ? " · exigente" : ""}
                      </td>
                      <td>{c.limit}</td>
                      <td>
                        <input
                          value={c.dose ?? ""}
                          placeholder="Pendiente de protocolo"
                          onChange={(e) => setDose(c.id, e.target.value)}
                        />
                      </td>
                      <td>
                        {c.sourceUrl ? (
                          <a href={c.sourceUrl} target="_blank" rel="noreferrer">
                            {c.source}
                          </a>
                        ) : (
                          c.source
                        )}
                      </td>
                      <td className="num">{usage.get(c.id) ?? 0}</td>
                      <td>
                        <button
                          type="button"
                          className="icon-btn"
                          disabled={(usage.get(c.id) ?? 0) > 0}
                          title={
                            (usage.get(c.id) ?? 0) > 0
                              ? "En uso en el plan: quítala antes de los días en que está asignada"
                              : "Eliminar del catálogo"
                          }
                          onClick={() => remove(c.id)}
                          aria-label={`Eliminar ${c.name}`}
                        >
                          ×
                        </button>
                      </td>
                    </tr>
                  )),
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Nueva acción</h2>
        </div>
        <div className="form-grid">
          <label>
            Código
            <input
              value={draft.code}
              maxLength={4}
              onChange={(e) => setDraft({ ...draft, code: e.target.value })}
            />
          </label>
          <label>
            Nombre
            <input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
          </label>
          <label>
            Categoría
            <select
              value={draft.category}
              onChange={(e) => setDraft({ ...draft, category: e.target.value as Category })}
            >
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {CATEGORY_LABEL[c]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Tipo de carga
            <select
              value={draft.loadType}
              onChange={(e) => setDraft({ ...draft, loadType: e.target.value as LoadType })}
            >
              <option value="CA">CA · acondicionamiento</option>
              <option value="RE">RE · recuperación</option>
              <option value="ninguna">Ninguna</option>
            </select>
          </label>
          <label>
            Dosis / rango prescrito (opcional)
            <input value={draft.dose} onChange={(e) => setDraft({ ...draft, dose: e.target.value })} />
          </label>
          <label>
            Procedencia (protocolo o fuente)
            <input
              value={draft.source}
              onChange={(e) => setDraft({ ...draft, source: e.target.value })}
            />
          </label>
          <label className="wide">
            Límite de uso
            <input value={draft.limit} onChange={(e) => setDraft({ ...draft, limit: e.target.value })} />
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={draft.intense}
              onChange={(e) => setDraft({ ...draft, intense: e.target.checked })}
            />
            Carga exigente (no admite días críticos ni vísperas)
          </label>
        </div>
        <div className="form-actions">
          <button type="button" className="btn primary" disabled={!canAdd} onClick={add}>
            Añadir al catálogo
          </button>
          {duplicate ? <span className="muted">Ya existe una acción con ese código.</span> : null}
        </div>
      </section>
    </div>
  );
}
