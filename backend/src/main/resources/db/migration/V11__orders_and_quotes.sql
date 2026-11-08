-- Phase 9a: delivery options, carts, quotes, orders and stock reservations.
-- Money in paise, quantities in the listing's unit. Prices are copied onto orders when placed.

ALTER TABLE listings
    ADD COLUMN pickup_available      boolean NOT NULL DEFAULT true,
    ADD COLUMN own_delivery          boolean NOT NULL DEFAULT false,
    ADD COLUMN delivery_pincodes     varchar(6)[] NOT NULL DEFAULT '{}',
    ADD COLUMN delivery_charge_paise bigint  NOT NULL DEFAULT 0,
    ADD COLUMN cod_accepted          boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT listings_some_delivery CHECK (pickup_available OR own_delivery),
    ADD CONSTRAINT listings_delivery_charge_not_negative CHECK (delivery_charge_paise >= 0),
    ADD CONSTRAINT listings_delivery_pincodes_count CHECK (cardinality(delivery_pincodes) <= 50);

CREATE TABLE cart_items (
    id                uuid PRIMARY KEY,
    buyer_business_id uuid        NOT NULL REFERENCES businesses (id),
    listing_id        uuid        NOT NULL REFERENCES listings (id),
    quantity          bigint      NOT NULL,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT cart_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT cart_items_buyer_listing_key UNIQUE (buyer_business_id, listing_id)
);

CREATE TABLE orders (
    id                     uuid PRIMARY KEY,
    buyer_business_id      uuid         NOT NULL REFERENCES businesses (id),
    seller_business_id     uuid         NOT NULL REFERENCES businesses (id),
    -- The listing was confidential: the seller's name is shown to the buyer only once accepted.
    seller_confidential    boolean      NOT NULL DEFAULT false,
    status                 varchar(20)  NOT NULL,
    payment_method         varchar(20)  NOT NULL,
    payment_status         varchar(20)  NOT NULL DEFAULT 'PENDING',
    delivery_method        varchar(20)  NOT NULL,
    delivery_address       varchar(300),
    delivery_pincode       varchar(6),
    pickup_city            varchar(100) NOT NULL,
    pickup_state           varchar(100) NOT NULL,
    items_total_paise      bigint       NOT NULL,
    gst_total_paise        bigint       NOT NULL,
    delivery_charge_paise  bigint       NOT NULL,
    total_paise            bigint       NOT NULL,
    quote_id               uuid,
    idempotency_key        varchar(64)  NOT NULL,
    status_reason          varchar(300),
    delivery_person_name   varchar(100),
    delivery_person_phone  varchar(15),
    placed_at              timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL,
    completed_at           timestamptz,
    CONSTRAINT orders_status_check CHECK (status IN ('PLACED', 'ACCEPTED', 'DECLINED', 'CANCELLED',
        'EXPIRED', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'PICKED_UP', 'COMPLETED')),
    CONSTRAINT orders_payment_method_check CHECK (payment_method IN ('COD', 'PAY_ON_PICKUP')),
    CONSTRAINT orders_payment_status_check CHECK (payment_status IN ('PENDING', 'PAID')),
    CONSTRAINT orders_delivery_method_check CHECK (delivery_method IN ('PICKUP', 'SELLER_DELIVERY')),
    CONSTRAINT orders_delivery_address_needed CHECK (delivery_method = 'PICKUP'
        OR (delivery_address IS NOT NULL AND delivery_pincode ~ '^[1-9][0-9]{5}$')),
    CONSTRAINT orders_totals_add_up CHECK (total_paise = items_total_paise + gst_total_paise + delivery_charge_paise),
    CONSTRAINT orders_not_to_self CHECK (buyer_business_id <> seller_business_id),
    CONSTRAINT orders_idempotency_key UNIQUE (buyer_business_id, idempotency_key)
);

CREATE INDEX orders_buyer_idx ON orders (buyer_business_id, placed_at DESC);
CREATE INDEX orders_seller_idx ON orders (seller_business_id, placed_at DESC);
CREATE INDEX orders_placed_idx ON orders (placed_at) WHERE status = 'PLACED';

CREATE TABLE order_items (
    id                 uuid PRIMARY KEY,
    order_id           uuid         NOT NULL REFERENCES orders (id),
    listing_id         uuid         NOT NULL REFERENCES listings (id),
    batch_id           uuid         NOT NULL REFERENCES inventory_batches (id),
    product_line       varchar(300) NOT NULL,
    quantity           bigint       NOT NULL,
    quantity_unit      varchar(10)  NOT NULL,
    unit_price_paise   bigint       NOT NULL,
    gst_rate_bp        integer      NOT NULL,
    line_total_paise   bigint       NOT NULL,
    gst_paise          bigint       NOT NULL,
    CONSTRAINT order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT order_items_line_total CHECK (line_total_paise = quantity * unit_price_paise)
);

