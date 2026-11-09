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

## Phase 6 status
Done on 2026-11-08: **buyer portal, search and filters, open to everyone (no sign-in).**
- Home shows the latest real offers; `/offers` searches by words, typos ("detrgent") and category
  names in English, Telugu or Hindi, with filters for category, state and city, brand, price per
  pack, minimum order value and days left, and four sort orders; `/offers/{id}` shows photos,
  prices, stock, MRP, place and seller ("Confidential seller" when the seller chose that).
- Only offers on sale appear: published, batch approved, at least **30 days** before the date on
  the pack, and enough stock for the minimum order. Never shown: street addresses, and the name or
  id of confidential sellers.
- Public queries run as the read-only `marketplace_public` database role (row policies and column
  grants) with a 2-second limit.
- The site asks search engines not to index it until the pilot; set `SITE_INDEXABLE=true` when
  building the website to allow indexing.
- Tests: backend 90 unit and 76 integration; website 146 unit, 50 Playwright and 20 full-stack
  Playwright tests (including an anonymous buyer finding a confidential offer through a typo).
- Known risk: public prices can be scraped; real rate limiting comes with the WAF in Phase 12.

## Phase 7 status
Done on 2026-11-08: **search assistant and similar offers.**
- "Ask in your own words" on the home page: a question in English, Telugu, Hindi or mixed text
  becomes search filters (category, place, quantity such as "20 cases", price, days left, sort).
  The model (Google **Gemini `gemini-3.1-flash-lite`**, free tier) only proposes filters; the
  backend checks each one against real categories and places, and the normal search returns the
  offers. Nothing the model writes is shown; the offers page lists what it searched for as
  removable filters.
- Falls back to normal search when there is no key, the provider is slow (over 5 s) or out of
  quota (then it is not asked again for 15 minutes), or a visitor or the day is over its limit
  (10 a minute and 100 a day per visitor; 200 a day in total, all adjustable).
- Only the question text is sent to Google. Questions are logged without any address or identity
  and deleted after 90 days.
- "Similar offers" on each offer page: same or sibling category, comparable price, same state
  first (plain rules, no AI).
- **Evaluation:** 40 questions in en/te/hi/mixed (`backend/src/test/resources/ai-eval`).
  `gemini-3.1-flash-lite` scored 96.3% (104/108 checks; Telugu and mixed 100%), about 2 s per
  answer. Run it with `cd backend && mvn test -Pai-eval` (needs `GEMINI_API_KEY`; uses free quota).
  `gemini-2.5-flash` allows only 20 free requests a day, so it is not used.
- **To use it:** put `GEMINI_API_KEY=...` (from Google AI Studio) in `.env`. On Gemini's free tier
  Google may use the questions to improve its products; only anonymous search text is sent.
- Tests: backend 101 unit and 86 integration (with a fake AI provider); website 152 unit, 52
  Playwright and 22 full-stack Playwright tests.

## Phase 8 status
Done on 2026-11-08: **label reading, Batch Passport and versioned compliance rules.**
- After a "label with the date" photo is uploaded, Gemini vision reads the printed date, batch
  number and MRP in the background. Rules compare them with what the seller typed and **flag**
  differences or unreadable labels; nothing is certified. Sellers see the flags; reviewers see the
  photo reading next to the typed values and must tick "I compared it myself" to approve a
  flagged batch (recorded). No key, quota or timeout means "not checked", never a block.
- The `compliance` module holds rule set `pilot-2026-11` (in `PilotRules`, written for counsel):
  blocks (30 days left, photos, MRP, price within MRP, regulated category, stock) and flags (label
  date, batch number, MRP). Every evaluation is stored append-only with stage (submit, approve,
  daily) and version; a 06:30 daily job re-checks published offers. Checkout and dispatch hooks
  are used from Phase 9.
- Batch Passport: sellers and reviewers see the batch's full history; buyers see a summary on the
  offer page (approved on, expiry matched the label or was checked by a reviewer, dates changed
  after approval or not).
- Evaluations: assistant 96.3% (Phase 7); labels 38/38 checks on 10 generated label images. Real
  dot-printed pack photos are harder: add pilot photos to `ai-eval/labels` before relying on it.
- Only label photos (location data already removed) are sent to Google; separate daily cap
  (`AI_VISION_DAILY_LIMIT`, default 100).
- Tests: backend 105 unit and 94 integration; website 158 unit, Playwright and full-stack suites.

