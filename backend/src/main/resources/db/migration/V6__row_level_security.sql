-- Phase 4: Row-Level Security on seller data, as defense in depth behind the application's own
-- membership checks.
--
-- How it is used: after checking membership, a service transaction runs
--     SELECT set_config('app.business_id', '<business id>', true);
--     SET LOCAL ROLE marketplace_tenant;
-- (see platform.tenancy.TenantScope). From then on, until the transaction ends, every query on
-- these tables sees and writes only that business's rows, even if a WHERE clause is forgotten.
-- No business id set means no rows. Both settings end with the transaction, so a pooled
-- connection never carries them into the next request.
--
-- Limit: this guards against application bugs, not SQL injection (injected SQL could RESET ROLE).
-- A dedicated non-owner login for the application is planned for Phase 12.

DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'marketplace_tenant') THEN
        CREATE ROLE marketplace_tenant NOLOGIN;
    END IF;
END;
$$;

-- The application's login (which also runs these migrations) may switch to the tenant role.
-- Needed on managed PostgreSQL (RDS) where that login is not a superuser.
GRANT marketplace_tenant TO CURRENT_USER;

GRANT USAGE ON SCHEMA public TO marketplace_tenant;
GRANT SELECT ON users, businesses, business_memberships, categories, products, product_variants
    TO marketplace_tenant;
GRANT INSERT ON audit_events TO marketplace_tenant;
GRANT SELECT, INSERT, UPDATE ON warehouses, inventory_batches, listings TO marketplace_tenant;
GRANT SELECT, INSERT, UPDATE, DELETE ON price_tiers TO marketplace_tenant;

-- A custom setting reads as '' (not NULL) after an earlier SET LOCAL ended, so treat '' as unset.
CREATE FUNCTION app_current_business_id() RETURNS uuid
    LANGUAGE sql STABLE AS
$$
SELECT NULLIF(current_setting('app.business_id', true), '')::uuid
$$;

ALTER TABLE warehouses ENABLE ROW LEVEL SECURITY;
ALTER TABLE warehouses FORCE ROW LEVEL SECURITY;
CREATE POLICY warehouses_own_business ON warehouses
    USING (business_id = app_current_business_id())
    WITH CHECK (business_id = app_current_business_id());

ALTER TABLE inventory_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_batches FORCE ROW LEVEL SECURITY;
CREATE POLICY inventory_batches_own_business ON inventory_batches
    USING (business_id = app_current_business_id())
    WITH CHECK (business_id = app_current_business_id());

ALTER TABLE listings ENABLE ROW LEVEL SECURITY;
ALTER TABLE listings FORCE ROW LEVEL SECURITY;
CREATE POLICY listings_own_business ON listings
    USING (business_id = app_current_business_id())
    WITH CHECK (business_id = app_current_business_id());

ALTER TABLE price_tiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE price_tiers FORCE ROW LEVEL SECURITY;
CREATE POLICY price_tiers_own_business ON price_tiers
    USING (business_id = app_current_business_id())
    WITH CHECK (business_id = app_current_business_id());
