import { readState, resetState, writeState } from "@/lib/store";
import { SCHEMA_VERSION, type AppState } from "@/lib/types";

export async function GET() {
  return Response.json(await readState());
}

export async function PUT(request: Request) {
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
  return Response.json(await writeState(state));
}

/** Restaura la misión demo. */
export async function DELETE() {
  return Response.json(await resetState());
}
