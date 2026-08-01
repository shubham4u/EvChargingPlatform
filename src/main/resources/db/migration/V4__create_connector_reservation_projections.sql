CREATE TABLE connector_reservation_projections (
    reservation_id UUID PRIMARY KEY,
    station_id     UUID        NOT NULL,
    connector_id   UUID        NOT NULL,
    user_id        UUID,
    status         VARCHAR(20) NOT NULL,
    expires_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_cr_projection_station_id ON connector_reservation_projections (station_id);
CREATE INDEX idx_cr_projection_connector_id ON connector_reservation_projections (connector_id);
CREATE INDEX idx_cr_projection_status ON connector_reservation_projections (status);
CREATE INDEX idx_cr_projection_station_status ON connector_reservation_projections (station_id, status);