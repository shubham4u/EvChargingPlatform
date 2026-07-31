CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    reservation_number VARCHAR(40) NOT NULL UNIQUE,
    station_id UUID NOT NULL,
    charger_id UUID NOT NULL,
    user_id UUID NOT NULL,
    vehicle_id UUID NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    expiration_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_reservations_station ON reservations(station_id);
CREATE INDEX idx_reservations_charger ON reservations(charger_id);
CREATE INDEX idx_reservations_user ON reservations(user_id);
CREATE INDEX idx_reservations_status ON reservations(status);
CREATE INDEX idx_reservations_expiration ON reservations(expiration_time);
