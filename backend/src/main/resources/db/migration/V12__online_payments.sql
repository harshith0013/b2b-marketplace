-- Phase 9b: online payment through Razorpay (Route). The buyer pays the platform; the seller's
-- share is transferred to the seller's Razorpay linked account on hold and released when the
-- order is delivered or picked up. Money in paise. Only provider ids are stored: never card or
-- UPI details, and only the last four digits of a seller's bank account.

ALTER TABLE listings
    ADD COLUMN online_accepted boolean NOT NULL DEFAULT false;

ALTER TABLE orders DROP CONSTRAINT orders_payment_method_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_method_check
    CHECK (payment_method IN ('COD', 'PAY_ON_PICKUP', 'ONLINE'));
ALTER TABLE orders DROP CONSTRAINT orders_payment_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check
    CHECK (payment_status IN ('PENDING', 'PAID', 'REFUNDED'));

CREATE INDEX orders_online_unpaid_idx ON orders (placed_at)
    WHERE payment_method = 'ONLINE' AND payment_status = 'PENDING' AND status = 'PLACED';

-- A seller's Razorpay linked account. Each onboarding step's id is kept so a retry resumes.
CREATE TABLE payout_accounts (
    business_id         uuid PRIMARY KEY REFERENCES businesses (id),
    razorpay_account_id varchar(40),
    stakeholder_id      varchar(40),
    product_id          varchar(40),
    status              varchar(30)  NOT NULL DEFAULT 'NOT_STARTED',
    bank_last4          varchar(4)   NOT NULL,
    ifsc_code           varchar(11)  NOT NULL,
    beneficiary_name    varchar(120) NOT NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    CONSTRAINT payout_accounts_status_check CHECK (status IN ('NOT_STARTED', 'CREATED',
        'REQUESTED', 'NEEDS_CLARIFICATION', 'UNDER_REVIEW', 'ACTIVATED', 'SUSPENDED', 'FAILED')),
    CONSTRAINT payout_accounts_ifsc_format CHECK (ifsc_code ~ '^[A-Z]{4}0[A-Z0-9]{6}$')
);

-- One online payment per order.
CREATE TABLE payments (
    id                  uuid PRIMARY KEY,
    order_id            uuid        NOT NULL UNIQUE REFERENCES orders (id),
    buyer_business_id   uuid        NOT NULL REFERENCES businesses (id),
    seller_business_id  uuid        NOT NULL REFERENCES businesses (id),
    amount_paise        bigint      NOT NULL,
    platform_fee_paise  bigint      NOT NULL,
    razorpay_order_id   varchar(40) NOT NULL UNIQUE,
    razorpay_payment_id varchar(40) UNIQUE,
    status              varchar(20) NOT NULL DEFAULT 'CREATED',
    transfer_id         varchar(40),
    transfer_status     varchar(20) NOT NULL DEFAULT 'NONE',
    refund_id           varchar(40),
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    captured_at         timestamptz,
    CONSTRAINT payments_amount_positive CHECK (amount_paise > 0),
    CONSTRAINT payments_fee_in_range CHECK (platform_fee_paise >= 0 AND platform_fee_paise < amount_paise),
    CONSTRAINT payments_status_check CHECK (status IN ('CREATED', 'CAPTURED', 'REFUNDED')),
    CONSTRAINT payments_transfer_status_check
        CHECK (transfer_status IN ('NONE', 'HELD', 'RELEASED', 'REVERSED')),
    CONSTRAINT payments_captured_has_id CHECK (status = 'CREATED' OR razorpay_payment_id IS NOT NULL)
);

CREATE INDEX payments_open_idx ON payments (updated_at)
    WHERE status = 'CREATED' OR (status = 'CAPTURED' AND transfer_status <> 'RELEASED');

-- Webhook deliveries already handled (Razorpay may send an event more than once).
CREATE TABLE payment_events (
    event_id    varchar(64) PRIMARY KEY,
    event_type  varchar(64) NOT NULL,
    received_at timestamptz NOT NULL
);

-- --- Row-Level Security ---------------------------------------------------------------------
-- Businesses (marketplace_tenant) read their own payout account and the payments of their own
-- orders, as buyer or seller. Nothing is written as a tenant: see the payments role below.

GRANT SELECT ON payout_accounts, payments TO marketplace_tenant;

ALTER TABLE payout_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE payout_accounts FORCE ROW LEVEL SECURITY;
CREATE POLICY payout_accounts_owner ON payout_accounts FOR SELECT TO marketplace_tenant
    USING (business_id = app_current_business_id());

ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments FORCE ROW LEVEL SECURITY;
CREATE POLICY payments_parties ON payments FOR SELECT TO marketplace_tenant
    USING (buyer_business_id = app_current_business_id()
        OR seller_business_id = app_current_business_id());

ALTER TABLE payment_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_events FORCE ROW LEVEL SECURITY;

-- Payments (marketplace_payments): the payment service, its webhook and its reconciliation job.
-- Writes the payment tables; on orders it sees only online orders and may change only their
-- payment status. Callers check the person's membership before entering this role.
DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'marketplace_payments') THEN
        CREATE ROLE marketplace_payments NOLOGIN;
    END IF;
END;
$$;

GRANT marketplace_payments TO CURRENT_USER;
GRANT USAGE ON SCHEMA public TO marketplace_payments;
GRANT SELECT, INSERT, UPDATE ON payout_accounts, payments TO marketplace_payments;
GRANT SELECT, INSERT ON payment_events TO marketplace_payments;
GRANT SELECT (id, buyer_business_id, seller_business_id, status, payment_method, payment_status,
    total_paise, placed_at) ON orders TO marketplace_payments;
GRANT UPDATE (payment_status, updated_at) ON orders TO marketplace_payments;
GRANT INSERT ON audit_events TO marketplace_payments;

CREATE POLICY audit_events_payments_insert ON audit_events FOR INSERT TO marketplace_payments
    WITH CHECK (true);
CREATE POLICY payout_accounts_payments ON payout_accounts TO marketplace_payments
    USING (true) WITH CHECK (true);
CREATE POLICY payments_payments ON payments TO marketplace_payments
    USING (true) WITH CHECK (true);
CREATE POLICY payment_events_payments ON payment_events TO marketplace_payments
    USING (true) WITH CHECK (true);
CREATE POLICY orders_payments ON orders TO marketplace_payments
    USING (payment_method = 'ONLINE') WITH CHECK (payment_method = 'ONLINE');

-- The ordering role checks at checkout that the seller can be paid online.
GRANT SELECT (business_id, status) ON payout_accounts TO marketplace_ordering;
CREATE POLICY payout_accounts_ordering_read ON payout_accounts FOR SELECT TO marketplace_ordering
    USING (true);
