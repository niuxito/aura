"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { LOCALES, LOCALE_NAMES, isLocale, type Locale } from "@/lib/i18n";
import { countBySeverity, validate } from "@/lib/rules";
import type { Adaptation, Alert, AppState } from "@/lib/types";
import AdaptationsView from "./AdaptationsView";
import AlertsView from "./AlertsView";
import CatalogView from "./CatalogView";
import GravityStrip from "./GravityStrip";
import { I18nProvider, useI18n } from "./I18n";
import MissionView from "./MissionView";
import PreventionView from "./PreventionView";
import PublishView from "./PublishView";
import QuestionnaireView from "./QuestionnaireView";

const TABS = [
  "mission",
  "prevention",
  "catalog",
  "questionnaire",
  "publish",
  "adaptations",
  "alerts",
] as const;
type Tab = (typeof TABS)[number];
type SaveStatus = "saved" | "saving" | "error";

const SAVE_DELAY_MS = 500;
/** How often to check whether the interface has sent new adaptations or alerts. */
const ADAPTATIONS_POLL_MS = 15000;

/** Allows linking to a specific tab with ?tab=adaptations. */
function initialTab(): Tab {
  if (typeof window === "undefined") return "prevention";
  const requested = new URLSearchParams(window.location.search).get("tab");
  return (TABS as readonly string[]).includes(requested ?? "") ? (requested as Tab) : "prevention";
}

/** Allows linking to a specific flight day with ?fd=2. */
function initialDay(): number {
  if (typeof window === "undefined") return 1;
  const requested = Number(new URLSearchParams(window.location.search).get("fd"));
  return Number.isInteger(requested) && requested > 0 ? requested : 1;
}

interface AdaptationsResponse {
  adaptations: Adaptation[];
  latestPackageVersion: number | null;
}

/** null if the server does not answer: retried on the next poll. */
async function fetchAdaptations(): Promise<AdaptationsResponse | null> {
  try {
    const res = await fetch("/api/adaptations");
    return res.ok ? ((await res.json()) as AdaptationsResponse) : null;
  } catch {
    return null;
  }
}

/** null if the server does not answer: retried on the next poll. */
async function fetchAlerts(): Promise<Alert[] | null> {
  try {
    const res = await fetch("/api/alerts");
    return res.ok ? ((await res.json()) as { alerts: Alert[] }).alerts : null;
  } catch {
    return null;
  }
}

export default function App({ locale }: { locale: Locale }) {
  return (
    <I18nProvider initialLocale={locale}>
      <Console />
    </I18nProvider>
  );
}

function LanguageSwitch() {
  const { locale, setLocale, t } = useI18n();
  return (
    <select
      className="lang-switch"
      value={locale}
      aria-label={t.app.language}
      title={t.app.language}
      onChange={(e) => {
        if (isLocale(e.target.value)) setLocale(e.target.value);
      }}
    >
      {LOCALES.map((l) => (
        <option key={l} value={l}>
          {l.toUpperCase()} · {LOCALE_NAMES[l]}
        </option>
      ))}
    </select>
  );
}

