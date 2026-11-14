import { parseAdaptationInput } from "@/lib/adaptations";
import {
  latestPackageVersion,
  listAdaptations,
  receiveAdaptations,
  reviewAdaptation,
} from "@/lib/store";
import type { AdaptationInput, ReviewStatus } from "@/lib/types";

// Adaptaciones del plan decididas por la interfaz del astronauta.
//
//   POST  /api/adaptations   La interfaz envía una adaptación o una lista. Ya
//                            están aplicadas a bordo: el envío no bloquea y el
//                            servidor no las rechaza por su contenido, solo las
//                            registra y señala las que se salen de la política.
//   GET   /api/adaptations   Registro completo; ?crewId= filtra por tripulante.
//                            La interfaz lo usa para conocer el resultado de la
//                            revisión.
//   PATCH /api/adaptations   Control de misión valida u objeta una adaptación.
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};
const REVIEW_STATUSES: ReviewStatus[] = ["pendiente", "validada", "objetada"];

export async function OPTIONS() {
  return new Response(null, { status: 204, headers: CORS });
}

export async function GET(request: Request) {
  const crewId = new URL(request.url).searchParams.get("crewId");
  const all = await listAdaptations();
  return Response.json(
    {
      adaptations: crewId ? all.filter((a) => a.crewId === crewId) : all,
      latestPackageVersion: await latestPackageVersion(),
    },
    { headers: CORS },
  );
}

export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "JSON no válido" }, { status: 400, headers: CORS });
  }
  const inputs: AdaptationInput[] = [];
  for (const raw of Array.isArray(body) ? body : [body]) {
    const parsed = parseAdaptationInput(raw);
    if (typeof parsed === "string") {
      return Response.json({ error: parsed }, { status: 400, headers: CORS });
    }
    inputs.push(parsed);
  }
  const adaptations = await receiveAdaptations(inputs);
  return Response.json({ adaptations }, { status: 202, headers: CORS });
}

export async function PATCH(request: Request) {
  const body = (await request.json()) as { id?: string; status?: ReviewStatus; note?: string };
  if (!body.id || !body.status || !REVIEW_STATUSES.includes(body.status)) {
    return Response.json({ error: "Faltan id o status" }, { status: 400 });
  }
  const adaptation = await reviewAdaptation(body.id, body.status, body.note?.trim() ?? "");
  if (!adaptation) return Response.json({ error: "Adaptación no encontrada" }, { status: 404 });
  return Response.json({ adaptation });
}
