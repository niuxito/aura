import { parseAlertInput } from "@/lib/alerts";
import { acknowledgeAlert, listAlerts, receiveAlerts } from "@/lib/store";
import type { AlertInput, AlertStatus } from "@/lib/types";

// Alertas de la interfaz del astronauta: lo que no se resuelve adaptando el plan.
//
//   POST  /api/alerts   La interfaz envía una alerta o una lista. No bloquea: el
//                       servidor las registra siempre; reenviar el mismo id no duplica.
//   GET   /api/alerts   Registro completo; ?crewId= filtra por tripulante. La interfaz
//                       lo usa para saber si control de misión la ha visto o atendido.
//   PATCH /api/alerts   Control de misión marca una alerta como vista o atendida.
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};
const ACK_STATUSES: AlertStatus[] = ["nueva", "vista", "atendida"];

export async function OPTIONS() {
  return new Response(null, { status: 204, headers: CORS });
}

export async function GET(request: Request) {
  const crewId = new URL(request.url).searchParams.get("crewId");
  const all = await listAlerts();
  return Response.json(
    { alerts: crewId ? all.filter((a) => a.crewId === crewId) : all },
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
  const inputs: AlertInput[] = [];
  for (const raw of Array.isArray(body) ? body : [body]) {
    const parsed = parseAlertInput(raw);
    if (typeof parsed === "string") {
      return Response.json({ error: parsed }, { status: 400, headers: CORS });
    }
    inputs.push(parsed);
  }
  const alerts = await receiveAlerts(inputs);
  return Response.json({ alerts }, { status: 202, headers: CORS });
}

export async function PATCH(request: Request) {
  const body = (await request.json()) as { id?: string; status?: AlertStatus; note?: string };
  if (!body.id || !body.status || !ACK_STATUSES.includes(body.status)) {
    return Response.json({ error: "Faltan id o status" }, { status: 400 });
  }
  const alert = await acknowledgeAlert(body.id, body.status, body.note?.trim() ?? "");
  if (!alert) return Response.json({ error: "Alerta no encontrada" }, { status: 404 });
  return Response.json({ alert });
}
