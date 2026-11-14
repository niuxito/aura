import { createHash } from "crypto";
import { buildPackageBody, canonicalJson, type MissionPackage } from "./package";
import { createSeedState } from "./seed";
import { checkPolicy } from "./adaptations";
import { getDoc, listDocs, setDoc } from "./storage";
import {
  SCHEMA_VERSION,
  type Adaptation,
  type AdaptationInput,
  type AppState,
  type PackageSummary,
  type ReviewStatus,
} from "./types";

// Estado, paquetes y adaptaciones de AURA Manager. Dónde se guardan lo decide
// lib/storage.ts: ficheros en local, Postgres en el despliegue.

const readJson = <T>(collection: string, name: string) => getDoc<T>(collection, name);
const STATE = ["state", "current"] as const;

export async function readState(): Promise<AppState> {
  const saved = await readJson<AppState>(...STATE);
  // Un estado de un esquema anterior no se migra: se sustituye por la demo.
  if (saved?.schemaVersion === SCHEMA_VERSION) return saved;
  const seed = createSeedState();
  await setDoc(...STATE, seed);
  return seed;
}

export async function writeState(state: AppState): Promise<AppState> {
  const next = { ...state, updatedAt: new Date().toISOString() };
  await setDoc(...STATE, next);
  return next;
}

export async function resetState(): Promise<AppState> {
  const seed = createSeedState();
  await setDoc(...STATE, seed);
  return seed;
}

async function packageVersions(): Promise<number[]> {
  return (await listDocs("packages"))
    .map((name) => /^v(\d+)$/.exec(name)?.[1])
    .filter((v): v is string => v !== undefined)
    .map(Number)
    .sort((a, b) => a - b);
}

export async function readPackage(version?: number): Promise<MissionPackage | null> {
  const versions = await packageVersions();
  const target = version ?? versions.at(-1);
  if (target === undefined || !versions.includes(target)) return null;
  return readJson<MissionPackage>("packages", `v${target}`);
}

export async function listPackages(): Promise<PackageSummary[]> {
  const versions = await packageVersions();
  const packages = await Promise.all(versions.map((v) => readPackage(v)));
  return packages
    .filter((p): p is MissionPackage => p !== null)
    .map((p) => ({
      packageVersion: p.packageVersion,
      publishedAt: p.publishedAt,
      checksum: p.checksum,
      missionName: p.mission.name,
      schema: p.schema,
    }))
    .reverse();
}

export async function publishPackage(state: AppState): Promise<MissionPackage> {
  const versions = await packageVersions();
  const version = (versions.at(-1) ?? 0) + 1;
  const body = buildPackageBody(state, version, new Date().toISOString());
  const checksum =
    "sha256:" + createHash("sha256").update(canonicalJson(body)).digest("hex");
  const pkg: MissionPackage = { ...body, checksum };
  await setDoc("packages", `v${version}`, pkg);
  return pkg;
}

// ---------- Adaptaciones recibidas de la interfaz del astronauta ----------

export async function latestPackageVersion(): Promise<number | null> {
  return (await packageVersions()).at(-1) ?? null;
}

export async function listAdaptations(): Promise<Adaptation[]> {
  const names = await listDocs("adaptations");
  const all = await Promise.all(names.map((n) => readJson<Adaptation>("adaptations", n)));
  return all
    .filter((a): a is Adaptation => a !== null)
    .sort((a, b) => a.receivedAt.localeCompare(b.receivedAt));
}

/**
 * Registra adaptaciones. Nunca las rechaza por su contenido: las que se salen
 * de la política quedan marcadas para que control de misión las revise.
 * Reenviar una adaptación ya registrada (mismo id) no la duplica.
 */
export async function receiveAdaptations(inputs: AdaptationInput[]): Promise<Adaptation[]> {
  const all = await listAdaptations();
  const result: Adaptation[] = [];
  for (const input of inputs) {
    const existing = all.find((a) => a.id === input.id);
    if (existing) {
      result.push(existing);
      continue;
    }
    const pkg = await readPackage(input.packageVersion);
    const { original, policy } = checkPolicy(pkg, input);
    const adaptation: Adaptation = {
      ...input,
      receivedAt: new Date().toISOString(),
      original,
      policy,
      review: { status: "pendiente", note: "", reviewedAt: null },
    };
    await setDoc("adaptations", adaptation.id, adaptation);
    all.push(adaptation);
    result.push(adaptation);
  }
  return result;
}

export async function reviewAdaptation(
  id: string,
  status: ReviewStatus,
  note: string,
): Promise<Adaptation | null> {
  const all = await listAdaptations();
  const target = all.find((a) => a.id === id);
  if (!target) return null;
  target.review = {
    status,
    note,
    reviewedAt: status === "pendiente" ? null : new Date().toISOString(),
  };
  await setDoc("adaptations", target.id, target);
  return target;
}
