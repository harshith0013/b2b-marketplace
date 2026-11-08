import { describe, expect, it, vi } from "vitest";
import { checkBackendHealth } from "./backend-health";

function fakeFetch(response: Response | Error) {
  return vi.fn(async () => {
    if (response instanceof Error) throw response;
    return response;
  }) as unknown as typeof fetch;
}

describe("checkBackendHealth", () => {
  it("reports UP when the backend says UP", async () => {
    const fetchImpl = fakeFetch(Response.json({ status: "UP" }));
    await expect(
      checkBackendHealth("http://backend", fetchImpl),
    ).resolves.toEqual({
      frontend: "UP",
      backend: "UP",
    });
    expect(fetchImpl).toHaveBeenCalledWith(
      "http://backend/actuator/health",
      expect.anything(),
    );
  });

  it("reports DOWN for a non-UP status", async () => {
    const result = await checkBackendHealth(
      "http://b",
      fakeFetch(Response.json({ status: "OUT_OF_SERVICE" })),
    );
    expect(result.backend).toBe("DOWN");
  });

  it("reports DOWN for an error response", async () => {
    const result = await checkBackendHealth(
      "http://b",
      fakeFetch(new Response("", { status: 503 })),
    );
    expect(result.backend).toBe("DOWN");
  });

  it("reports UNREACHABLE when the request fails", async () => {
    const result = await checkBackendHealth(
      "http://b",
      fakeFetch(new Error("ECONNREFUSED")),
    );
    expect(result.backend).toBe("UNREACHABLE");
  });
});
