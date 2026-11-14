import { validate } from "@/lib/rules";
import { listPackages, publishPackage, writeState } from "@/lib/store";
import { SCHEMA_VERSION, type AppState } from "@/lib/types";

/** Guarda el estado recibido, lo valida y publica una nueva versión del paquete. */
export async function POST(request: Request) {
  const state = (await request.json()) as AppState;
  if (
    state?.schemaVersion !== SCHEMA_VERSION ||
    !state.mission ||
    !Array.isArray(state.days) ||
    !Array.isArray(state.plan) ||
    !state.questionnaire ||
    !Array.isArray(state.capacities) ||
    !Array.isArray(state.signals)
  ) {
    return Response.json({ error: "Estado no válido" }, { status: 400 });
  }
  const blockers = validate(state).filter((i) => i.severity === "bloqueo");
  if (blockers.length > 0) {
    return Response.json(
      { error: "El plan tiene bloqueos sin resolver", blockers },
      { status: 409 },
    );
  }
  const saved = await writeState(state);
  const pkg = await publishPackage(saved);
  return Response.json({ package: pkg, packages: await listPackages() });
}
