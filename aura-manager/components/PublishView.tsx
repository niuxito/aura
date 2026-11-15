"use client";

import { useEffect, useState } from "react";
import { languageList } from "@/lib/i18n";
import { formatFlightDay } from "@/lib/met";
import { countBySeverity } from "@/lib/rules";
import type { AppState, Issue, PackageSummary } from "@/lib/types";
import { useI18n } from "./I18n";

const SEVERITY_ORDER: Issue["severity"][] = ["blocker", "warning", "info"];

interface Props {
  state: AppState;
  issues: Issue[];
  onSelectDay: (day: number) => void;
}

export default function PublishView({ state, issues, onSelectDay }: Props) {
  const { t, tx, locale, formatDate } = useI18n();
  const [packages, setPackages] = useState<PackageSummary[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const counts = countBySeverity(issues);

  useEffect(() => {
    let cancelled = false;
    fetch("/api/package?list=1")
      .then((r) => r.json())
      .then((list: PackageSummary[]) => {
        if (!cancelled) setPackages(list);
      })
      .catch(() => {
        if (!cancelled) setError("list");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const publish = async () => {
    setBusy(true);
    setError(null);
    try {
      const res = await fetch("/api/publish", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(state),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "");
      setPackages(data.packages);
    } catch (err) {
      setError(err instanceof Error && err.message ? err.message : "publish");
    } finally {
      setBusy(false);
    }
  };

  const crewName = (id: string | null) => state.crew.find((c) => c.id === id)?.name;
  const ordered = [...issues].sort(
    (a, b) =>
      SEVERITY_ORDER.indexOf(a.severity) - SEVERITY_ORDER.indexOf(b.severity) || (a.day ?? 0) - (b.day ?? 0),
  );

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>{t.publish.validationTitle}</h2>
          <div className="counts">
            <span className="count sev-blocker">{t.publish.blockers(counts.blocker)}</span>
            <span className="count sev-warning">{t.publish.warnings(counts.warning)}</span>
            <span className="count sev-info">{t.publish.infos(counts.info)}</span>
          </div>
        </div>
        {ordered.length === 0 ? (
          <p className="muted">{t.publish.noIssues}</p>
        ) : (
          <ul className="issues">
            {ordered.map((issue) => (
              <li key={issue.id} className={`issue sev-${issue.severity}`}>
                <span className="issue-tag">{t.labels.severity[issue.severity]}</span>
                <span>
                  {issue.day !== null ? (
                    <button type="button" className="link" onClick={() => onSelectDay(issue.day!)}>
                      {formatFlightDay(issue.day)}
                    </button>
                  ) : (
                    <em>{t.publish.missionScope}</em>
                  )}
                  {crewName(issue.crewId) ? ` · ${crewName(issue.crewId)}` : ""} — {tx(issue.message)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>{t.publish.title}</h2>
          <button
            type="button"
            className="btn primary"
            disabled={busy || counts.blocker > 0}
            onClick={publish}
          >
            {busy ? t.publish.publishing : t.publish.publishVersion((packages?.[0]?.packageVersion ?? 0) + 1)}
          </button>
        </div>
        <p className="muted">
          {counts.blocker > 0 ? t.publish.resolveBlockers : t.publish.warningsTravel}{" "}
          {t.publish.monitorBefore} <code>/api/package</code> {t.publish.monitorAfter}{" "}
          {t.publish.languages(languageList(locale))}
        </p>
        {error ? (
          <p className="error">
            {error === "list" ? t.publish.listError : error === "publish" ? t.publish.publishError : error}
          </p>
        ) : null}

        {packages === null ? (
          <p className="muted">{t.publish.loadingVersions}</p>
        ) : packages.length === 0 ? (
          <p className="muted">{t.publish.noPackages}</p>
        ) : (
          <table className="packages-table">
            <thead>
              <tr>
                <th>{t.publish.colVersion}</th>
                <th>{t.publish.colPublished}</th>
                <th>{t.publish.colMission}</th>
                <th>{t.publish.colSchema}</th>
                <th>{t.publish.colChecksum}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {packages.map((p) => (
                <tr key={p.packageVersion}>
                  <td className="num">v{p.packageVersion}</td>
                  <td>{formatDate(p.publishedAt, { dateStyle: "medium", timeStyle: "short" })}</td>
                  <td>{typeof p.missionName === "string" ? p.missionName : tx(p.missionName)}</td>
                  <td>
                    <code>{p.schema.split("/")[1]}</code>
                  </td>
                  <td>
                    <code>{p.checksum.slice(0, 23)}…</code>
                  </td>
                  <td>
                    <a
                      href={`/api/package?version=${p.packageVersion}`}
                      download={`aura-mission-package-v${p.packageVersion}.json`}
                    >
                      {t.publish.download}
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
