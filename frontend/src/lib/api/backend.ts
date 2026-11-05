import "server-only";

/** A failed call to the backend API, with the status and the API's own explanation. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const TIMEOUT_MS = 5000;

function backendUrl(): string {
  return process.env.BACKEND_URL ?? "http://localhost:8080";
}

/**
 * Calls the Spring Boot API as the signed-in person. Server-only: the token never reaches the
 * browser. Throws {@link ApiError} for any non-2xx response.
 */
export async function callBackend<T>(
  token: string,
  path: string,
  init: { method?: string; body?: unknown } = {},
): Promise<T> {
  const response = await fetch(`${backendUrl()}${path}`, {
    method: init.method ?? "GET",
    cache: "no-store",
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...(init.body !== undefined && { "Content-Type": "application/json" }),
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: unknown } | null;
    const detail = typeof problem?.detail === "string" ? problem.detail : response.statusText;
    throw new ApiError(response.status, detail);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
