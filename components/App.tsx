"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { countBySeverity, validate } from "@/lib/rules";
import type { Adaptation, AppState } from "@/lib/types";
import AdaptationsView from "./AdaptationsView";
import CatalogView from "./CatalogView";
import GravityStrip from "./GravityStrip";
import MissionView from "./MissionView";
import PreventionView from "./PreventionView";
import PublishView from "./PublishView";
import QuestionnaireView from "./QuestionnaireView";

type Tab = "mision" | "prevencion" | "catalogo" | "cuestionario" | "publicar" | "adaptaciones";
type SaveStatus = "guardado" | "guardando" | "error";

const TABS: { id: Tab; label: string }[] = [
  { id: "mision", label: "Plan de misión" },
  { id: "prevencion", label: "Plan de prevención" },
  { id: "catalogo", label: "Catálogo" },
  { id: "cuestionario", label: "Cuestionario" },
  { id: "publicar", label: "Publicar" },
  { id: "adaptaciones", label: "Adaptaciones" },
];

const SAVE_DELAY_MS = 500;
/** Cada cuánto se consulta si la interfaz ha enviado adaptaciones nuevas. */
const ADAPTATIONS_POLL_MS = 15000;

/** Permite enlazar a una pestaña concreta con ?tab=adaptaciones. */
function initialTab(): Tab {
  if (typeof window === "undefined") return "prevencion";
  const requested = new URLSearchParams(window.location.search).get("tab");
  return TABS.some((t) => t.id === requested) ? (requested as Tab) : "prevencion";
}

/** Permite enlazar a un día de vuelo concreto con ?fd=2. */
function initialDay(): number {
  if (typeof window === "undefined") return 1;
  const requested = Number(new URLSearchParams(window.location.search).get("fd"));
  return Number.isInteger(requested) && requested > 0 ? requested : 1;
}

interface AdaptationsResponse {
  adaptations: Adaptation[];
  latestPackageVersion: number | null;
}

/** null si el servidor no responde: se reintenta en la siguiente consulta. */
async function fetchAdaptations(): Promise<AdaptationsResponse | null> {
  try {
    const res = await fetch("/api/adaptations");
    return res.ok ? ((await res.json()) as AdaptationsResponse) : null;
  } catch {
    return null;
  }
}

