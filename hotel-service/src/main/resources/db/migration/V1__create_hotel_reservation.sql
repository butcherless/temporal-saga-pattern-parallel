CREATE TABLE hotel_reservation (
    id                UUID PRIMARY KEY,
    hotel_code        VARCHAR(50)  NOT NULL,
    room_type         VARCHAR(50)  NOT NULL,
    check_in_date     DATE         NOT NULL,
    check_out_date    DATE         NOT NULL,
    guest_count       INTEGER      NOT NULL,
    offer_reference   UUID         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);
