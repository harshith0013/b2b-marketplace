-- Phase 8: what the AI read from label photos, every compliance rule evaluation, and the history
-- (Batch Passport) that sellers, reviewers and, in summary, buyers can see.
-- AI readings only flag differences; they never certify anything. A reviewer decides.

CREATE TABLE label_readings (
    id            uuid PRIMARY KEY,
    business_id   uuid        NOT NULL REFERENCES businesses (id),
    batch_id      uuid        NOT NULL,
    photo_id      uuid        NOT NULL,
    created_at    timestamptz NOT NULL,
    -- READ: the label could be read (fields may still be null when not printed);
    -- UNREADABLE: the photo was looked at but the label could not be read;
    -- NOT_CHECKED: the photo was not looked at (no AI key, quota, timeout, provider error).
    outcome       varchar(20) NOT NULL,
    reason        varchar(30),
    printed_date  varchar(40),
    date_type     varchar(20),
    expiry_year   integer,
    expiry_month  integer,
    expiry_day    integer,
    batch_number  varchar(50),
    mrp_paise     bigint,
    model         varchar(100),
    CONSTRAINT label_readings_outcome_check CHECK (outcome IN ('READ', 'UNREADABLE', 'NOT_CHECKED')),
    CONSTRAINT label_readings_date_type_check CHECK (date_type IS NULL OR date_type IN ('EXPIRY', 'USE_BY', 'BEST_BEFORE')),
    CONSTRAINT label_readings_month_range CHECK (expiry_month IS NULL OR expiry_month BETWEEN 1 AND 12),
    CONSTRAINT label_readings_day_range CHECK (expiry_day IS NULL OR expiry_day BETWEEN 1 AND 31),
    CONSTRAINT label_readings_batch_fk FOREIGN KEY (batch_id, business_id)
        REFERENCES inventory_batches (id, business_id)
);

CREATE INDEX label_readings_batch_idx ON label_readings (batch_id, created_at DESC);

-- Every time the rules ran: which rule set version, at which stage, and each rule's result.
CREATE TABLE compliance_checks (
    id               uuid PRIMARY KEY,
    business_id      uuid        NOT NULL REFERENCES businesses (id),
    batch_id         uuid        NOT NULL,
    listing_id       uuid,
    stage            varchar(20) NOT NULL,
    rule_set_version varchar(40) NOT NULL,
    -- [{"rule": "SHELF_LIFE", "outcome": "BLOCK", "code": "SHELF_LIFE_TOO_SHORT"}, ...]
    results          jsonb       NOT NULL,
    blocked          boolean     NOT NULL,
    flagged          boolean     NOT NULL,
    created_at       timestamptz NOT NULL,
    CONSTRAINT compliance_checks_stage_check CHECK (stage IN ('SUBMIT', 'APPROVE', 'DAILY', 'CHECKOUT', 'DISPATCH')),
    CONSTRAINT compliance_checks_batch_fk FOREIGN KEY (batch_id, business_id)
        REFERENCES inventory_batches (id, business_id)
);

CREATE INDEX compliance_checks_batch_idx ON compliance_checks (batch_id, created_at DESC);

CREATE TRIGGER compliance_checks_no_update_or_delete
    BEFORE UPDATE OR DELETE ON compliance_checks
    FOR EACH ROW EXECUTE FUNCTION append_only_reject_change();

-- A reviewer approving despite a label flag must confirm they compared photo and dates.
ALTER TABLE review_decisions ADD COLUMN label_check_confirmed boolean NOT NULL DEFAULT false;

-- Set when an approved batch's printed details change (it then goes back to review), so buyers
-- can be told the dates changed after approval.
ALTER TABLE inventory_batches ADD COLUMN details_changed_after_approval_at timestamptz;

-- Daily vision (label reading) requests, counted apart from assistant questions.
ALTER TABLE ai_usage_daily ADD COLUMN vision_requests integer NOT NULL DEFAULT 0;

-- --- Row-Level Security ---------------------------------------------------------------------

GRANT SELECT, INSERT ON label_readings, compliance_checks TO marketplace_tenant;
GRANT SELECT ON label_readings, compliance_checks TO marketplace_reviewer;
GRANT INSERT ON compliance_checks TO marketplace_reviewer;

ALTER TABLE label_readings ENABLE ROW LEVEL SECURITY;
ALTER TABLE label_readings FORCE ROW LEVEL SECURITY;
CREATE POLICY label_readings_own_business ON label_readings
    USING (business_id = app_current_business_id())
    WITH CHECK (business_id = app_current_business_id());
CREATE POLICY label_readings_reviewer_read ON label_readings FOR SELECT TO marketplace_reviewer
    USING (true);

ALTER TABLE compliance_checks ENABLE ROW LEVEL SECURITY;
ALTER TABLE compliance_checks FORCE ROW LEVEL SECURITY;
CREATE POLICY compliance_checks_own_business ON compliance_checks
    USING (business_id = app_current_business_id())
    WITH CHECK (business_id = app_current_business_id());
CREATE POLICY compliance_checks_reviewer_read ON compliance_checks FOR SELECT TO marketplace_reviewer
    USING (true);
CREATE POLICY compliance_checks_reviewer_insert ON compliance_checks FOR INSERT TO marketplace_reviewer
    WITH CHECK (true);

-- Buyers' passport summary: only the outcome and printed date of readings for batches on sale.
GRANT SELECT (batch_id, outcome, created_at, expiry_year, expiry_month, expiry_day)
    ON label_readings TO marketplace_public;
CREATE POLICY label_readings_public_read ON label_readings FOR SELECT TO marketplace_public
    USING (EXISTS (SELECT 1 FROM inventory_batches b WHERE b.id = label_readings.batch_id));

-- The audit log, for the seller's Batch Passport: sellers read their own business's events,
-- reviewers all. Not forced, so the application's own login (the table owner) keeps writing
-- events outside any business scope, as before; tenant and reviewer roles are filtered.
GRANT SELECT ON audit_events TO marketplace_tenant, marketplace_reviewer;
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_events_own_business ON audit_events FOR SELECT TO marketplace_tenant
    USING (business_id = app_current_business_id());
CREATE POLICY audit_events_tenant_insert ON audit_events FOR INSERT TO marketplace_tenant
    WITH CHECK (true);
CREATE POLICY audit_events_reviewer_read ON audit_events FOR SELECT TO marketplace_reviewer
    USING (true);
CREATE POLICY audit_events_reviewer_insert ON audit_events FOR INSERT TO marketplace_reviewer
    WITH CHECK (true);
