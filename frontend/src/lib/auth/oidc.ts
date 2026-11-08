import "server-only";
import * as client from "openid-client";
import { authConfig, type AuthConfig } from "./config";
import { publicHostFetch } from "./internal-fetch";
import type { LoginTransaction, Session } from "./session";

export const CALLBACK_PATH = "/api/auth/callback";
const SCOPES = "openid email profile";

let discovered: Promise<client.Configuration> | undefined;

function discover(config: AuthConfig): Promise<client.Configuration> {
  const fetchOverride = config.issuerInternalUrl
    ? publicHostFetch(config.issuer, config.issuerInternalUrl)
    : undefined;
  return client
    .discovery(
      config.issuer,
      config.clientId,
      undefined,
      client.ClientSecretPost(config.clientSecret),
      {
        execute: config.allowHttpIssuer ? [client.allowInsecureRequests] : [],
        ...(fetchOverride && { [client.customFetch]: fetchOverride }),
      },
    )
    .then((configuration) => {
      if (fetchOverride) configuration[client.customFetch] = fetchOverride;
      return configuration;
    });
}

/** The provider's endpoints, discovered once per server process (retried after a failure). */
function oidc(): Promise<client.Configuration> {
  discovered ??= discover(authConfig()).catch((error: unknown) => {
    discovered = undefined;
    throw error;
  });
  return discovered;
}

export function redirectUri(): string {
  return new URL(CALLBACK_PATH, authConfig().appBaseUrl).toString();
}

/** Builds the provider's sign-in URL and the secrets to check when the person comes back. */
export async function beginLogin(
  returnTo: string,
): Promise<{ url: URL; transaction: LoginTransaction }> {
  const configuration = await oidc();
  const transaction: LoginTransaction = {
    state: client.randomState(),
    nonce: client.randomNonce(),
    codeVerifier: client.randomPKCECodeVerifier(),
    returnTo,
  };
  const url = client.buildAuthorizationUrl(configuration, {
    redirect_uri: redirectUri(),
    scope: SCOPES,
    state: transaction.state,
    nonce: transaction.nonce,
    code_challenge: await client.calculatePKCECodeChallenge(
      transaction.codeVerifier,
    ),
    code_challenge_method: "S256",
  });
  return { url, transaction };
}

export class LoginError extends Error {}

/**
 * Exchanges the code from the provider for an ID token after checking state, nonce and PKCE. The
 * ID token's signature, issuer, audience and expiry are verified by openid-client.
 */
export async function completeLogin(
  callbackUrl: URL,
  transaction: LoginTransaction,
): Promise<Session> {
  const configuration = await oidc();
  // The provider redirected to the public address; check against that, not the internal one.
  const currentUrl = new URL(
    callbackUrl.pathname + callbackUrl.search,
    authConfig().appBaseUrl,
  );
  const tokens = await client.authorizationCodeGrant(
    configuration,
    currentUrl,
    {
      pkceCodeVerifier: transaction.codeVerifier,
      expectedState: transaction.state,
      expectedNonce: transaction.nonce,
      idTokenExpected: true,
    },
  );
  const claims = tokens.claims();
  if (!tokens.id_token || !claims) throw new LoginError("No ID token returned");
  const email = typeof claims.email === "string" ? claims.email : undefined;
  if (!email || !isTrue(claims.email_verified)) {
    throw new LoginError(
      "The sign-in provider did not confirm a verified email",
    );
  }
  return { idToken: tokens.id_token, email, expiresAt: claims.exp };
}

function isTrue(value: unknown): boolean {
  return value === true || value === "true";
}
