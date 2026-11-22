CREATE TABLE connectors (
    id UUID PRIMARY KEY,
    station_id UUID NOT NULL REFERENCES stations(id),
    connector_type VARCHAR(20) NOT NULL,
    max_power_kw INTEGER NOT NULL CHECK (max_power_kw > 0),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_connectors_station ON connectors(station_id);
CREATE INDEX idx_charging_sessions_vehicle ON charging_sessions(vehicle_id);
CREATE INDEX idx_reservations_charger_window ON reservations(charger_id, status, start_time, expiration_time);
