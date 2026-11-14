import { listPackages, readPackage } from "@/lib/store";

// Entry point for the astronaut monitor:
//   GET /api/package            → latest published package
//   GET /api/package?version=2  → a specific version
//   GET /api/package?list=1     → summary of published versions
const CORS = { "Access-Control-Allow-Origin": "*" };

export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  if (params.has("list")) {
    return Response.json(await listPackages(), { headers: CORS });
  }
  const raw = params.get("version");
  const version = raw === null ? undefined : Number(raw);
  if (version !== undefined && !Number.isInteger(version)) {
    return Response.json({ error: "Invalid version" }, { status: 400, headers: CORS });
  }
  const pkg = await readPackage(version);
  if (!pkg) {
    return Response.json(
      { error: "No package has been published with that version" },
      { status: 404, headers: CORS },
    );
  }
  return Response.json(pkg, { headers: CORS });
}
