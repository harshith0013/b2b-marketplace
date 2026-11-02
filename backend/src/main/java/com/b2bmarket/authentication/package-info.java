/**
 * Maps a verified access token to a {@code CurrentUser} and serves {@code /api/v1/me}. Token
 * signature, issuer, expiry and audience checks live in {@code platform.security}.
 *
 * <p>Module boundary: other modules may call only this module's public service API, never its
 * repositories or entities.
 */
package com.b2bmarket.authentication;
