import { parseAlertInput } from "@/lib/alerts";
import { legacyAlertStatus } from "@/lib/legacy";
import { acknowledgeAlert, listAlerts, receiveAlerts } from "@/lib/store";
import type { AlertInput, AlertStatus } from "@/lib/types";

// Alerts from the astronaut interface: what is not resolved by adapting the plan.
//
//   POST  /api/alerts   The interface sends one alert or a list. Non-blocking: the
//                       server always records them; resending the same id does not
//                       duplicate. v4 (Spanish) codes are still accepted.
//   GET   /api/alerts   Full record; ?crewId= filters by crew member. The interface
//                       uses it to learn whether mission control has seen or handled it.
//   PATCH /api/alerts   Mission control marks an alert as seen or handled.
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};
const ACK_STATUSES: AlertStatus[] = ["new", "seen", "handled"];

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
    return Response.json({ error: "Invalid JSON" }, { status: 400, headers: CORS });
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
  const body = (await request.json()) as { id?: string; status?: string; note?: string };
  const status = legacyAlertStatus(body.status) as AlertStatus;
  if (!body.id || !ACK_STATUSES.includes(status)) {
    return Response.json({ error: "Missing id or status" }, { status: 400 });
  }
  const alert = await acknowledgeAlert(body.id, status, body.note?.trim() ?? "");
  if (!alert) return Response.json({ error: "Alert not found" }, { status: 404 });
  return Response.json({ alert });
}
