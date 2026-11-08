import type { NextRequest } from "next/server";
import { isUuid } from "@/lib/actions/ids";
import { ApiError, fetchPublicBinary } from "@/lib/api/backend";

const IMAGE_TYPES = new Set(["image/jpeg", "image/png"]);

/** A photo of a published offer, for anyone. The API serves only offers on sale. */
export async function GET(
  _request: NextRequest,
  ctx: RouteContext<"/api/offer-photos/[listingId]/[photoId]">,
) {
  const { listingId, photoId } = await ctx.params;
  if (![listingId, photoId].every(isUuid))
    return new Response(null, { status: 404 });
  try {
    const { bytes, contentType } = await fetchPublicBinary(
      `/api/v1/offers/${listingId}/photos/${photoId}`,
    );
    if (!IMAGE_TYPES.has(contentType))
      return new Response(null, { status: 502 });
    return new Response(bytes, {
      headers: {
        "Content-Type": contentType,
        "Cache-Control": "public, max-age=300",
        "X-Content-Type-Options": "nosniff",
        "Content-Security-Policy": "default-src 'none'; sandbox",
      },
    });
  } catch (error) {
    // Not on sale (or no such photo): 404. Backend unreachable: 503, not a crash.
    return new Response(null, {
      status: error instanceof ApiError ? 404 : 503,
    });
  }
}