## Phase 9a status
Done on 2026-11-09: **cart, quotes, orders and local delivery (cash payments only).**
- Any business owner or manager can add an offer to the cart or ask the seller for a quote. A
  cart is checked out per seller: one order per seller. Prices, GST (rounded per line) and the
  delivery charge are worked out by the backend at order time and stored on the order.
- Sellers choose per listing: buyer pickup from the warehouse (paid on pickup) and/or their own
  delivery staff to listed PIN codes with a per-order charge, paid in cash on delivery if they
  accept COD.
- Placing an order reserves the stock for 24 hours (database row locks, so two buyers can never
  get the same last packs). The seller accepts or declines (with a reason); orders not accepted
  in 24 hours expire (job every 15 minutes) and release the stock. Dispatch re-checks the batch
  is not expired (rule set `pilot-2026-11.2`, stage `DISPATCH`) and deducts the stock.
- Steps: accept → ready for pickup → picked up, or accept → out for delivery (delivery person's
  name and phone, deleted 30 days after the order completes) → delivered; then marked paid →
  completed. Buyers can cancel before dispatch; staff members cannot accept, decline or cancel.
- Quotes: the seller answers with a price per unit that is valid for 48 hours; the buyer orders
  that quantity at that price. A confidential seller's name stays hidden until they accept.
- A new database role (`marketplace_ordering`) places orders: it reads only offers on sale and
  writes only the buyer's own orders. Orders, quotes and carts have Row-Level Security: only the
  two businesses involved can see them.
- Repeat submits are safe: every checkout carries an idempotency key.
- Not yet: long-distance shipping (Phase 9c, a logistics partner). Online payment: Phase 9b.
- Tests: backend 111 unit and 105 integration; website 169 unit, Playwright and full-stack suites
  (the full-stack journey: quote → order with delivery → accept → deliver → paid).

## Phase 9b status
Done on 2026-11-09: **online payment with Razorpay Route (test mode).**
- Buyers can choose **Pay online** (UPI, card, netbanking in Razorpay's own checkout) where the
  seller allows it on the listing and has an active payout account. Card and UPI details never
  reach this site. Unpaid online orders expire after one hour and release their stock.
- **Payouts:** the business owner adds the bank account on the new Payouts page; the backend
  creates a Razorpay Route linked account (account, stakeholder, Route product, settlement
  account). Only the last four digits of the account number are stored here. Each step's id is
  saved, so a set-up that stops part-way resumes without a second account.
- **Money flow:** the buyer pays the platform. Once captured, the seller's share (total minus the
  platform fee, `PLATFORM_FEE_BP`, 0 for the pilot) is transferred to the seller's linked account
  **on hold**, and released when the order is delivered or picked up (which also completes it).
  If the order is declined, cancelled or expires, the transfer is reversed and the buyer refunded
  in full. The seller can accept only after payment.
- **Verification:** the checkout signature is checked (HMAC-SHA256), then the backend asks
  Razorpay itself for the payment; the browser's word is never enough. Every step looks at
  Razorpay first (existing transfers, refunds), so retries never charge, transfer or refund twice.
  A job every 10 minutes finishes anything left open (a closed browser, Razorpay unreachable).
- **Webhook** at `POST /api/v1/payments/razorpay-webhook`: checked against `RAZORPAY_WEBHOOK_SECRET`,
  each event handled once, and only used as a nudge to re-check with Razorpay. It needs a public
  URL, so it is off until a secret is set.
- Safety: the backend refuses to start with a key that is not `rzp_test_…` (unless
  `RAZORPAY_ALLOW_LIVE_KEYS=true`); secrets are never logged; a new database role
  (`marketplace_payments`) may change only the payment status of online orders; payments and
  payout accounts have Row-Level Security.
- **To use it:** put `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` (test mode) in `.env`.
  `cd backend && mvn test -Prazorpay-live` checks the keys against Razorpay's test API (creates a
  ₹1 test order; no money moves). To pay in test mode, use Razorpay's test card or UPI
  `success@razorpay` in the checkout window.
- **Razorpay must enable Route** on the platform's account (Dashboard → Route, or ask Razorpay
  support), even in test mode. Until then, payout set-up says payouts are not switched on yet,
  and the full-stack payment test is skipped.
- Tests: backend 119 unit and 116 integration (with a fake Razorpay); website 173 unit, 52
  Playwright and 26 full-stack tests (the payment one runs once Route is enabled).

If Homebrew's default JDK is newer than 21, run Maven with
`JAVA_HOME=$(brew --prefix openjdk@21)`.
