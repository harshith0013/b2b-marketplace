# Architecture

## Shape of the system

```
 Browser (mobile-first, en/te/hi)
        │  HTTPS
        ▼
 ┌─────────────────────────┐        ┌───────────────────────────────┐
 │ Next.js (App Router)    │  HTTP  │ Spring Boot modular monolith  │
 │ - pages, i18n, proxy    │ ─────► │ - REST API /api/v1/**         │
 │ - BFF route handlers    │ server │ - Spring Security (deny-all   │
 │   (/api/**)             │  only  │   default; Google ID token)   │
 └─────────────────────────┘        │ - Flyway-owned schema         │
                                    └──────────────┬────────────────┘
                                                   ▼
                                    PostgreSQL 17 + pgvector + pg_trgm
```

The browser never calls the Spring Boot API directly. Next.js route handlers act as a
backend-for-frontend, which keeps tokens server-side, avoids CORS entirely and lets us shape
responses for small screens.

## Sign-in (Phase 3)

1. `/[lang]/login` → `/api/auth/login` starts an OpenID Connect authorization-code flow with
   Google (PKCE, state, nonce), using `openid-client`.
2. Google redirects to `/api/auth/callback`; the server exchanges the code, verifies the ID token
   and requires a verified email, then stores the ID token in an encrypted httpOnly cookie
   (`iron-session`). The browser never sees a token.
3. Server Components and Server Actions send that ID token to the API as a bearer token. The API
   checks signature, issuer, audience (the Google client id), expiry and `email_verified`, then
   maps the person to a `users` row by `identity_subject`.
4. `src/proxy.ts` sends signed-out visitors of protected pages to the login page, and sends an
   expired session straight back through Google, which re-signs the person in silently.

Locally and in CI the "Google" is `mock-oauth2-server` in Docker Compose (any email works).
Setup for real Google: `docs/google-signin-setup.md`.

## Backend: modular monolith

One deployable, with one package per business module under `com.b2bmarket`:

authentication, users, businesses, businessverification, catalog, products, inventory, batches,
listings, sellers, buyers, search, ai, recommendations, compliance, orders, quotations, payments,
settlements, shipping, notifications, disputes, privacy, auditing, administration.

Cross-cutting infrastructure (security config, error handling, request ids, clock) lives in
`com.b2bmarket.platform` and contains no business logic.

Rules every module follows:

- Controller → Service → Repository. Controllers are thin; transactions live on service methods.
- Public API responses are DTOs (Java records). JPA entities never leave their module.
- A module calls another module only through that module's public service, never its repositories.
- Every query that touches business data is scoped by the caller's business id (tenant boundary).
- Schema changes only through new Flyway migrations. Applied migrations are never edited.
- Time comes from the injected `Clock`, never `Instant.now()` directly, so expiry rules are testable.

`ModuleStructureTest` (Spring Modulith) fails the build on boundary violations or cycles. Seller
modules depend in one direction: listings → batches → inventory (warehouses) and products →
catalog; all of them go through `businesses.TenantAccess` for membership checks.

## Tenant isolation (Phase 4)

Two layers. First, every service method checks membership and role (`TenantAccess`: 404 for
non-members, 403 for the wrong role) and every repository query names the business. Second,
PostgreSQL Row-Level Security on `warehouses`, `inventory_batches`, `listings` and `price_tiers`:
after the check, the transaction runs `set_config('app.business_id', …, true)` and
`SET LOCAL ROLE marketplace_tenant`, so the database itself returns and accepts only that
business's rows. Both settings end with the transaction. Composite foreign keys
`(id, business_id)` stop a row of one business pointing at another's. RLS guards against
application bugs, not SQL injection (injected SQL could reset the role); a separate non-owner
login for the application is planned for Phase 12.

## Frontend

- Next.js 16 App Router with Cache Components and Partial Prefetching enabled.
- All pages live under `app/[lang]` (`en`, `te`, `hi`). `src/proxy.ts` redirects `/` to the
  saved language (cookie), else the browser's `Accept-Language`, else English.
- Translations are JSON dictionaries in `src/i18n/dictionaries`. A unit test fails the build if
  Telugu or Hindi is missing any key that English has.
- Tailwind CSS v4 with design tokens in `globals.css`, shadcn/ui conventions (`components.json`,
  `src/components/ui`). shadcn's CLI can add more components later.
- Security headers set in `next.config.ts`. A nonce-based Content-Security-Policy is added in
  Phase 3.

## Development phases

| Phase | Scope | Notes |
| --- | --- | --- |
| 1 | Monorepo, Spring Boot + Next.js, PostgreSQL, Flyway, Docker Compose, health checks, tests, CI | **Done** (see README) |
| 2 | Design system and simple responsive UI shells | **Done**: tokens, core components, buyer home, seller home, listing wizard shell (see README) |
| 3 | Authentication, business accounts, access control | **Done.** Sign in with Google (OIDC, server-side session in an encrypted cookie), users, businesses, OWNER/MANAGER/STAFF roles, invitations by email, tenant isolation tests, append-only audit log, Spring Modulith boundary test. Email or phone OTP can be added later |
| 4 | Database, catalogue, inventory | **Done.** Non-food FMCG categories, products and variants, warehouses, batches, draft listings, price tiers; Row-Level Security on seller tables; warehouses page |
| 5 | Seller dashboard and stock uploads | Step-by-step listing, photos to private S3, baseline eligibility checks, minimal admin approval queue |
| 6 | Buyer portal, discovery, filtering | Full-text + trigram search, filters (location, price, brand, MOQ, shelf life) |
| 7 | AI search and recommendations | Spring AI added here; strict tool calling; evaluation set of real queries in en/te/hi and mixed language |
| 8 | Expiry detection, Batch Passport, compliance engine | OCR comparison, versioned rules, checks at listing/publish/checkout/dispatch |
| 9 | Cart, quotations, orders, payments | Order state machine, inventory reservations, idempotency, provider sandbox, COD support |
| 10 | Confidential trading and privacy controls | Confidential Deal Mode disclosure rules, DPDP requests |
| 11 | Admin, disputes, analytics | Full admin tooling, dispute workflow |
| 12 | Testing, security review, AWS deployment, pilot | ECS Fargate, RDS, WAF, KMS, Secrets Manager, CloudWatch |

