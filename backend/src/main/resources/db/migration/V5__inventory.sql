-- Phase 4: a seller's warehouses, stock batches, draft listings and their price tiers.
-- Every table carries business_id. Composite foreign keys (child id, business_id) make the
-- database itself refuse to link one business's batch to another business's warehouse or listing.

CREATE TABLE warehouses (
    id           uuid PRIMARY KEY,
    business_id  uuid         NOT NULL REFERENCES businesses (id),
    name         varchar(100) NOT NULL,
    address_line varchar(300) NOT NULL,
    city         varchar(100) NOT NULL,
    state        varchar(100) NOT NULL,
    pincode      varchar(6)   NOT NULL,
    active       boolean      NOT NULL DEFAULT true,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    CONSTRAINT warehouses_pincode_format CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    CONSTRAINT warehouses_id_business_key UNIQUE (id, business_id),
    CONSTRAINT warehouses_business_name_key UNIQUE (business_id, name)
);

-- Stock of one product variant at one warehouse, with the dates printed on the pack.
CREATE TABLE inventory_batches (
    id              uuid PRIMARY KEY,
    business_id     uuid        NOT NULL REFERENCES businesses (id),
    variant_id      uuid        NOT NULL REFERENCES product_variants (id),
    warehouse_id    uuid        NOT NULL,
    batch_number    varchar(50) NOT NULL,
    manufactured_on date,
    expires_on      date        NOT NULL,
    date_type       varchar(20) NOT NULL,
    quantity        bigint      NOT NULL,
    quantity_unit   varchar(10) NOT NULL,
    packs_per_case  integer,
    mrp_paise       bigint, -- Maximum retail price printed on one pack, in paise.
    status          varchar(20) NOT NULL DEFAULT 'DRAFT',
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT inventory_batches_date_type_check CHECK (date_type IN ('EXPIRY', 'USE_BY', 'BEST_BEFORE')),
    CONSTRAINT inventory_batches_dates_in_order CHECK (manufactured_on IS NULL OR manufactured_on <= expires_on),
    CONSTRAINT inventory_batches_quantity_not_negative CHECK (quantity >= 0),
    CONSTRAINT inventory_batches_quantity_unit_check CHECK (quantity_unit IN ('PIECE', 'CASE')),
    CONSTRAINT inventory_batches_case_size CHECK (
        (quantity_unit = 'CASE' AND packs_per_case > 0) OR (quantity_unit = 'PIECE' AND packs_per_case IS NULL)),
    CONSTRAINT inventory_batches_mrp_positive CHECK (mrp_paise IS NULL OR mrp_paise > 0),
    CONSTRAINT inventory_batches_status_check CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT inventory_batches_warehouse_fk FOREIGN KEY (warehouse_id, business_id)
        REFERENCES warehouses (id, business_id),
    CONSTRAINT inventory_batches_id_business_key UNIQUE (id, business_id),
    CONSTRAINT inventory_batches_batch_number_key UNIQUE (business_id, variant_id, batch_number)
);

CREATE INDEX inventory_batches_business_expires_idx ON inventory_batches (business_id, expires_on);

-- Approved dates are never changed silently: changing them must send the batch back to review
-- (the service also records an audit event). Approval itself arrives in Phase 5.
CREATE FUNCTION inventory_batches_guard_approved_dates() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.status = 'APPROVED'
        AND (NEW.expires_on, NEW.manufactured_on, NEW.date_type, NEW.batch_number, NEW.variant_id)
            IS DISTINCT FROM (OLD.expires_on, OLD.manufactured_on, OLD.date_type, OLD.batch_number, OLD.variant_id)
        AND NEW.status <> 'PENDING_REVIEW' THEN
        RAISE EXCEPTION 'approved batch % cannot change its dates without re-review', OLD.id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER inventory_batches_approved_dates
    BEFORE UPDATE ON inventory_batches
    FOR EACH ROW EXECUTE FUNCTION inventory_batches_guard_approved_dates();

CREATE TABLE listings (
    id               uuid PRIMARY KEY,
    business_id      uuid        NOT NULL REFERENCES businesses (id),
    batch_id         uuid        NOT NULL,
    status           varchar(20) NOT NULL DEFAULT 'DRAFT',
    clearance_reason varchar(30) NOT NULL,
    visibility       varchar(20) NOT NULL DEFAULT 'PUBLIC',
    -- Minimum order quantity, in the batch's unit (copied here so the quantity carries its unit).
    moq              bigint      NOT NULL,
    quantity_unit    varchar(10) NOT NULL,
    gst_rate_bp      integer     NOT NULL, -- GST rate in basis points: 1800 = 18%.
    notes            varchar(1000),
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    CONSTRAINT listings_status_check CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'PAUSED', 'CLOSED')),
    CONSTRAINT listings_clearance_reason_check CHECK (clearance_reason IN
        ('NEAR_EXPIRY', 'OVERSTOCK', 'DISCONTINUED', 'PACKAGING_CHANGE', 'SEASON_END', 'OTHER')),
    CONSTRAINT listings_visibility_check CHECK (visibility IN ('PUBLIC', 'CONFIDENTIAL')),
    CONSTRAINT listings_moq_positive CHECK (moq > 0),
    CONSTRAINT listings_quantity_unit_check CHECK (quantity_unit IN ('PIECE', 'CASE')),
    CONSTRAINT listings_gst_rate_range CHECK (gst_rate_bp BETWEEN 0 AND 4000),
    CONSTRAINT listings_batch_fk FOREIGN KEY (batch_id, business_id)
        REFERENCES inventory_batches (id, business_id),
    CONSTRAINT listings_id_business_key UNIQUE (id, business_id)
);

-- At most one open listing per batch.
CREATE UNIQUE INDEX listings_open_batch_key ON listings (batch_id) WHERE status <> 'CLOSED';
CREATE INDEX listings_business_id_idx ON listings (business_id, created_at);

-- Quantity-based prices. Prices exclude GST; the listing carries the GST rate.
CREATE TABLE price_tiers (
    id               uuid PRIMARY KEY,
    listing_id       uuid        NOT NULL,
    business_id      uuid        NOT NULL,
    min_quantity     bigint      NOT NULL,
    unit_price_paise bigint      NOT NULL,
    created_at       timestamptz NOT NULL,
    CONSTRAINT price_tiers_min_quantity_positive CHECK (min_quantity > 0),
    CONSTRAINT price_tiers_price_positive CHECK (unit_price_paise > 0),
    CONSTRAINT price_tiers_listing_fk FOREIGN KEY (listing_id, business_id)
        REFERENCES listings (id, business_id) ON DELETE CASCADE,
    CONSTRAINT price_tiers_listing_quantity_key UNIQUE (listing_id, min_quantity)
);
