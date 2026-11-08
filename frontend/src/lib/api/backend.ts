import "server-only";

/** A failed call to the backend API, with the status and the API's own explanation. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    /** Check codes from a 422 response (why a listing is not ready). */
    readonly problems: string[] = [],
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
  if (!response.ok) throw await failure(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

async function failure(response: Response): Promise<ApiError> {
  const problem = (await response.json().catch(() => null)) as {
    detail?: unknown;
    problems?: unknown;
  } | null;
  const detail =
    typeof problem?.detail === "string" ? problem.detail : response.statusText;
  const problems = Array.isArray(problem?.problems)
    ? problem.problems.filter((p): p is string => typeof p === "string")
    : [];
  return new ApiError(response.status, detail, problems);
}

/** Public, anonymous reads (offer search): no token is sent. */
export async function callPublic<T>(path: string): Promise<T> {
  const response = await fetch(`${backendUrl()}${path}`, {
    cache: "no-store",
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw await failure(response);
  return (await response.json()) as T;
}

/** A public photo, without a token. */
export async function fetchPublicBinary(
  path: string,
): Promise<{ bytes: ArrayBuffer; contentType: string }> {
  const response = await fetch(`${backendUrl()}${path}`, {
    cache: "no-store",
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (!response.ok) throw await failure(response);
  return {
    bytes: await response.arrayBuffer(),
    contentType:
      response.headers.get("Content-Type") ?? "application/octet-stream",
  };
}

/** Photo uploads take longer than JSON calls. */
const UPLOAD_TIMEOUT_MS = 30_000;

/** Sends multipart form data (a photo upload) as the signed-in person. */
export async function callBackendMultipart<T>(
  token: string,
  path: string,
  body: FormData,
): Promise<T> {
  const response = await fetch(`${backendUrl()}${path}`, {
    method: "POST",
    cache: "no-store",
    signal: AbortSignal.timeout(UPLOAD_TIMEOUT_MS),
    headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
    body,
  });
  if (!response.ok) throw await failure(response);
  return (await response.json()) as T;
}

/** Fetches a binary response (a photo) as the signed-in person. */
export async function fetchBackendBinary(
  token: string,
  path: string,
): Promise<{ bytes: ArrayBuffer; contentType: string }> {
  const response = await fetch(`${backendUrl()}${path}`, {
    cache: "no-store",
    signal: AbortSignal.timeout(UPLOAD_TIMEOUT_MS),
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw await failure(response);
  return {
    bytes: await response.arrayBuffer(),
    contentType:
      response.headers.get("Content-Type") ?? "application/octet-stream",
  };
}
