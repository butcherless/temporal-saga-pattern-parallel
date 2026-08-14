CREATE TABLE travel_booking (
    id             UUID PRIMARY KEY,
    business_key   VARCHAR(255) NOT NULL UNIQUE,
    operator_id    VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0
);
