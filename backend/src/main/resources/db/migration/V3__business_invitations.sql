-- Phase 3b: an owner invites someone into their business by email. The invitee sees the
-- invitation after signing in with that email and accepts it, which creates the membership.

CREATE TABLE business_invitations (
    id           uuid PRIMARY KEY,
    business_id  uuid         NOT NULL REFERENCES businesses (id),
    email        varchar(320) NOT NULL,
    role         varchar(20)  NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'PENDING',
    invited_by   uuid         NOT NULL REFERENCES users (id),
    created_at   timestamptz  NOT NULL,
    expires_at   timestamptz  NOT NULL,
    responded_at timestamptz,
    accepted_by  uuid REFERENCES users (id),
    CONSTRAINT business_invitations_email_lowercase CHECK (email = lower(email)),
    -- Owners are never invited: a business gets its owner when it is created.
    CONSTRAINT business_invitations_role_check CHECK (role IN ('MANAGER', 'STAFF')),
    CONSTRAINT business_invitations_status_check CHECK (status IN
        ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT business_invitations_expiry_after_creation CHECK (expires_at > created_at)
);

-- At most one open invitation per person per business.
CREATE UNIQUE INDEX business_invitations_one_pending_key
    ON business_invitations (business_id, email) WHERE status = 'PENDING';

CREATE INDEX business_invitations_pending_email_idx
    ON business_invitations (email) WHERE status = 'PENDING';
