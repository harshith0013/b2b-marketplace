import type { NextRequest } from "next/server";
import { isUuid } from "@/lib/actions/ids";
import { photoResponse } from "@/lib/api/photo-response";

/** A batch photo, for members of the business (the API checks membership). */
export async function GET(
  _request: NextRequest,
  ctx: RouteContext<"/api/photos/[businessId]/[batchId]/[photoId]">,
) {
  const { businessId, batchId, photoId } = await ctx.params;
  if (![businessId, batchId, photoId].every(isUuid))
    return new Response(null, { status: 404 });
  return photoResponse(
    `/api/v1/businesses/${businessId}/batches/${batchId}/photos/${photoId}`,
  );
}
