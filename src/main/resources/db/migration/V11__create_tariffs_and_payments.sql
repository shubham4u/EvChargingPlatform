-- Pricing: insert-only tariff versions; the latest per station is current.
CREATE TABLE tariffs (
    id UUID PRIMARY KEY,
    station_id UUID NOT NULL REFERENCES stations(id),
    currency VARCHAR(3) NOT NULL,
    price_per_kwh NUMERIC(10, 4) NOT NULL CHECK (price_per_kwh >= 0),
    price_per_minute NUMERIC(10, 4) NOT NULL CHECK (price_per_minute >= 0),
    session_fee NUMERIC(10, 2) NOT NULL CHECK (session_fee >= 0),
    defined_by UUID,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_tariffs_station_created ON tariffs(station_id, created_at);

-- Payments: one saved card per user, one intent per charging session.
CREATE TABLE payment_methods (
    user_id UUID PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    last4 VARCHAR(4),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    authorized_amount NUMERIC(12, 2) NOT NULL,
    captured_amount NUMERIC(12, 2),
    card_token VARCHAR(255) NOT NULL,
    card_brand VARCHAR(255),
    card_last4 VARCHAR(4),
    provider_reference VARCHAR(255),
    failure_code VARCHAR(255),
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_payment_intents_user ON payment_intents(user_id, created_at);
CREATE INDEX idx_payment_intents_status ON payment_intents(status);