CREATE INDEX order_items_order_idx ON order_items (order_id);

-- Stock held for an order until it is delivered (consumed) or ends (released).
CREATE TABLE inventory_reservations (
    id                 uuid PRIMARY KEY,
    order_id           uuid        NOT NULL REFERENCES orders (id),
    batch_id           uuid        NOT NULL REFERENCES inventory_batches (id),
    seller_business_id uuid        NOT NULL REFERENCES businesses (id),
    buyer_business_id  uuid        NOT NULL REFERENCES businesses (id),
    quantity           bigint      NOT NULL,
    status             varchar(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at         timestamptz NOT NULL,
    created_at         timestamptz NOT NULL,
    CONSTRAINT inventory_reservations_quantity_positive CHECK (quantity > 0),
    CONSTRAINT inventory_reservations_status_check CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED'))
);

CREATE INDEX inventory_reservations_active_idx ON inventory_reservations (batch_id) WHERE status = 'ACTIVE';

CREATE TABLE quotes (
    id                       uuid PRIMARY KEY,
    buyer_business_id        uuid         NOT NULL REFERENCES businesses (id),
    seller_business_id       uuid         NOT NULL REFERENCES businesses (id),
    listing_id               uuid         NOT NULL REFERENCES listings (id),
    seller_confidential      boolean      NOT NULL DEFAULT false,
    product_line             varchar(300) NOT NULL,
    quantity                 bigint       NOT NULL,
    quantity_unit            varchar(10)  NOT NULL,
    note                     varchar(500),
    status                   varchar(20)  NOT NULL DEFAULT 'REQUESTED',
    offered_unit_price_paise bigint,
    valid_until              timestamptz,
    created_at               timestamptz  NOT NULL,
    updated_at               timestamptz  NOT NULL,
    CONSTRAINT quotes_quantity_positive CHECK (quantity > 0),
    CONSTRAINT quotes_status_check CHECK (status IN ('REQUESTED', 'OFFERED', 'DECLINED', 'ORDERED', 'EXPIRED')),
    CONSTRAINT quotes_offer_complete CHECK (status <> 'OFFERED' OR (offered_unit_price_paise > 0 AND valid_until IS NOT NULL)),
    CONSTRAINT quotes_not_to_self CHECK (buyer_business_id <> seller_business_id)
);

CREATE INDEX quotes_buyer_idx ON quotes (buyer_business_id, created_at DESC);
CREATE INDEX quotes_seller_idx ON quotes (seller_business_id, created_at DESC);

-- --- Row-Level Security ---------------------------------------------------------------------
-- Sellers and buyers (marketplace_tenant): an order, its items and reservations, and a quote are
-- visible to the two businesses in it and nobody else. Carts belong to the buyer.

GRANT SELECT, INSERT, UPDATE, DELETE ON cart_items TO marketplace_tenant;
GRANT SELECT, UPDATE ON orders, quotes TO marketplace_tenant;
GRANT SELECT ON order_items TO marketplace_tenant;
GRANT SELECT, UPDATE ON inventory_reservations TO marketplace_tenant;

ALTER TABLE cart_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE cart_items FORCE ROW LEVEL SECURITY;
CREATE POLICY cart_items_own ON cart_items
    USING (buyer_business_id = app_current_business_id())
    WITH CHECK (buyer_business_id = app_current_business_id());

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
CREATE POLICY orders_parties ON orders TO marketplace_tenant
    USING (buyer_business_id = app_current_business_id() OR seller_business_id = app_current_business_id())
    WITH CHECK (buyer_business_id = app_current_business_id() OR seller_business_id = app_current_business_id());

ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items FORCE ROW LEVEL SECURITY;
CREATE POLICY order_items_parties ON order_items FOR SELECT TO marketplace_tenant
    USING (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_items.order_id));

ALTER TABLE inventory_reservations ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_reservations FORCE ROW LEVEL SECURITY;
CREATE POLICY reservations_parties ON inventory_reservations TO marketplace_tenant
    USING (buyer_business_id = app_current_business_id() OR seller_business_id = app_current_business_id())
    WITH CHECK (buyer_business_id = app_current_business_id() OR seller_business_id = app_current_business_id());

ALTER TABLE quotes ENABLE ROW LEVEL SECURITY;
ALTER TABLE quotes FORCE ROW LEVEL SECURITY;
CREATE POLICY quotes_parties ON quotes TO marketplace_tenant
    USING (buyer_business_id = app_current_business_id() OR seller_business_id = app_current_business_id())
    WITH CHECK (buyer_business_id = app_current_business_id() OR seller_business_id = app_current_business_id());

