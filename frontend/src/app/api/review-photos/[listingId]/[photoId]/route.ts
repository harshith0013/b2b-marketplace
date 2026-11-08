import type { NextRequest } from "next/server";
import { isUuid } from "@/lib/actions/ids";
import { photoResponse } from "@/lib/api/photo-response";

/** A photo of a listing in review, for platform reviewers (the API checks the role). */
export async function GET(
  _request: NextRequest,
  ctx: RouteContext<"/api/review-photos/[listingId]/[photoId]">,
) {
  const { listingId, photoId } = await ctx.params;
  if (![listingId, photoId].every(isUuid))
    return new Response(null, { status: 404 });
  return photoResponse(`/api/v1/admin/reviews/${listingId}/photos/${photoId}`);
}
