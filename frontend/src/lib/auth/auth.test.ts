// @vitest-environment node
import http from "node:http";
import type { AddressInfo } from "node:net";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { isProtectedPath } from "@/proxy";
import { readAuthConfig } from "./config";
import { publicHostFetch } from "./internal-fetch";
import { localeOfPath, safeReturnTo } from "./return-to";
import {
  isExpired,
  sealLogin,
  sealSession,
  unsealLogin,
  unsealSession,
} from "./session";

const SECRET = "a-test-secret-that-is-at-least-32-characters-long";

describe("safeReturnTo", () => {
  it("keeps same-site paths with their query", () => {
    expect(safeReturnTo("/te/business/1?tab=members", "te")).toBe(
      "/te/business/1?tab=members",
    );
  });

  it.each([
    ["https://evil.example/steal"],
    ["//evil.example"],
    ["/\\evil.example"],
    ["javascript:alert(1)"],
    [""],
    [null],
  ])("sends %s to the account page instead", (value) => {
    expect(safeReturnTo(value, "hi")).toBe("/hi/account");
  });

  it("reads the language from a path", () => {
    expect(localeOfPath("/te/sell")).toBe("te");
    expect(localeOfPath("/fr/sell")).toBe("en");
  });
});

describe("session cookies", () => {
  const session = {
    idToken: "header.payload.signature",
    email: "asha@gmail.com",
    expiresAt: 2000,
  };

  it("round-trips a session", async () => {
    expect(
      await unsealSession(await sealSession(session, SECRET), SECRET),
    ).toEqual(session);
  });

  it("rejects a cookie sealed with another secret or tampered with", async () => {
    const sealed = await sealSession(session, SECRET);
    expect(await unsealSession(sealed, `${SECRET}-other`)).toBeNull();
    expect(
      await unsealSession(`${sealed.slice(0, -4)}AAAA`, SECRET),
    ).toBeNull();
    expect(await unsealSession(undefined, SECRET)).toBeNull();
  });

  it("round-trips a sign-in in progress", async () => {
    const transaction = {
      state: "s",
      nonce: "n",
      codeVerifier: "v",
      returnTo: "/en/account",
    };
    expect(
      await unsealLogin(await sealLogin(transaction, SECRET), SECRET),
    ).toEqual(transaction);
  });

  it("treats a session as expired a little before the token really expires", () => {
    expect(isExpired(session, 1900)).toBe(false);
    expect(isExpired(session, 1975)).toBe(true);
  });
});

describe("readAuthConfig", () => {
  const env = {
    AUTH_ISSUER: "https://accounts.google.com",
    AUTH_CLIENT_ID: "client",
    AUTH_CLIENT_SECRET: "secret",
    APP_BASE_URL: "https://example.in",
    SESSION_SECRET: SECRET,
  };

  it("accepts a complete https configuration", () => {
    expect(readAuthConfig(env).issuer.href).toBe(
      "https://accounts.google.com/",
    );
  });

  it("refuses to start without a setting or with a short session secret", () => {
    expect(() => readAuthConfig({ ...env, AUTH_CLIENT_ID: "" })).toThrow(
      /AUTH_CLIENT_ID/,
    );
    expect(() => readAuthConfig({ ...env, SESSION_SECRET: "short" })).toThrow(
      /32/,
    );
  });

  it("allows a plain-http issuer only when explicitly enabled for local development", () => {
    const local = { ...env, AUTH_ISSUER: "http://localhost:8090/default" };
    expect(() => readAuthConfig(local)).toThrow(/https/);
    expect(
      readAuthConfig({ ...local, AUTH_ALLOW_HTTP_ISSUER: "true" })
        .allowHttpIssuer,
    ).toBe(true);
  });
});

describe("isProtectedPath", () => {
  it.each([
    "/en/sell",
    "/te/sell/new",
    "/hi/account",
    "/en/onboarding",
    "/en/business/123",
  ])("protects %s", (path) => expect(isProtectedPath(path)).toBe(true));

  it.each([
    "/en",
    "/en/login",
    "/te/status",
    "/en/seller-tips",
    "/en/accounts",
  ])("leaves %s public", (path) => expect(isProtectedPath(path)).toBe(false));
});

describe("publicHostFetch", () => {
  let server: http.Server;
  let seen: { host?: string; path?: string; body?: string };
  let internal: URL;

  beforeAll(async () => {
    server = http.createServer((request, response) => {
      let body = "";
      request.on("data", (chunk) => (body += chunk));
      request.on("end", () => {
        seen = { host: request.headers.host, path: request.url, body };
        response.writeHead(200, { "content-type": "application/json" });
        response.end(JSON.stringify({ ok: true }));
      });
    });
    await new Promise<void>((resolve) =>
      server.listen(0, "127.0.0.1", resolve),
    );
    internal = new URL(
      `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
    );
  });

  afterAll(() => new Promise<void>((resolve) => server.close(() => resolve())));

  it("sends requests for the public issuer to the internal address with the public Host", async () => {
    const fetchVia = publicHostFetch(
      new URL("http://localhost:8090"),
      internal,
    );

    const response = await fetchVia("http://localhost:8090/default/token?x=1", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ code: "abc" }),
      redirect: "manual",
    });

    expect(await response.json()).toEqual({ ok: true });
    expect(seen).toEqual({
      host: "localhost:8090",
      path: "/default/token?x=1",
      body: "code=abc",
    });
  });
});
