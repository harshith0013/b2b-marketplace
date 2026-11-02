# Setting up the dev Amazon Cognito user pool (email one-time code)

Local development and CI do **not** need this: they use the mock OAuth2 server in
`docker-compose.yml`. Do this once to prove sign-in works against real Cognito, and as the start
of the production setup.

AWS console labels change from time to time; if a name below differs slightly, pick the closest
match.

## 0. Protect the account first (5 minutes)
1. Turn on MFA for the root user. Stop using the root user for daily work; create an admin user
   in IAM Identity Center and sign in with that.
2. **Billing → Budgets → Create budget**: a monthly cost budget of about ₹500 (≈ $6) with an email
   alert at 80%. Cognito's free tier covers our user numbers, but this catches surprises.

## 1. Create the user pool
Region: **Asia Pacific (Mumbai) ap-south-1** (keeps personal data in India for the DPDP Act).

**Cognito → User pools → Create user pool**
- Application type: *Traditional web application* (our Next.js server signs people in).
- Sign-in identifier: **Email**. Self-registration: on. Required attribute: `email`.
- Feature plan: **Essentials** (needed for one-time-code sign-in and for adding claims to access
  tokens). Check the current free tier on the Cognito pricing page.
- Sign-in options / authentication methods: enable **choice-based sign-in** and allow **Email
  message one-time password**. Leave SMS off until TRAI DLT registration is done.
- Email delivery: **Send email with Cognito** for now (free, but limited to roughly 50 emails a
  day). Production switches to Amazon SES with a verified domain.
- Account recovery: email only.

## 2. App client
- Create an app client named `b2b-marketplace-web-dev`.
- Authentication flows: **ALLOW_USER_AUTH** and **ALLOW_REFRESH_TOKEN_AUTH**. No others.
- Client secret: **none** for this dev check (simpler CLI test). Phase 3b decides whether the
  web server uses a secret.
- Access token lifetime: 15 minutes; refresh token: 30 days.
- Note the **User pool ID** (e.g. `ap-south-1_AbC123`) and the **App client ID**.

## 3. Put email into access tokens
The API identifies people by verified email, but Cognito access tokens carry it only if a
pre-token-generation trigger adds it.

1. **Lambda → Create function**: name `b2b-pre-token-generation`, runtime Node.js (current LTS),
   region ap-south-1. Code:

   ```js
   // Adds the signed-in person's email to the Cognito access token.
   export const handler = async (event) => {
     const attributes = event.request.userAttributes;
     event.response = {
       claimsAndScopeOverrideDetails: {
         accessTokenGeneration: {
           claimsToAddOrOverride: {
             email: attributes.email,
             email_verified: attributes.email_verified,
           },
         },
       },
     };
     return event;
   };
   ```
2. **User pool → Extensions (Lambda triggers) → Add trigger → Pre token generation**: choose the
   function and trigger event version **V2_0** (or newer). Cognito adds the permission it needs.

## 4. Create a test user
**User pool → Users → Create user**: your Gmail address, *Mark email address as verified* on,
no password needed for one-time-code sign-in. (Or sign up through the hosted pages once 3b exists.)

## 5. Check sign-in end to end (optional, needs the AWS CLI)
```bash
REGION=ap-south-1
CLIENT_ID=<app client id>
EMAIL=<your gmail>

# Sends a 6-digit code to your inbox and prints a Session value.
aws cognito-idp initiate-auth --region $REGION --client-id $CLIENT_ID \
  --auth-flow USER_AUTH \
  --auth-parameters USERNAME=$EMAIL,PREFERRED_CHALLENGE=EMAIL_OTP

# Exchange the code for tokens.
aws cognito-idp respond-to-auth-challenge --region $REGION --client-id $CLIENT_ID \
  --challenge-name EMAIL_OTP --session '<Session from above>' \
  --challenge-responses USERNAME=$EMAIL,EMAIL_OTP_CODE=<code from email>
```

Then run the backend against Cognito and call the API with the `AccessToken`:
```bash
cd backend
AUTH_ISSUER_URI=https://cognito-idp.ap-south-1.amazonaws.com/<user pool id> \
AUTH_AUDIENCE=<app client id> \
JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn spring-boot:run -Dspring-boot.run.profiles=local

curl -H "Authorization: Bearer <AccessToken>" http://localhost:8080/api/v1/me
```
Expected: your profile with your Gmail address. A `403` saying the email is not verified means
step 3 is missing or the trigger version is too old.

## What the API checks on every Cognito token
Signature (Cognito's published keys), issuer = the pool URL, not expired, `client_id` = the app
client, and `email_verified` = true. Nothing else from the token is trusted.
