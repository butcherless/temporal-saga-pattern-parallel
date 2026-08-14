CREATE TABLE car_reservation (
    id                UUID PRIMARY KEY,
    car_category      VARCHAR(50)  NOT NULL,
    pickup_location   VARCHAR(100) NOT NULL,
    pickup_date       DATE         NOT NULL,
    dropoff_date      DATE         NOT NULL,
    offer_reference   UUID         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);
