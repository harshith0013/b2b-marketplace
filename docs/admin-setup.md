# Platform admins and reviewers

Admins and reviewers check listings in the review queue (`/api/v1/admin/reviews`) before they are
published. These roles are separate from OWNER / MANAGER / STAFF inside a business, and a
reviewer cannot decide on listings of a business they belong to.

| Role | Can |
| --- | --- |
| `ADMIN` | Review listings (later: manage reviewers, disputes) |
| `REVIEWER` | Review listings |

## Granting the first admin

The person must have signed in once, so their account exists. Then, connected to the database as
the application's database user:

```sql
INSERT INTO platform_roles (user_id, role, granted_at)
SELECT id, 'ADMIN', now() FROM users WHERE email = 'their.email@example.com';
```

Use `'REVIEWER'` for a reviewer. To remove a role:

```sql
DELETE FROM platform_roles WHERE role = 'REVIEWER'
  AND user_id = (SELECT id FROM users WHERE email = 'their.email@example.com');
```

Locally, with Docker Compose running:

```bash
docker compose exec postgres psql -U b2b_app -d b2b_marketplace
```

An API and screen for managing reviewers come with the admin tooling in Phase 11.

## Local shortcut

For trying the review queue on your own machine only, set `BOOTSTRAP_ADMIN_EMAIL` in `.env` to the
email you sign in with and restart the stack. That email becomes an admin the next time it signs
in, and the grant is written to the audit log. **Never set this in a shared or deployed
environment**; there, use the SQL above.

## How it is protected

- The API checks the platform role on every review request (403 for everyone else).
- The review transaction then switches to the `marketplace_reviewer` database role. It can read
  every business's listings, batches and photos, but can change only review results (status,
  review time, rejection reason): no prices, quantities or dates, and it cannot create or delete
  seller data.
- A seller's own transaction can never publish a listing or approve a batch; the database refuses.
- Every decision is kept in `review_decisions` (append-only) and the audit log.
