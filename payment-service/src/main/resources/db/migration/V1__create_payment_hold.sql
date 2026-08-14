CREATE TABLE payment_hold (
    id                            UUID PRIMARY KEY,
    payment_authorization_token   VARCHAR(255) NOT NULL,
    status                        VARCHAR(20)  NOT NULL,
    created_at                    TIMESTAMPTZ  NOT NULL,
    updated_at                    TIMESTAMPTZ  NOT NULL,
    version                       BIGINT       NOT NULL DEFAULT 0
);
