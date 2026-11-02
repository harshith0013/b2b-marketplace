export type ServiceState = "UP" | "DOWN" | "UNREACHABLE";

export interface HealthSummary {
  frontend: "UP";
  backend: ServiceState;
}

/**
 * Asks the Spring Boot backend for its health. Only the overall status is
 * passed on; component details never leave the server.
 */
export async function checkBackendHealth(
  backendBaseUrl: string,
  fetchImpl: typeof fetch = fetch,
  timeoutMs = 2000,
): Promise<HealthSummary> {
  try {
    const response = await fetchImpl(`${backendBaseUrl}/actuator/health`, {
      cache: "no-store",
      signal: AbortSignal.timeout(timeoutMs),
      headers: { Accept: "application/json" },
    });
    if (!response.ok) return { frontend: "UP", backend: "DOWN" };
    const body = (await response.json()) as { status?: unknown };
    return { frontend: "UP", backend: body.status === "UP" ? "UP" : "DOWN" };
  } catch {
    return { frontend: "UP", backend: "UNREACHABLE" };
  }
}
