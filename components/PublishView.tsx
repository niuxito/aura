"use client";

import { useEffect, useState } from "react";
import { formatFlightDay } from "@/lib/met";
import { countBySeverity } from "@/lib/rules";
import type { AppState, Issue, PackageSummary } from "@/lib/types";

interface Props {
  state: AppState;
  issues: Issue[];
  onSelectDay: (day: number) => void;
}

const dateFormat = new Intl.DateTimeFormat("es-ES", { dateStyle: "medium", timeStyle: "short" });

export default function PublishView({ state, issues, onSelectDay }: Props) {
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
        if (!cancelled) setError("No se pudo leer la lista de paquetes.");
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
      if (!res.ok) throw new Error(data.error ?? "No se pudo publicar");
      setPackages(data.packages);
    } catch (err) {
      setError(err instanceof Error ? err.message : "No se pudo publicar");
    } finally {
      setBusy(false);
    }
  };

  const crewName = (id: string | null) => state.crew.find((c) => c.id === id)?.name;
  const ordered = [...issues].sort(
    (a, b) =>
      ["bloqueo", "aviso", "info"].indexOf(a.severity) -
        ["bloqueo", "aviso", "info"].indexOf(b.severity) || (a.day ?? 0) - (b.day ?? 0),
  );

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>Validación del plan</h2>
          <div className="counts">
            <span className="count sev-bloqueo">{counts.bloqueo} bloqueos</span>
            <span className="count sev-aviso">{counts.aviso} avisos</span>
            <span className="count sev-info">{counts.info} informativos</span>
          </div>
        </div>
        {ordered.length === 0 ? (
          <p className="muted">El plan no tiene avisos.</p>
        ) : (
          <ul className="issues">
            {ordered.map((issue) => (
              <li key={issue.id} className={`issue sev-${issue.severity}`}>
                <span className="issue-tag">{issue.severity}</span>
                <span>
                  {issue.day !== null ? (
                    <button type="button" className="link" onClick={() => onSelectDay(issue.day!)}>
                      {formatFlightDay(issue.day)}
                    </button>
                  ) : (
                    <em>Misión</em>
                  )}
                  {crewName(issue.crewId) ? ` · ${crewName(issue.crewId)}` : ""} — {issue.message}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Publicar paquete de misión</h2>
          <button
            type="button"
            className="btn primary"
            disabled={busy || counts.bloqueo > 0}
            onClick={publish}
          >
            {busy ? "Publicando…" : `Publicar versión ${(packages?.[0]?.packageVersion ?? 0) + 1}`}
          </button>
        </div>
        <p className="muted">
          {counts.bloqueo > 0
            ? "Resuelve los bloqueos para poder publicar."
            : "Los avisos no impiden publicar: viajan dentro del paquete para que el monitor los muestre."}{" "}
          El monitor del astronauta descarga el último paquete desde <code>/api/package</code> y
          trabaja sin conexión a partir de él.
        </p>
        {error ? <p className="error">{error}</p> : null}

        {packages === null ? (
          <p className="muted">Cargando versiones…</p>
        ) : packages.length === 0 ? (
          <p className="muted">Todavía no se ha publicado ningún paquete.</p>
        ) : (
          <table className="packages-table">
            <thead>
              <tr>
                <th>Versión</th>
                <th>Publicado</th>
                <th>Misión</th>
                <th>Esquema</th>
                <th>Checksum</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {packages.map((p) => (
                <tr key={p.packageVersion}>
                  <td className="num">v{p.packageVersion}</td>
                  <td>{dateFormat.format(new Date(p.publishedAt))}</td>
                  <td>{p.missionName}</td>
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
                      Descargar JSON
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