function Console() {
  const { t, tx } = useI18n();
  const [state, setState] = useState<AppState | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [tab, setTab] = useState<Tab>(initialTab);
  const [selectedDay, setSelectedDay] = useState(initialDay);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("saved");
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [adaptations, setAdaptations] = useState<Adaptation[]>([]);
  const [latestPackageVersion, setLatestPackageVersion] = useState<number | null>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);

  const applyAdaptations = useCallback((data: AdaptationsResponse | null) => {
    if (!data) return;
    setAdaptations(data.adaptations);
    setLatestPackageVersion(data.latestPackageVersion);
  }, []);

  const refreshAdaptations = useCallback(
    () => fetchAdaptations().then(applyAdaptations),
    [applyAdaptations],
  );

  const refreshAlerts = useCallback(
    () =>
      fetchAlerts().then((data) => {
        if (data) setAlerts(data);
      }),
    [],
  );

  useEffect(() => {
    const load = () => {
      void fetchAdaptations().then(applyAdaptations);
      void refreshAlerts();
    };
    load();
    const interval = setInterval(load, ADAPTATIONS_POLL_MS);
    return () => clearInterval(interval);
  }, [applyAdaptations, refreshAlerts]);

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
    setSaveStatus("saving");
    timer.current = setTimeout(() => {
      fetch("/api/state", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(next),
      })
        .then((r) => setSaveStatus(r.ok ? "saved" : "error"))
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
    if (!window.confirm(t.app.resetConfirm)) return;
    if (timer.current) clearTimeout(timer.current);
    const res = await fetch("/api/state", { method: "DELETE" });
    if (res.ok) {
      setState(await res.json());
      setSaveStatus("saved");
    } else {
      setSaveStatus("error");
    }
  };

  const issues = useMemo(() => (state ? validate(state) : []), [state]);

  if (loadError) return <main className="loading">{t.app.loadError}</main>;
  if (!state) return <main className="loading">{t.app.loading}</main>;

  const counts = countBySeverity(issues);
  const pendingAdaptations = adaptations.filter((a) => a.review.status === "pending").length;
  const newAlerts = alerts.filter((a) => a.ack.status === "new");
  const newRedAlerts = newAlerts.filter((a) => a.level === "red").length;
  const goToDay = (day: number) => {
    setSelectedDay(day);
    setTab("prevention");
  };

  return (
    <>
      <header className="topbar">
        <div className="brand">
          <span className="wordmark">AURA</span>
          <span className="brand-sub">{t.app.brandSub}</span>
        </div>
        <div className="mission-meta">
          <strong>{tx(state.mission.name)}</strong>
          <span className="muted">
            {t.app.missionMeta(tx(state.mission.destination), state.days.length, state.crew.length)}
          </span>
        </div>
        <div className="topbar-right">
          <span className={`save save-${saveStatus}`}>
            {saveStatus === "saved" ? t.app.saved : saveStatus === "saving" ? t.app.saving : t.app.saveError}
          </span>
          <LanguageSwitch />
          <button type="button" className="btn ghost" onClick={reset}>
            {t.app.resetDemo}
          </button>
        </div>
      </header>

      <div className="sim-banner">
        {t.app.banner(state.mission.timelineSource)}
        {state.mission.simulated ? t.app.bannerSimulated : ""}
      </div>

      <nav className="tabs">
        {TABS.map((id) => (
          <button
            key={id}
            type="button"
            className={`tab${tab === id ? " active" : ""}`}
            onClick={() => setTab(id)}
          >
            {t.tabs[id]}
            {id === "publish" && counts.blocker + counts.warning > 0 ? (
              <span className={`pill ${counts.blocker > 0 ? "sev-blocker" : "sev-warning"}`}>
                {counts.blocker + counts.warning}
              </span>
            ) : null}
            {id === "adaptations" && pendingAdaptations > 0 ? (
              <span className="pill sev-warning">{pendingAdaptations}</span>
            ) : null}
            {id === "alerts" && newAlerts.length > 0 ? (
              <span className={`pill ${newRedAlerts > 0 ? "sev-blocker" : "sev-warning"}`}>
                {newAlerts.length}
              </span>
            ) : null}
          </button>
        ))}
      </nav>

      <main className="content">
        {tab === "mission" || tab === "prevention" ? (
          <GravityStrip
            days={state.days}
            issues={issues}
            selectedDay={selectedDay}
            onSelect={setSelectedDay}
          />
        ) : null}
        {tab === "mission" ? (
          <MissionView
            state={state}
            issues={issues}
            selectedDay={selectedDay}
            onSelect={setSelectedDay}
            update={update}
          />
        ) : null}
        {tab === "prevention" ? (
          <PreventionView
            state={state}
            issues={issues}
            selectedDay={selectedDay}
            onSelect={setSelectedDay}
            update={update}
          />
        ) : null}
        {tab === "catalog" ? <CatalogView state={state} update={update} /> : null}
        {tab === "questionnaire" ? <QuestionnaireView state={state} /> : null}
        {tab === "adaptations" ? (
          <AdaptationsView
            state={state}
            adaptations={adaptations}
            latestPackageVersion={latestPackageVersion}
            refresh={refreshAdaptations}
          />
        ) : null}
        {tab === "alerts" ? <AlertsView state={state} alerts={alerts} refresh={refreshAlerts} /> : null}
        {tab === "publish" ? (
          <PublishView state={state} issues={issues} onSelectDay={goToDay} />
        ) : null}
      </main>
    </>
  );
}
