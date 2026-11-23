CREATE TABLE connector_occupancy (
    connector_id UUID PRIMARY KEY,
    station_id UUID NOT NULL,
    session_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_connector_occupancy_station ON connector_occupancy(station_id);

-- Station search: bounding-box prefilter on active stations, and per-connector hold lookups.
CREATE INDEX idx_stations_status_lat_lng ON stations(status, latitude, longitude);
CREATE INDEX idx_cr_projection_connector_status ON connector_reservation_projections(connector_id, status);
