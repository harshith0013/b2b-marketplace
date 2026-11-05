import "server-only";

/** Minimum length of SESSION_SECRET; iron-session needs at least 32 characters. */
export const MIN_SESSION_SECRET_LENGTH = 32;

export interface AuthConfig {
  /** Issuer as the browser and the tokens see it: https://accounts.google.com, or the local mock. */
  issuer: URL;
  /**
   * Development only: where this server reaches the issuer when its public address is not
   * reachable from inside Docker. Requests keep the public host name so tokens name the public
   * issuer.
   */
  issuerInternalUrl?: URL;
  clientId: string;
  clientSecret: string;
  /** Public origin of this website, for the OAuth redirect URI. */
  appBaseUrl: URL;
  sessionSecret: string;
  /** Plain-http issuers are allowed only for the local mock server. */
  allowHttpIssuer: boolean;
}

type Env = Record<string, string | undefined>;

function required(env: Env, name: string): string {
  const value = env[name]?.trim();
  if (!value) throw new Error(`Missing required environment variable ${name}`);
  return value;
}

export function readAuthConfig(env: Env = process.env): AuthConfig {
  const issuer = new URL(required(env, "AUTH_ISSUER"));
  const allowHttpIssuer = env.AUTH_ALLOW_HTTP_ISSUER === "true";
  if (issuer.protocol !== "https:" && !allowHttpIssuer) {
    throw new Error("AUTH_ISSUER must use https (set AUTH_ALLOW_HTTP_ISSUER=true only locally)");
  }
  const sessionSecret = required(env, "SESSION_SECRET");
  if (sessionSecret.length < MIN_SESSION_SECRET_LENGTH) {
    throw new Error(`SESSION_SECRET must be at least ${MIN_SESSION_SECRET_LENGTH} characters`);
  }
  const internal = env.AUTH_ISSUER_INTERNAL_URL?.trim();
  return {
    issuer,
    issuerInternalUrl: internal ? new URL(internal) : undefined,
    clientId: required(env, "AUTH_CLIENT_ID"),
    clientSecret: required(env, "AUTH_CLIENT_SECRET"),
    appBaseUrl: new URL(required(env, "APP_BASE_URL")),
    sessionSecret,
    allowHttpIssuer,
  };
}

let cached: AuthConfig | undefined;

/** Read once per server process; throws on first use if the environment is incomplete. */
export function authConfig(): AuthConfig {
  cached ??= readAuthConfig();
  return cached;
}
