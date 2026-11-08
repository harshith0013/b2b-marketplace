import "server-only";
import http from "node:http";
import type { CustomFetch } from "openid-client";

const NULL_BODY_STATUSES = new Set([101, 204, 205, 304]);

/**
 * Development only. Inside Docker Compose this server cannot reach the mock sign-in server at the
 * address the browser uses (http://localhost:8090). This sends those requests to the internal
 * address instead while keeping the public Host header, so the mock server still names the public
 * issuer in its tokens. Node's fetch does not allow setting Host, hence node:http.
 */
export function publicHostFetch(
  publicOrigin: URL,
  internalOrigin: URL,
): CustomFetch {
  return (url, init) => {
    const target = new URL(url);
    if (target.origin !== publicOrigin.origin)
      return fetch(url, init as RequestInit);

    const internal = new URL(target.pathname + target.search, internalOrigin);
    const body = init.body == null ? undefined : bodyToString(init.body);
    return new Promise((resolve, reject) => {
      const request = http.request(
        internal,
        {
          method: init.method,
          headers: { ...init.headers, host: publicOrigin.host },
          signal: init.signal,
        },
        (response) => {
          const chunks: Buffer[] = [];
          response.on("data", (chunk: Buffer) => chunks.push(chunk));
          response.on("error", reject);
          response.on("end", () => {
            const status = response.statusCode ?? 502;
            const headers = new Headers();
            for (const [name, value] of Object.entries(response.headers)) {
              if (value !== undefined)
                headers.set(
                  name,
                  Array.isArray(value) ? value.join(", ") : value,
                );
            }
            resolve(
              new Response(
                NULL_BODY_STATUSES.has(status) ? null : Buffer.concat(chunks),
                {
                  status,
                  headers,
                },
              ),
            );
          });
        },
      );
      request.on("error", reject);
      if (body !== undefined) request.write(body);
      request.end();
    });
  };
}

/** openid-client only sends form bodies (URLSearchParams or strings) to these endpoints. */
function bodyToString(
  body: NonNullable<Parameters<CustomFetch>[1]["body"]>,
): string {
  if (typeof body === "string" || body instanceof URLSearchParams)
    return body.toString();
  if (body instanceof Uint8Array) return Buffer.from(body).toString("utf8");
  if (body instanceof ArrayBuffer) return Buffer.from(body).toString("utf8");
  throw new Error("Streaming request bodies are not supported");
}
