# Project context for Claude Code

AI-powered, privacy-first B2B wholesale and surplus clearance marketplace for India, connecting
kirana shops, wholesalers, manufacturers, distributors and other verified businesses. Initial focus:
surplus and clearance inventory. Full vision and phase plan: `docs/ARCHITECTURE.md`.

## Stack
- `backend/`: Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway, PostgreSQL 17
  with pgvector and pg_trgm. Modular monolith, base package `com.b2bmarket`.
- `frontend/`: Next.js 16 App Router, React 19, TypeScript, Tailwind CSS 4, shadcn/ui conventions.
  Read `frontend/AGENTS.md` before writing Next.js code: this version differs from older docs.
- Local infra: `docker-compose.yml`. CI: `.github/workflows/ci.yml`.

## Commands
- Backend unit tests: `cd backend && mvn test`
- Backend all tests (needs Docker): `cd backend && mvn verify`
- Backend formatting: `cd backend && mvn spotless:apply`
- Frontend checks: `cd frontend && npm run lint && npm run typecheck && npm test && npm run build`
- Frontend e2e: `cd frontend && npx playwright install chromium && npm run test:e2e`
- Everything locally: `docker compose up --build`

## Working rules
- Work on ONE phase at a time. Never start the next phase without explicit instruction.
- Before coding a phase, state the plan and the files you will touch, then wait for approval.
- After every change, run the relevant build, lint and test commands and report what actually ran.
  Never claim a test passed unless you ran it.
- Pin exact dependency versions. Do not add a dependency released in the last 14 days.
- Verify Spring AI and Spring Boot compatibility before adding Spring AI (Phase 7 only).

## Architecture rules
- Controller -> Service -> Repository. Transactions on service methods. DTOs (records) in APIs;
  never expose JPA entities.
- Modules talk only through each other's public services, never repositories.
- Every query on business data is scoped to the caller's business (tenant isolation).
- Schema changes only via new Flyway migrations; never edit an applied one.
- Use the injected `Clock`, never `Instant.now()` directly.
- Money as integer paise (bigint). Quantities always carry a unit.
- Security is deny-by-default; every new endpoint needs an explicit authorization rule and a test
  proving another business cannot access its data.
- Every user-facing string goes into all three dictionaries (en, te, hi) in
  `frontend/src/i18n/dictionaries`; the unit test fails if any key is missing.

## AI and data integrity (non-negotiable)
- AI must never invent products, stock, prices, supplier verification or delivery commitments.
  All such data comes from authorized backend queries.
- AI uses only approved backend tools; no unrestricted database access.
- AI and OCR flag problems; they never certify authenticity or compliance.
- Approved expiry dates cannot be changed silently; changes require re-review and an audit event.

## Current status
Phases 1-8 and 9a (cart, quotes, orders, local delivery, cash only) are done (see README).
Next: Phase 9b (Razorpay Route in test mode; the user puts the test keys in `.env` themselves,
never in chat), then 9c (a logistics partner for long distance; check its test environment
first), each only when explicitly asked. Orders are placed under `TenantScope.enterOrdering(buyer)`
(role `marketplace_ordering`); lock batches in id order and never read seller tables outside it.
Compliance rules live in `compliance.PilotRules`; changing one needs a new version string. AI label
readings only flag; reviewers confirm. The AI assistant only proposes filters that the `search`
module checks; never show model-written text. After changing the prompt or model, run
`mvn test -Pai-eval` (live, uses provider quota) and compare with the README's score. Public (anonymous) queries
must run after `TenantScope.enterPublic()` and only through the `search` module. Review code must call `PlatformRoles.requireReviewer` then `TenantScope.enterReview()`. Pilot category: non-food FMCG. Seller tables have Row-Level Security: any service
touching warehouses, batches, listings or price tiers must call `TenantAccess.enter(...)` inside its
transaction before querying them; new seller tables need a policy in a new migration and an RLS test.
Sign-in is "Sign in with Google" (OIDC on the Next.js server, encrypted cookie session); locally
`mock-oauth2-server` stands in for Google. Modules keep entities, repositories and controllers in
an `internal` subpackage; `ModuleStructureTest` enforces boundaries. Pages that read the session
or request-time params must do so inside `<Suspense>` (Cache Components).
Playwright reuses any server already on port 3000: stop `docker compose` before `npm run test:e2e`. On this machine run Maven
with `JAVA_HOME=/opt/homebrew/opt/openjdk@21` (the Homebrew default JDK is 27).
