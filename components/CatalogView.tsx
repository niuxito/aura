"use client";

import { useState } from "react";
import { withText } from "@/lib/i18n";
import type { AppState, CatalogItem, Category, LoadType } from "@/lib/types";
import { CATEGORIES } from "@/lib/types";
import { useI18n } from "./I18n";

interface Props {
  state: AppState;
  update: (fn: (s: AppState) => AppState) => void;
}

const LOAD_TYPES: LoadType[] = ["CL", "RE", "none"];

const emptyDraft = {
  code: "",
  name: "",
  category: "exercise" as Category,
  loadType: "CL" as LoadType,
  intense: false,
  limit: "",
  dose: "",
  source: "",
};

export default function CatalogView({ state, update }: Props) {
  const { t, tx, locale } = useI18n();
  const [draft, setDraft] = useState(emptyDraft);
  const usage = new Map<string, number>();
  for (const entry of state.plan) usage.set(entry.itemId, (usage.get(entry.itemId) ?? 0) + 1);

  const code = draft.code.trim().toUpperCase();
  const duplicate = state.catalog.some((c) => c.code === code);
  const canAdd = code !== "" && draft.name.trim() !== "" && draft.source.trim() !== "" && !duplicate;

  /** New texts are written in the console's current language. */
  const text = (value: string) => withText(undefined, locale, value.trim());

  const add = () => {
    if (!canAdd) return;
    const item: CatalogItem = {
      id: code,
      code,
      name: text(draft.name),
      category: draft.category,
      loadType: draft.loadType,
      intense: draft.intense,
      transitionPrep: false,
      selfReport: false,
      capabilities: [],
      record: {},
      limit: text(draft.limit),
      dose: draft.dose.trim() ? text(draft.dose) : null,
      source: text(draft.source),
      experimental: false,
    };
    update((s) => ({ ...s, catalog: [...s.catalog, item] }));
    setDraft(emptyDraft);
  };

  const patchItem = (id: string, patch: Partial<CatalogItem>) =>
    update((s) => ({
      ...s,
      catalog: s.catalog.map((c) => (c.id === id ? { ...c, ...patch } : c)),
    }));

  const remove = (id: string) =>
    update((s) => ({ ...s, catalog: s.catalog.filter((c) => c.id !== id) }));

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>{t.catalog.title}</h2>
          <span className="muted">{t.catalog.hint}</span>
        </div>
        <div className="table-wrap">
          <table className="catalog-table">
            <thead>
              <tr>
                <th>{t.catalog.colCode}</th>
                <th>{t.catalog.colAction}</th>
                <th>{t.catalog.colLoad}</th>
                <th>{t.catalog.colLimit}</th>
                <th>{t.catalog.colDose}</th>
                <th title={t.catalog.durationHint}>{t.catalog.colDuration}</th>
                <th>{t.catalog.colSource}</th>
                <th>{t.catalog.colUses}</th>
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
                        <strong>{tx(c.name)}</strong>
                        <small>
                          {t.labels.category[c.category]}
                          {c.experimental ? ` · ${t.common.experimental}` : ""}
                          {c.capabilities.length ? ` · ${c.capabilities.map(tx).join(", ")}` : ""}
                        </small>
                      </td>
                      <td>
                        {c.loadType === "none" ? "—" : c.loadType}
                        {c.intense ? ` · ${t.catalog.demanding}` : ""}
                      </td>
                      <td>{tx(c.limit)}</td>
                      <td>
                        <DoseInput
                          value={c.dose}
                          placeholder={t.catalog.dosePlaceholder}
                          onChange={(dose) => patchItem(c.id, { dose })}
                        />
                      </td>
                      <td>
                        <input
                          className="num-input"
                          type="number"
                          min={1}
                          title={t.catalog.durationHint}
                          value={c.durationMin ?? ""}
                          onChange={(e) =>
                            patchItem(c.id, {
                              durationMin: e.target.value === "" ? null : Math.max(1, Number(e.target.value)),
                            })
                          }
                        />
                      </td>
                      <td>
                        {c.sourceUrl ? (
                          <a href={c.sourceUrl} target="_blank" rel="noreferrer">
                            {tx(c.source)}
                          </a>
                        ) : (
                          tx(c.source)
                        )}
                      </td>
                      <td className="num">{usage.get(c.id) ?? 0}</td>
                      <td>
                        <button
                          type="button"
                          className="icon-btn"
                          disabled={(usage.get(c.id) ?? 0) > 0}
                          title={(usage.get(c.id) ?? 0) > 0 ? t.catalog.inUse : t.catalog.removeFromCatalog}
                          onClick={() => remove(c.id)}
                          aria-label={t.common.remove(tx(c.name))}
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
          <h2>{t.catalog.newTitle}</h2>
          <span className="muted">{t.catalog.languageNote(t.languageName[locale])}</span>
        </div>
        <div className="form-grid">
          <label>
            {t.catalog.code}
            <input
              value={draft.code}
              maxLength={4}
              onChange={(e) => setDraft({ ...draft, code: e.target.value })}
            />
          </label>
          <label>
            {t.catalog.name}
            <input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
          </label>
          <label>
            {t.catalog.category}
            <select
              value={draft.category}
              onChange={(e) => setDraft({ ...draft, category: e.target.value as Category })}
            >
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {t.labels.category[c]}
                </option>
              ))}
            </select>
          </label>
          <label>
            {t.catalog.loadType}
            <select
              value={draft.loadType}
              onChange={(e) => setDraft({ ...draft, loadType: e.target.value as LoadType })}
            >
              {LOAD_TYPES.map((l) => (
                <option key={l} value={l}>
                  {t.labels.loadType[l]}
                </option>
              ))}
            </select>
          </label>
          <label>
            {t.catalog.doseOptional}
            <input value={draft.dose} onChange={(e) => setDraft({ ...draft, dose: e.target.value })} />
          </label>
          <label>
            {t.catalog.source}
            <input
              value={draft.source}
              onChange={(e) => setDraft({ ...draft, source: e.target.value })}
            />
          </label>
          <label className="wide">
            {t.catalog.limit}
            <input value={draft.limit} onChange={(e) => setDraft({ ...draft, limit: e.target.value })} />
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={draft.intense}
              onChange={(e) => setDraft({ ...draft, intense: e.target.checked })}
            />
            {t.catalog.intense}
          </label>
        </div>
        <div className="form-actions">
          <button type="button" className="btn primary" disabled={!canAdd} onClick={add}>
            {t.catalog.add}
          </button>
          {duplicate ? <span className="muted">{t.catalog.duplicate}</span> : null}
        </div>
      </section>
    </div>
  );
}

/**
 * Dose in the console's current language. Empty in every language means
 * "pending protocol" (null): the tool never invents a dose.
 */
function DoseInput({
  value,
  placeholder,
  onChange,
}: {
  value: CatalogItem["dose"];
  placeholder: string;
  onChange: (dose: CatalogItem["dose"]) => void;
}) {
  const { locale, tx, t } = useI18n();
  const own = value?.[locale] ?? "";
  const fallback = own === "" ? tx(value) : "";
  return (
    <input
      value={own}
      placeholder={fallback || placeholder}
      title={fallback ? t.common.fallbackHint : undefined}
      className={fallback ? "missing-translation" : undefined}
      onChange={(e) => {
        const next = withText(value, locale, e.target.value);
        onChange(Object.values(next).some((v) => v?.trim()) ? next : null);
      }}
    />
  );
}
