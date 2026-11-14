import { validate } from "@/lib/rules";
import { listPackages, publishPackage, writeState } from "@/lib/store";
import { SCHEMA_VERSION, type AppState } from "@/lib/types";

/** Saves the received state, validates it and publishes a new package version. */
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
    return Response.json({ error: "Invalid state" }, { status: 400 });
  }
  const blockers = validate(state).filter((i) => i.severity === "blocker");
  if (blockers.length > 0) {
    return Response.json(
      { error: "The plan has unresolved blockers", blockers },
      { status: 409 },
    );
  }
  const saved = await writeState(state);
  const pkg = await publishPackage(saved);
  return Response.json({ package: pkg, packages: await listPackages() });
}
