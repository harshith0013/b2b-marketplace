import "server-only";
import { ApiError, fetchBackendBinary } from "@/lib/api/backend";
import { getSession } from "@/lib/auth/current-user";

const IMAGE_TYPES = new Set(["image/jpeg", "image/png"]);

/**
 * Streams a private photo from the API to the browser. The token stays on this server; the
 * browser only ever sees this site's URL. Never cached: photos are private to their business.
 */
export async function photoResponse(apiPath: string): Promise<Response> {
  const session = await getSession();
  if (!session) return new Response(null, { status: 401 });
  try {
    const { bytes, contentType } = await fetchBackendBinary(
      session.idToken,
      apiPath,
    );
    if (!IMAGE_TYPES.has(contentType))
      return new Response(null, { status: 502 });
    return new Response(bytes, {
      headers: {
        "Content-Type": contentType,
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
        "Content-Security-Policy": "default-src 'none'; sandbox",
      },
    });
  } catch (error) {
    if (error instanceof ApiError) {
      // Anything but 401/403 shows as 404, so ids cannot be probed.
      const status =
        error.status === 401 || error.status === 403 ? error.status : 404;
      return new Response(null, { status });
    }
    throw error;
  }
}
