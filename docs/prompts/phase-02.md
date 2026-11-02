# Prompt: Phase 2 — simple responsive UI and design system

Read CLAUDE.md and docs/ARCHITECTURE.md. Phase 1 must be verified first.

Goal: the design system and UI shells, with no real data yet.

Scope:
- Design tokens (colour, type scale, spacing, radius) building on `frontend/src/app/globals.css`.
- Fonts that render Latin, Telugu and Devanagari well, self-hosted.
- Core components: button, input, select, card, badge, dialog, toast, skeleton, empty state,
  error state. Minimum 48px touch targets, visible focus, WCAG AA contrast in light and dark.
- Buyer home shell: search, AI assistant entry, Buy, Sell, Wholesale Deals, Clearance Offers,
  Categories, Nearby Suppliers, My Orders. Simple, not an enterprise dashboard.
- Seller home shell and a step-by-step listing wizard shell (no saving yet).
- Every string in en, te and hi.

Non-goals: authentication, API calls, database tables, real products or prices. Placeholder
content must be clearly marked as sample data.

Acceptance criteria:
- Lint, typecheck, unit tests and build pass; new components have unit tests.
- Playwright: each shell renders on the mobile and desktop projects in all three languages.
- Lighthouse accessibility score of at least 95 on the buyer home.

First present the plan and the file list and wait for my approval. Report at the end. Do not
start Phase 3.