-- Placing orders and asking for quotes (marketplace_ordering), for the buyer in app.business_id:
-- reads only stock that is on sale, may lock its batch row (FOR UPDATE needs an UPDATE grant), and
-- writes orders, items, reservations and quotes for that buyer. The reservation sweep runs as this
-- role with no business set and may only release expired reservations and expire orders.
DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'marketplace_ordering') THEN
        CREATE ROLE marketplace_ordering NOLOGIN;
    END IF;
END;
$$;

GRANT marketplace_ordering TO CURRENT_USER;
GRANT USAGE ON SCHEMA public TO marketplace_ordering;
GRANT SELECT ON categories, products, product_variants, listings, price_tiers, inventory_batches
    TO marketplace_ordering;
GRANT SELECT (id, legal_name, trade_name, verification_status) ON businesses TO marketplace_ordering;
GRANT SELECT (id, business_id, city, state, pincode) ON warehouses TO marketplace_ordering;
GRANT UPDATE (updated_at) ON inventory_batches TO marketplace_ordering;
GRANT SELECT, INSERT ON orders, order_items, quotes TO marketplace_ordering;
GRANT UPDATE (status, updated_at) ON orders, quotes TO marketplace_ordering;
-- The nightly clean-up removes delivery people's names and phones 30 days after completion.
GRANT UPDATE (delivery_person_name, delivery_person_phone) ON orders TO marketplace_ordering;
GRANT SELECT, INSERT ON inventory_reservations TO marketplace_ordering;
GRANT UPDATE (status) ON inventory_reservations TO marketplace_ordering;
GRANT SELECT, DELETE ON cart_items TO marketplace_ordering;
GRANT INSERT ON audit_events TO marketplace_ordering;
CREATE POLICY audit_events_ordering_insert ON audit_events FOR INSERT TO marketplace_ordering
    WITH CHECK (true);

CREATE POLICY listings_ordering_read ON listings FOR SELECT TO marketplace_ordering
    USING (status = 'PUBLISHED');
CREATE POLICY inventory_batches_ordering_read ON inventory_batches FOR SELECT TO marketplace_ordering
    USING (status = 'APPROVED');
CREATE POLICY inventory_batches_ordering_lock ON inventory_batches FOR UPDATE TO marketplace_ordering
    USING (status = 'APPROVED') WITH CHECK (status = 'APPROVED');
CREATE POLICY price_tiers_ordering_read ON price_tiers FOR SELECT TO marketplace_ordering
    USING (EXISTS (SELECT 1 FROM listings l WHERE l.id = price_tiers.listing_id));
CREATE POLICY warehouses_ordering_read ON warehouses FOR SELECT TO marketplace_ordering USING (true);
CREATE POLICY cart_items_ordering ON cart_items TO marketplace_ordering
    USING (buyer_business_id = app_current_business_id());
-- Counting what is already reserved needs every active reservation of the batch.
CREATE POLICY reservations_ordering_read ON inventory_reservations FOR SELECT TO marketplace_ordering
    USING (true);
CREATE POLICY reservations_ordering_insert ON inventory_reservations FOR INSERT TO marketplace_ordering
    WITH CHECK (buyer_business_id = app_current_business_id());
CREATE POLICY reservations_ordering_release ON inventory_reservations FOR UPDATE TO marketplace_ordering
    USING (status = 'ACTIVE' AND (app_current_business_id() IS NULL
        OR buyer_business_id = app_current_business_id()))
    WITH CHECK (status IN ('RELEASED', 'ACTIVE'));
CREATE POLICY orders_ordering ON orders TO marketplace_ordering
    -- With no business set (the jobs): orders to expire, and completed ones to clean up. EXPIRED
    -- is readable too because UPDATE ... RETURNING checks the changed row against this rule.
    USING (buyer_business_id = app_current_business_id()
        OR (app_current_business_id() IS NULL AND status IN ('PLACED', 'EXPIRED', 'COMPLETED')))
    WITH CHECK (buyer_business_id = app_current_business_id()
        OR (app_current_business_id() IS NULL AND status IN ('EXPIRED', 'COMPLETED')));
CREATE POLICY order_items_ordering ON order_items TO marketplace_ordering
    USING (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_items.order_id))
    WITH CHECK (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_items.order_id));
CREATE POLICY quotes_ordering ON quotes TO marketplace_ordering
    USING (buyer_business_id = app_current_business_id())
    WITH CHECK (buyer_business_id = app_current_business_id());

