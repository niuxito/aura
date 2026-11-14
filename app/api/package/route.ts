import { listPackages, readPackage } from "@/lib/store";

// Punto de entrada del monitor del astronauta:
//   GET /api/package            → último paquete publicado
//   GET /api/package?version=2  → una versión concreta
//   GET /api/package?list=1     → resumen de versiones publicadas
const CORS = { "Access-Control-Allow-Origin": "*" };

export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  if (params.has("list")) {
    return Response.json(await listPackages(), { headers: CORS });
  }
  const raw = params.get("version");
  const version = raw === null ? undefined : Number(raw);
  if (version !== undefined && !Number.isInteger(version)) {
    return Response.json({ error: "Versión no válida" }, { status: 400, headers: CORS });
  }
  const pkg = await readPackage(version);
  if (!pkg) {
    return Response.json(
      { error: "No hay ningún paquete publicado con esa versión" },
      { status: 404, headers: CORS },
    );
  }
  return Response.json(pkg, { headers: CORS });
}
