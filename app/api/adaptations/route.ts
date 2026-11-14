import { parseAdaptationInput } from "@/lib/adaptations";
import { legacyReviewStatus } from "@/lib/legacy";
import {
  latestPackageVersion,
  listAdaptations,
  receiveAdaptations,
  reviewAdaptation,
} from "@/lib/store";
import type { AdaptationInput, ReviewStatus } from "@/lib/types";

// Plan adaptations decided by the astronaut interface.
//
//   POST  /api/adaptations   The interface sends one adaptation or a list. They
//                            are already applied on board: sending does not
//                            block and the server never rejects them for their
//                            content; it records them and flags those outside
//                            the policy. v4 (Spanish) codes are still accepted.
//   GET   /api/adaptations   Full record; ?crewId= filters by crew member. The
//                            interface uses it to learn the review outcome.
//   PATCH /api/adaptations   Mission control validates or objects to one.
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};
const REVIEW_STATUSES: ReviewStatus[] = ["pending", "validated", "objected"];

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
    return Response.json({ error: "Invalid JSON" }, { status: 400, headers: CORS });
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
  const body = (await request.json()) as { id?: string; status?: string; note?: string };
  const status = legacyReviewStatus(body.status) as ReviewStatus;
  if (!body.id || !REVIEW_STATUSES.includes(status)) {
    return Response.json({ error: "Missing id or status" }, { status: 400 });
  }
  const adaptation = await reviewAdaptation(body.id, status, body.note?.trim() ?? "");
  if (!adaptation) return Response.json({ error: "Adaptation not found" }, { status: 404 });
  return Response.json({ adaptation });
}
