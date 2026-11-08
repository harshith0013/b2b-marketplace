-- Phase 6: public offer search. Search indexes, and the database role that anonymous buyers'
-- queries run as.

-- Full-text search over brand, name and description ('simple': no language-specific stemming, as
-- names mix English, Telugu and Hindi), plus trigram matching so typos still find products.
ALTER TABLE products
    ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', brand || ' ' || name || ' ' || coalesce(description, ''))) STORED;

CREATE INDEX products_search_vector_idx ON products USING gin (search_vector);
CREATE INDEX products_brand_name_trgm_idx ON products USING gin ((lower(brand || ' ' || name)) gin_trgm_ops);
CREATE INDEX listings_published_idx ON listings (reviewed_at DESC) WHERE status = 'PUBLISHED';

-- Public (anonymous) queries switch to this role: SET LOCAL ROLE marketplace_public
-- (platform.TenantScope.enterPublic). Its row policies show only what is on sale: published
-- listings whose batch is approved, with their prices, warehouses (city, state and PIN code only)
-- and photos. It has no access to users, memberships, invitations, audit or review history, and
-- cannot write anything.
DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'marketplace_public') THEN
        CREATE ROLE marketplace_public NOLOGIN;
    END IF;
END;
$$;

GRANT marketplace_public TO CURRENT_USER;

GRANT USAGE ON SCHEMA public TO marketplace_public;
GRANT SELECT ON categories, products, product_variants, listings, inventory_batches, price_tiers
    TO marketplace_public;
GRANT SELECT (id, legal_name, trade_name, verification_status) ON businesses TO marketplace_public;
GRANT SELECT (id, business_id, city, state, pincode, active) ON warehouses TO marketplace_public;
GRANT SELECT (id, business_id, batch_id, kind, storage_key, content_type, width, height, created_at)
    ON batch_photos TO marketplace_public;

CREATE POLICY listings_public_read ON listings FOR SELECT TO marketplace_public
    USING (status = 'PUBLISHED');

CREATE POLICY inventory_batches_public_read ON inventory_batches FOR SELECT TO marketplace_public
    USING (status = 'APPROVED'
        AND EXISTS (SELECT 1 FROM listings l WHERE l.batch_id = inventory_batches.id));

CREATE POLICY price_tiers_public_read ON price_tiers FOR SELECT TO marketplace_public
    USING (EXISTS (SELECT 1 FROM listings l WHERE l.id = price_tiers.listing_id));

CREATE POLICY warehouses_public_read ON warehouses FOR SELECT TO marketplace_public
    USING (EXISTS (SELECT 1 FROM inventory_batches b WHERE b.warehouse_id = warehouses.id));

CREATE POLICY batch_photos_public_read ON batch_photos FOR SELECT TO marketplace_public
    USING (EXISTS (SELECT 1 FROM inventory_batches b WHERE b.id = batch_photos.batch_id));
