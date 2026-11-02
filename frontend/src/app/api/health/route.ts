import { connection } from "next/server";
import { checkBackendHealth } from "@/lib/backend-health";

export async function GET() {
  // Always evaluate at request time; health must never be served from cache.
  await connection();
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const summary = await checkBackendHealth(backendUrl);
  return Response.json(summary, {
    headers: { "Cache-Control": "no-store" },
  });
}
