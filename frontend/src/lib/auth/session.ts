import { sealData, unsealData } from "iron-session";

/**
 * The signed-in session, stored encrypted in an httpOnly cookie. The browser cannot read or
 * change it; only this server can. It holds the sign-in provider's ID token, which is sent to the
 * backend on every API call.
 */
export interface Session {
  idToken: string;
  email: string;
  /** Epoch seconds when the ID token expires. */
  expiresAt: number;
}

/** The short-lived state of a sign-in in progress (between leaving for Google and coming back). */
export interface LoginTransaction {
  state: string;
  nonce: string;
  codeVerifier: string;
  returnTo: string;
}

export const SESSION_COOKIE = "b2b_session";
export const LOGIN_COOKIE = "b2b_login";
export const LOGIN_TTL_SECONDS = 10 * 60;
/** Treat the token as expired slightly early so a request never reaches the API with a stale one. */
export const EXPIRY_LEEWAY_SECONDS = 30;

export function cookieOptions(secure: boolean, maxAgeSeconds: number) {
  return {
    httpOnly: true,
    secure,
    sameSite: "lax" as const, // Sent on the redirect back from Google; not on cross-site posts.
    path: "/",
    maxAge: maxAgeSeconds,
  };
}

export function sealSession(session: Session, secret: string): Promise<string> {
  return sealData(session, { password: secret, ttl: 0 });
}

export function sealLogin(
  transaction: LoginTransaction,
  secret: string,
): Promise<string> {
  return sealData(transaction, { password: secret, ttl: LOGIN_TTL_SECONDS });
}

/** The session, or null when the cookie is missing, tampered with or unreadable. */
export async function unsealSession(
  sealed: string | undefined,
  secret: string,
): Promise<Session | null> {
  if (!sealed) return null;
  try {
    const data = await unsealData<Partial<Session>>(sealed, {
      password: secret,
      ttl: 0,
    });
    if (!data.idToken || !data.email || typeof data.expiresAt !== "number")
      return null;
    return {
      idToken: data.idToken,
      email: data.email,
      expiresAt: data.expiresAt,
    };
  } catch {
    return null;
  }
}

export async function unsealLogin(
  sealed: string | undefined,
  secret: string,
): Promise<LoginTransaction | null> {
  if (!sealed) return null;
  try {
    const data = await unsealData<Partial<LoginTransaction>>(sealed, {
      password: secret,
      ttl: LOGIN_TTL_SECONDS,
    });
    if (!data.state || !data.nonce || !data.codeVerifier || !data.returnTo)
      return null;
    return {
      state: data.state,
      nonce: data.nonce,
      codeVerifier: data.codeVerifier,
      returnTo: data.returnTo,
    };
  } catch {
    return null;
  }
}

export function isExpired(session: Session, nowSeconds: number): boolean {
  return session.expiresAt - EXPIRY_LEEWAY_SECONDS <= nowSeconds;
}