Security and baseline eligibility are built into every phase, not postponed to Phase 12.

## Open product decisions

These affect later phases and should be settled before the phase that needs them:

1. **Pilot category**: settled 2026-11-05: non-food FMCG (personal care, home care, household,
   stationery). Regulated categories wait for the compliance engine (Phase 8).
2. **Pilot city and delivery model** (before Phase 9): seller-delivered, buyer pickup or a
   logistics partner.
3. **Payment model** (before Phase 9): marketplace split settlement through a licensed payment
   aggregator; whether COD and buyer credit are supported at launch.
4. **Row-Level Security**: settled 2026-11-05: on seller tables, as defense in depth (see
   "Tenant isolation").
5. **Confidential Deal Mode disclosure matrix** (before Phase 10): exactly which identity fields
   are visible to whom before quotation, after order and on the GST invoice.
6. **Product name and domain**: replace the `B2B Marketplace` working name and `com.b2bmarket`
   package before external users see anything.

## Dependencies and risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Spring AI / Spring Boot version mismatch | Build or runtime failures in Phase 7 | Spring AI not added until Phase 7; pick the version from its compatibility matrix then |
| SMS OTP in India requires TRAI DLT registration | Login blocked at launch | Start sender-id and template registration during Phase 3 |
| FSSAI and food e-commerce obligations | Legal exposure if regulated food is listed early | Narrow pilot category; compliance engine with versioned rules reviewed by counsel |
| Payment aggregator rules (RBI) | Cannot hold or route marketplace funds directly | Use a provider's marketplace/split-settlement product; never hold funds |
| DPDP Act and Rules | Fines, loss of trust | Consent records, purpose limitation, deletion workflows, India-region hosting |
| Cross-tenant data leaks | Severe privacy breach | Business-scoped queries, object-level checks, automated leak tests from Phase 3 |
| OCR/AI misreading expiry dates | Expired goods published | OCR only flags; human review for conflicts; checks repeated at checkout and dispatch |
| Low digital literacy of users | Poor adoption | Large touch targets, three languages, minimal steps, WhatsApp notifications later |
| AWS cost before revenue | Burn | Single small RDS and Fargate service for pilot; no Redis until measured need |

## Data model

Migrated so far: users, businesses, business_memberships, business_invitations, audit_events
(Phase 3); categories, products, product_variants, warehouses, inventory_batches, listings,
price_tiers (Phase 4). The rest is still a proposal, created incrementally. All primary keys are UUIDs; every table has
`created_at`/`updated_at` (timestamptz, UTC); business-owned tables carry `business_id`.

| Table | Purpose and key columns |
| --- | --- |
| users | `identity_subject` (sign-in provider's user id, unique), email (unique, verified), phone, display name, preferred locale, status |
| businesses | legal name, trade name, type (kirana, wholesaler, manufacturer…), GSTIN (unique), city, state, pincode, verification status |
| business_memberships | user_id, business_id, role (owner, manager, staff); unique (user_id, business_id) |
| roles | platform roles (buyer, seller, admin, reviewer) |
| licences | business_id, type (GST, FSSAI…), number, valid_from, valid_to, status |
| verification_documents | business_id, licence_id, S3 key (private), checksum, review status |
| categories | parent_id, slug, names per locale, regulated flag |
| products | category_id, brand, name, attributes (jsonb), regulated flag |
| product_variants | product_id, pack size, unit, GTIN/barcode |
| warehouses | business_id, address, pincode, geo point |
| inventory_batches | variant_id, seller business_id, batch no, mfg date, expiry date, date type (expiry / use-by / best-before), quantity, verification status; expiry immutable after approval |
| evidence_records | batch_id, type (photo, label OCR, invoice), S3 key, perceptual hash, OCR output |
| compliance_reviews | batch_id or listing_id, rule set version, result, reviewer, reasons |
| listings | batch_id, warehouse_id, status, clearance reason, MOQ, visibility (public / confidential) |
| price_tiers | listing_id, min quantity, unit price (paise, bigint), GST rate |
| carts, quotations, orders, order_items | Phase 9; prices captured at order time; order status as a state machine |
| inventory_reservations | listing_id, order_id, quantity, expires_at |
| payments, settlements, invoices | Phase 9; idempotency keys unique; provider references only, never card data |
| shipments, disputes | Phase 9–11 |
| ai_search_requests, recommendations | Phase 7; queries retained per privacy policy, scoped to business |
| notifications | channel, template, recipient, status |
| audit_events | append-only: actor, business, action, target, request id, timestamp |

Money is stored as integer paise, never floating point. Quantities carry an explicit unit.
