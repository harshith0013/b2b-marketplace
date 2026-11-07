-- Phase 5a: platform roles, stock photos, review decisions, and the reviewer database role.

-- Who may review listings for the whole platform. Separate from roles inside a business.
-- Grant the first admin with (see docs/admin-setup.md):
--   INSERT INTO platform_roles (user_id, role, granted_at)
--   SELECT id, 'ADMIN', now() FROM users WHERE email = '<their email>';
CREATE TABLE platform_roles (
    user_id    uuid        NOT NULL REFERENCES users (id),
    role       varchar(20) NOT NULL,
    granted_by uuid REFERENCES users (id),
    granted_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT platform_roles_role_check CHECK (role IN ('ADMIN', 'REVIEWER'))
);

-- Photos of a batch. The image itself is in private object storage under storage_key; the key is
-- never shown to anyone. Photos are re-encoded on upload, which drops camera metadata (GPS).
CREATE TABLE batch_photos (
    id             uuid PRIMARY KEY,
    business_id    uuid         NOT NULL REFERENCES businesses (id),
    batch_id       uuid         NOT NULL,
    kind           varchar(20)  NOT NULL,
    storage_key    varchar(300) NOT NULL UNIQUE,
    content_type   varchar(50)  NOT NULL,
    size_bytes     integer      NOT NULL,
    width          integer      NOT NULL,
    height         integer      NOT NULL,
    sha256         varchar(64)  NOT NULL,
    uploaded_by    uuid         NOT NULL REFERENCES users (id),
    created_at     timestamptz  NOT NULL,
    CONSTRAINT batch_photos_kind_check CHECK (kind IN ('PACK_FRONT', 'LABEL_DATE', 'OTHER')),
    CONSTRAINT batch_photos_content_type_check CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT batch_photos_size_positive CHECK (size_bytes > 0),
    CONSTRAINT batch_photos_batch_fk FOREIGN KEY (batch_id, business_id)
        REFERENCES inventory_batches (id, business_id)
);

CREATE INDEX batch_photos_batch_id_idx ON batch_photos (batch_id, created_at);

ALTER TABLE listings
    ADD COLUMN submitted_at     timestamptz,
    ADD COLUMN reviewed_at      timestamptz,
    ADD COLUMN rejection_reason varchar(500);

-- Every approve or reject, kept for good.
CREATE TABLE review_decisions (
    id               uuid PRIMARY KEY,
    listing_id       uuid         NOT NULL,
    business_id      uuid         NOT NULL,
    reviewer_user_id uuid         NOT NULL REFERENCES users (id),
    decision         varchar(20)  NOT NULL,
    reason           varchar(500),
    created_at       timestamptz  NOT NULL,
    CONSTRAINT review_decisions_decision_check CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT review_decisions_reason_on_reject CHECK (decision = 'APPROVED' OR reason IS NOT NULL),
    CONSTRAINT review_decisions_listing_fk FOREIGN KEY (listing_id, business_id)
        REFERENCES listings (id, business_id)
);

CREATE INDEX review_decisions_listing_id_idx ON review_decisions (listing_id, created_at);

CREATE FUNCTION append_only_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% is append-only (% rejected)', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER review_decisions_no_update_or_delete
    BEFORE UPDATE OR DELETE ON review_decisions
    FOR EACH ROW EXECUTE FUNCTION append_only_reject_change();

-- A seller's own transaction can never publish a listing or approve/reject a batch: only the
-- reviewer role can. (A paused listing may be resumed by its seller.)
CREATE FUNCTION seller_review_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF current_user = 'marketplace_tenant' AND NEW.status IS DISTINCT FROM OLD.status THEN
        IF TG_TABLE_NAME = 'listings' AND NEW.status = 'PUBLISHED' AND OLD.status <> 'PAUSED' THEN
            RAISE EXCEPTION 'a seller cannot publish listing % without review', OLD.id;
        END IF;
        IF TG_TABLE_NAME = 'inventory_batches' AND NEW.status IN ('APPROVED', 'REJECTED') THEN
            RAISE EXCEPTION 'a seller cannot set the review result of batch %', OLD.id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER listings_seller_review_guard
    BEFORE UPDATE ON listings
    FOR EACH ROW EXECUTE FUNCTION seller_review_guard();

CREATE TRIGGER inventory_batches_seller_review_guard
    BEFORE UPDATE ON inventory_batches
    FOR EACH ROW EXECUTE FUNCTION seller_review_guard();

-- --- Row-Level Security ---------------------------------------------------------------------

GRANT SELECT, INSERT, DELETE ON batch_photos TO marketplace_tenant;
GRANT SELECT ON review_decisions TO marketplace_tenant;

ALTER TABLE batch_photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE batch_photos FORCE ROW LEVEL SECURITY;
CREATE POLICY batch_photos_own_business ON batch_photos
    USING (business_id = app_current_business_id())
    WITH CHECK (business_id = app_current_business_id());

ALTER TABLE review_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE review_decisions FORCE ROW LEVEL SECURITY;
CREATE POLICY review_decisions_own_business ON review_decisions FOR SELECT
    USING (business_id = app_current_business_id());

-- Reviewers: after the application has checked the person's platform role, a review transaction
-- runs SET LOCAL ROLE marketplace_reviewer (platform.TenantScope.enterReview). That role can read
-- every business's seller data, but change only review results: column grants below limit which
-- columns, and it has no INSERT or DELETE on seller tables.
DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'marketplace_reviewer') THEN
        CREATE ROLE marketplace_reviewer NOLOGIN;
    END IF;
END;
$$;

GRANT marketplace_reviewer TO CURRENT_USER;

GRANT USAGE ON SCHEMA public TO marketplace_reviewer;
GRANT SELECT ON users, businesses, business_memberships, platform_roles, categories, products,
    product_variants, warehouses, inventory_batches, listings, price_tiers, batch_photos,
    review_decisions TO marketplace_reviewer;
GRANT INSERT ON audit_events, review_decisions TO marketplace_reviewer;
GRANT UPDATE (status, updated_at) ON inventory_batches TO marketplace_reviewer;
GRANT UPDATE (status, updated_at, reviewed_at, rejection_reason) ON listings TO marketplace_reviewer;
GRANT UPDATE (review_status, updated_at) ON products TO marketplace_reviewer;

CREATE POLICY warehouses_reviewer_read ON warehouses FOR SELECT TO marketplace_reviewer USING (true);
CREATE POLICY inventory_batches_reviewer_read ON inventory_batches FOR SELECT TO marketplace_reviewer
    USING (true);
CREATE POLICY inventory_batches_reviewer_update ON inventory_batches FOR UPDATE TO marketplace_reviewer
    USING (true) WITH CHECK (true);
CREATE POLICY listings_reviewer_read ON listings FOR SELECT TO marketplace_reviewer USING (true);
CREATE POLICY listings_reviewer_update ON listings FOR UPDATE TO marketplace_reviewer
    USING (true) WITH CHECK (true);
CREATE POLICY price_tiers_reviewer_read ON price_tiers FOR SELECT TO marketplace_reviewer USING (true);
CREATE POLICY batch_photos_reviewer_read ON batch_photos FOR SELECT TO marketplace_reviewer
    USING (true);
CREATE POLICY review_decisions_reviewer_read ON review_decisions FOR SELECT TO marketplace_reviewer
    USING (true);
CREATE POLICY review_decisions_reviewer_insert ON review_decisions FOR INSERT TO marketplace_reviewer
    WITH CHECK (true);
