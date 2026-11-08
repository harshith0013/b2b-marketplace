-- Phase 7: the search assistant. What was asked and how it went, to measure quality, and a daily
-- usage counter for the provider's free quota. No IP address, user id or business id is stored:
-- assistant questions are anonymous. Rows older than the retention period (default 90 days) are
-- deleted daily.

CREATE TABLE ai_search_requests (
    id                uuid PRIMARY KEY,
    created_at        timestamptz  NOT NULL,
    question          varchar(300) NOT NULL,
    interpretation    jsonb,
    outcome           varchar(30)  NOT NULL,
    model             varchar(100),
    latency_ms        integer,
    prompt_tokens     integer,
    completion_tokens integer,
    CONSTRAINT ai_search_requests_outcome_check CHECK (outcome IN
        ('UNDERSTOOD', 'NOT_CONFIGURED', 'VISITOR_LIMIT', 'DAILY_LIMIT', 'PROVIDER_LIMIT',
         'PROVIDER_ERROR', 'TIMEOUT', 'INVALID_ANSWER'))
);

CREATE INDEX ai_search_requests_created_at_idx ON ai_search_requests (created_at);

CREATE TABLE ai_usage_daily (
    day               date PRIMARY KEY,
    requests          integer NOT NULL DEFAULT 0,
    prompt_tokens     bigint  NOT NULL DEFAULT 0,
    completion_tokens bigint  NOT NULL DEFAULT 0
);
