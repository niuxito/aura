import { promises as fs } from "fs";
import path from "path";
import { neon } from "@neondatabase/serverless";

// JSON document store, organized in collections.
// - With DATABASE_URL (Vercel deployment): aura_documents table in Postgres.
// - Without it (local development): files in data/<collection>/<name>.json.

const DATA_DIR = path.join(process.cwd(), "data");
const databaseUrl = process.env.DATABASE_URL;
const sql = databaseUrl ? neon(databaseUrl) : null;

const fileOf = (collection: string, name: string) =>
  path.join(DATA_DIR, collection, `${encodeURIComponent(name)}.json`);

const isMissing = (err: unknown) => (err as NodeJS.ErrnoException).code === "ENOENT";

export async function getDoc<T>(collection: string, name: string): Promise<T | null> {
  if (sql) {
    const rows = await sql`select value from aura_documents where key = ${`${collection}:${name}`}`;
    return rows.length > 0 ? (rows[0].value as T) : null;
  }
  try {
    return JSON.parse(await fs.readFile(fileOf(collection, name), "utf8")) as T;
  } catch (err) {
    if (isMissing(err)) return null;
    throw err;
  }
}

export async function setDoc(collection: string, name: string, value: unknown): Promise<void> {
  if (sql) {
    await sql`
      insert into aura_documents (key, value, updated_at)
      values (${`${collection}:${name}`}, ${JSON.stringify(value)}::jsonb, now())
      on conflict (key) do update set value = excluded.value, updated_at = now()`;
    return;
  }
  const file = fileOf(collection, name);
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, JSON.stringify(value, null, 2), "utf8");
}

/** Names of the documents in a collection. */
export async function listDocs(collection: string): Promise<string[]> {
  if (sql) {
    const prefix = `${collection}:`;
    const rows = await sql`
      select key from aura_documents where starts_with(key, ${prefix})`;
    return rows.map((r) => (r.key as string).slice(prefix.length));
  }
  try {
    const files = await fs.readdir(path.join(DATA_DIR, collection));
    return files
      .filter((f) => f.endsWith(".json"))
      .map((f) => decodeURIComponent(f.slice(0, -".json".length)));
  } catch (err) {
    if (isMissing(err)) return [];
    throw err;
  }
}
