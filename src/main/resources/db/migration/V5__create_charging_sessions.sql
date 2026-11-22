CREATE TABLE charging_sessions (
    id UUID PRIMARY KEY,
    charging_session_number VARCHAR(40) NOT NULL UNIQUE,
    station_id UUID NOT NULL,
    connector_id UUID NOT NULL,
    user_id UUID NOT NULL,
    vehicle_id UUID NOT NULL,
    reservation_id UUID,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ,
    stopped_at TIMESTAMPTZ,
    total_energy_kwh NUMERIC,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_charging_sessions_station ON charging_sessions(station_id);
CREATE INDEX idx_charging_sessions_connector ON charging_sessions(connector_id);
CREATE INDEX idx_charging_sessions_user ON charging_sessions(user_id);
CREATE INDEX idx_charging_sessions_reservation ON charging_sessions(reservation_id);
CREATE INDEX idx_charging_sessions_status ON charging_sessions(status);
