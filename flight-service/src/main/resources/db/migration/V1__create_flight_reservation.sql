CREATE TABLE flight_reservation (
    id                        UUID PRIMARY KEY,
    flight_number             VARCHAR(20)  NOT NULL,
    origin_airport_code       VARCHAR(10)  NOT NULL,
    destination_airport_code  VARCHAR(10)  NOT NULL,
    departure_date            DATE         NOT NULL,
    passenger_count           INTEGER      NOT NULL,
    offer_reference           UUID         NOT NULL,
    status                    VARCHAR(20)  NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0
);