export default function App() {
  const [state, setState] = useState<AppState | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [tab, setTab] = useState<Tab>(initialTab);
  const [selectedDay, setSelectedDay] = useState(initialDay);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("guardado");
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [adaptations, setAdaptations] = useState<Adaptation[]>([]);
  const [latestPackageVersion, setLatestPackageVersion] = useState<number | null>(null);

  const applyAdaptations = useCallback((data: AdaptationsResponse | null) => {
    if (!data) return;
    setAdaptations(data.adaptations);
    setLatestPackageVersion(data.latestPackageVersion);
  }, []);

  const refreshAdaptations = useCallback(
    () => fetchAdaptations().then(applyAdaptations),
    [applyAdaptations],
  );

  useEffect(() => {
    const load = () => void fetchAdaptations().then(applyAdaptations);
    load();
    const interval = setInterval(load, ADAPTATIONS_POLL_MS);
    return () => clearInterval(interval);
  }, [applyAdaptations]);

  useEffect(() => {
    fetch("/api/state")
      .then((r) => {
        if (!r.ok) throw new Error();
        return r.json();
      })
      .then(setState)
      .catch(() => setLoadError(true));
  }, []);

  const save = useCallback((next: AppState) => {
    if (timer.current) clearTimeout(timer.current);
    setSaveStatus("guardando");
    timer.current = setTimeout(() => {
      fetch("/api/state", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(next),
      })
        .then((r) => setSaveStatus(r.ok ? "guardado" : "error"))
        .catch(() => setSaveStatus("error"));
    }, SAVE_DELAY_MS);
  }, []);

  const update = useCallback(
    (fn: (s: AppState) => AppState) => {
      if (!state) return;
      const next = fn(state);
      if (next === state) return;
      setState(next);
      save(next);
    },
    [state, save],
  );

  const reset = async () => {
    if (!window.confirm("¿Restaurar la misión demo? Se perderán los cambios del borrador.")) return;
    if (timer.current) clearTimeout(timer.current);
    const res = await fetch("/api/state", { method: "DELETE" });
    if (res.ok) {
      setState(await res.json());
      setSaveStatus("guardado");
    } else {
      setSaveStatus("error");
    }
  };

  const issues = useMemo(() => (state ? validate(state) : []), [state]);

  if (loadError) return <main className="loading">No se pudo cargar el plan de misión.</main>;
  if (!state) return <main className="loading">Cargando plan de misión…</main>;

  const counts = countBySeverity(issues);
  const pendingAdaptations = adaptations.filter((a) => a.review.status === "pendiente").length;
  const goToDay = (day: number) => {
    setSelectedDay(day);
    setTab("prevencion");
  };

  return (
    <>
      <header className="topbar">
        <div className="brand">
          <span className="wordmark">AURA</span>
          <span className="brand-sub">Control de misión · plan de prevención</span>
        </div>
        <div className="mission-meta">
          <strong>{state.mission.name}</strong>
          <span className="muted">
            {state.mission.destination} · {state.days.length} días de vuelo · {state.crew.length} tripulantes
          </span>
        </div>
        <div className="topbar-right">
          <span className={`save save-${saveStatus}`}>
            {saveStatus === "guardado"
              ? "Guardado"
              : saveStatus === "guardando"
                ? "Guardando…"
                : "Error al guardar"}
          </span>
          <button type="button" className="btn ghost" onClick={reset}>
            Restaurar demo
          </button>
        </div>
      </header>

      <div className="sim-banner">
        Cronograma: {state.mission.timelineSource}. Tiempos en MET (tiempo transcurrido desde el
        despegue, DD/HH:MM).
        {state.mission.simulated
          ? " La carga operativa, la tripulación y el plan de prevención son un ejemplo del equipo: no son datos de NASA ni una prescripción médica."
          : ""}
      </div>

      <nav className="tabs">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            className={`tab${tab === t.id ? " active" : ""}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
            {t.id === "publicar" && counts.bloqueo + counts.aviso > 0 ? (
              <span className={`pill ${counts.bloqueo > 0 ? "sev-bloqueo" : "sev-aviso"}`}>
                {counts.bloqueo + counts.aviso}
              </span>
            ) : null}
            {t.id === "adaptaciones" && pendingAdaptations > 0 ? (
              <span className="pill sev-aviso">{pendingAdaptations}</span>
            ) : null}
          </button>
        ))}
      </nav>

      <main className="content">
        {tab === "mision" || tab === "prevencion" ? (
          <GravityStrip
            days={state.days}
            issues={issues}
            selectedDay={selectedDay}
            onSelect={setSelectedDay}
          />
        ) : null}
        {tab === "mision" ? (
          <MissionView
            state={state}
            issues={issues}
            selectedDay={selectedDay}
            onSelect={setSelectedDay}
            update={update}
          />
        ) : null}
        {tab === "prevencion" ? (
          <PreventionView
            state={state}
            issues={issues}
            selectedDay={selectedDay}
            onSelect={setSelectedDay}
            update={update}
          />
        ) : null}
        {tab === "catalogo" ? <CatalogView state={state} update={update} /> : null}
        {tab === "cuestionario" ? <QuestionnaireView state={state} /> : null}
        {tab === "adaptaciones" ? (
          <AdaptationsView
            state={state}
            adaptations={adaptations}
            latestPackageVersion={latestPackageVersion}
            refresh={refreshAdaptations}
          />
        ) : null}
        {tab === "publicar" ? (
          <PublishView state={state} issues={issues} onSelectDay={goToDay} />
        ) : null}
      </main>
    </>
  );
}
