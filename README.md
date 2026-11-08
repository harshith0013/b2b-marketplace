# B2B Marketplace (working name)

AI-powered, privacy-first B2B wholesale and surplus clearance marketplace for India.
See `docs/ARCHITECTURE.md` for the architecture, phase plan, risks and proposed data model.

## Prerequisites
- Java 21 and Maven 3.9+
- Node.js 24 (ships npm 11; npm 10 has a resolver bug with this project's dependencies)
- Docker Desktop (for PostgreSQL and the backend integration tests)

## Quick start

Everything in Docker:
```bash
docker compose up --build
# Web: http://localhost:3000   API health: http://localhost:8080/actuator/health
```

Or run the apps from your editor with only the database in Docker:
```bash
docker compose up -d postgres
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm install && npm run dev
```

## Tests
```bash
cd backend && mvn verify              # unit + integration (Testcontainers, needs Docker)
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
cd frontend && npx playwright install chromium && npm run test:e2e
```

## Layout
```
backend/     Spring Boot modular monolith (com.b2bmarket.<module>)
frontend/    Next.js App Router app; pages under src/app/[lang] (en, te, hi)
docs/        Architecture and phase prompts
CLAUDE.md    Project rules read automatically by Claude Code
```

## Phase 1 status
Verified locally on 2026-11-02 (Spring Boot 4.1.1, JDK 21, Node 24):
- Backend: `mvn verify` passes (5 unit tests, 6 Testcontainers integration tests);
  `spotless:check` is clean.
- Frontend: lint, typecheck, 17 unit tests, production build and 6 Playwright tests pass.
- `docker compose up --build`: all three containers healthy; the home page shows the server
  as Working.
- Telugu and Hindi strings need review by a native speaker.

## Phase 2 status
Done on 2026-11-02: design tokens (contrast-checked in light and dark by a unit test),
self-hosted Noto fonts for Latin, Telugu and Devanagari, core UI components in
`frontend/src/components/ui`, and shells for the buyer home (`/[lang]`), seller home
(`/[lang]/sell`), listing wizard (`/[lang]/sell/new`) and system status (`/[lang]/status`).
- 80 unit tests and 40 Playwright tests pass (every shell in en/te/hi on mobile and desktop,
  each with an axe WCAG 2.2 AA scan).
- Lighthouse accessibility on the buyer home: 100 in all three languages, mobile and desktop.
- Nothing is saved and there are no API calls yet; all offers shown are marked as sample data.
- The Docker image build downloads the fonts, so it needs internet access.

## Phase 3 status
Done on 2026-11-02: **Sign in with Google**, business accounts and access control.
- Sign-in runs on the website's server (OpenID Connect with PKCE); the session is an encrypted,
  httpOnly cookie. Pages under `/sell`, `/account`, `/onboarding` and `/business` need sign-in.
- Businesses with OWNER / MANAGER / STAFF roles, invitations by email (accepted on `/account`),
  an append-only audit log, and a Spring Modulith test that fails the build on module boundary
  violations. Another business gets a 404 for anything that is not theirs.
- Backend: 36 unit and 26 integration tests (real signed tokens and PostgreSQL).
  Frontend: 115 unit tests, 44 Playwright tests on the website alone, and 12 Playwright tests
  against the whole stack (`npm run test:e2e:stack` after `docker compose up --build`).
- **Try it locally:** `docker compose up --build`, open <http://localhost:3000/en/account>, choose
  *Continue with Google*, and type any email on the mock login page.
- Real Google accounts: see `docs/google-signin-setup.md`.
- Stop `docker compose` before `npm run test:e2e` (it reuses whatever runs on port 3000).

## Phase 4 status
Done on 2026-11-05: **database, catalogue and inventory** (pilot category: non-food FMCG).
- Migrations V4–V6: categories (seeded in en/te/hi), products and pack variants with GS1 barcode
  checks, warehouses, stock batches, draft listings and quantity price tiers (paise, excluding GST).
- **Row-Level Security** on warehouses, batches, listings and price tiers: each request's
  transaction is switched to its business (`TenantScope`), so even an unfiltered query returns
  only that business's rows. It guards against application bugs, not SQL injection.
- Approved batch dates can only change by going back to review (enforced by a database trigger,
  recorded in the audit log). Expired stock cannot be added or listed.
- New products are visible only to the business that added them until approved (Phase 5).
- Website: `/[lang]/business/[id]/warehouses` (list, add, switch off). Batches and listings have
  an API only; their screens come in Phase 5.
- Backend: 70 unit and 46 integration tests. Frontend: 117 unit tests, 44 Playwright tests and 14
  full-stack Playwright tests.
- Category names and new Telugu/Hindi strings need native-speaker review.

## Phase 5a status
Done on 2026-11-05: **backend for stock photos, eligibility checks and the review queue.**
- Photos: `POST /api/v1/businesses/{id}/batches/{batchId}/photos` (JPEG or PNG, up to 5 MB, 6 per
  batch). Every photo is re-encoded, which removes camera metadata such as **GPS location**, and
  phone rotation is applied first. Stored privately (SeaweedFS locally, AWS S3 later) and served
  only through the API to the business's members and to reviewers.
- Eligibility checks, run on submit and again on approval: at least 30 days of shelf life, a
  pack-front and a label-date photo, MRP entered, price with GST not above MRP, unregulated
  category, enough stock for the minimum order. All failures are reported together as codes.
- Sellers submit, withdraw, pause, resume and close listings. Platform admins and reviewers
  (`platform_roles`, see `docs/admin-setup.md`) approve or reject in `/api/v1/admin/reviews`;
  approval publishes the listing and approves its batch and product. Reviewers cannot decide on
  their own business, and a seller's transaction can never publish its own listing (database
  trigger). Review transactions use a separate `marketplace_reviewer` database role.
- Backend: 83 unit and 63 integration tests (real PostgreSQL, SeaweedFS and signed tokens).
- Screens for all of this come in Phase 5b. Locally, set `BOOTSTRAP_ADMIN_EMAIL` in `.env` to try
  the queue.
- MinIO no longer publishes Docker images, so SeaweedFS (S3-compatible) is the local store.

## Phase 5b status
Done on 2026-11-07: **website screens for selling and review.** Phase 5 is complete.
- `/sell` picks the business; `/business/{id}/listings` is the seller dashboard; the listing wizard
  (`…/listings/new`) creates the product, batch and draft listing in four steps (prices typed in
  rupees, stored as paise); each listing page takes photos (phones open the camera), lists the
  open checks in plain words, and offers submit, withdraw, pause, resume and close.
- `/admin/reviews` is the review queue for platform reviewers (linked from their account page):
  photos at full size, the automatic checks, past decisions, approve, or send back with a reason
  the seller then sees.
- Photos reach the browser only through this site's own routes; the token never leaves the server.
- Tests: backend 85 unit and 64 integration; website 142 unit, 44 Playwright, and 16 full-stack
  Playwright tests including the whole seller → reviewer → seller journey.
- **Try it:** `docker compose up --build`, sign in as any email to sell; sign in as
  `e2e-reviewer@example.test` (a local-only test reviewer) to review, or add your own email to
  `BOOTSTRAP_ADMIN_EMAIL` in `.env`.

If Homebrew's default JDK is newer than 21, run Maven with
`JAVA_HOME=$(brew --prefix openjdk@21)`.
