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
Phases 1 and 2 are done (see README). Next: Phase 3 (authentication, business accounts, access
control), only when explicitly asked; there is no prompt file for it yet.
Playwright reuses any server already on port 3000: stop `docker compose` before `npm run test:e2e`. On this machine run Maven
with `JAVA_HOME=/opt/homebrew/opt/openjdk@21` (the Homebrew default JDK is 27).
