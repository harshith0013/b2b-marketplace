# Setting up "Sign in with Google"

Local development and CI do **not** need this: they use the mock sign-in server in
`docker-compose.yml`, where you type any email on a fake login page. Do this when you want real
Google accounts, for example on a test server or before the pilot.

It is free. The app asks only for `openid`, `email` and `profile`, which Google treats as
non-sensitive, so no Google review is needed. Google console labels change from time to time; if a
name below differs slightly, pick the closest match.

## 1. Create a Google Cloud project
1. Go to <https://console.cloud.google.com/> and sign in with the Google account that will own
   the app (ideally a company account, not a personal one).
2. Create a project, for example `b2b-marketplace`.

## 2. Configure the consent screen
**APIs & Services → OAuth consent screen** (may be called *Google Auth Platform → Branding*):
- User type: **External**.
- App name: your product name (shown to people when they sign in), support email, and a
  developer contact email.
- Authorized domains: your production domain when you have one.
- Scopes: `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile`. Nothing else.
- While the app is in **Testing** mode only the test users you list can sign in (up to 100).
  Add your own Gmail address. Click **Publish app** when real users should be able to sign in.

## 3. Create the OAuth client
**APIs & Services → Credentials → Create credentials → OAuth client ID**:
- Application type: **Web application**, name `b2b-marketplace-web`.
- Authorized redirect URIs (exact match, no trailing slash):
  - `http://localhost:3000/api/auth/callback` for trying real Google locally
  - `https://<your-domain>/api/auth/callback` for each deployed environment
- Copy the **Client ID** and **Client secret**. Treat the secret like a password: never commit it.

## 4. Point the app at Google
Website (Next.js):
```
AUTH_ISSUER=https://accounts.google.com
AUTH_CLIENT_ID=<client id>.apps.googleusercontent.com
AUTH_CLIENT_SECRET=<client secret>
APP_BASE_URL=https://<your-domain>           # http://localhost:3000 locally
SESSION_SECRET=<openssl rand -base64 32>
```
Do not set `AUTH_ALLOW_HTTP_ISSUER` or `AUTH_ISSUER_INTERNAL_URL` outside local development.

Backend (Spring Boot):
```
AUTH_ISSUER_URI=https://accounts.google.com
AUTH_ISSUER_ALIASES=accounts.google.com      # Google uses both spellings in its tokens
AUTH_AUDIENCE=<client id>.apps.googleusercontent.com
```

In deployed environments these come from AWS Secrets Manager (Phase 12), never from files.

## 5. Try it locally with real Google
1. Copy `.env.example` to `.env` (git-ignored) and fill in `GOOGLE_CLIENT_ID` and
   `GOOGLE_CLIENT_SECRET`.
2. Make sure `http://localhost:3000/api/auth/callback` is an authorized redirect URI and your
   Gmail address is a test user (while the app is in Testing mode).
3. Start the stack with the Google override:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.google.yml up --build
   ```
4. Open <http://localhost:3000/en/account>, choose **Continue with Google** and sign in. You
   should land on your account page showing your Gmail address.

Plain `docker compose up --build` goes back to the mock sign-in.

## What is checked on every sign-in
- **Website:** the code from Google is exchanged on the server, with state, nonce and PKCE
  checks. The ID token's signature, issuer, audience and expiry are verified, and the email must
  be verified by Google. The token is kept in an encrypted, httpOnly cookie that the browser cannot
  read.
- **Backend:** checks the token again on every API call: Google's signature, issuer, expiry,
  audience = this app, and a verified email.

Sessions last as long as Google's ID token (about an hour). After that, visiting a signed-in page
goes back through Google, which signs the person in again without asking if they are still
signed in to Google.
