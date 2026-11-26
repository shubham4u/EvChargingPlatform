-- H2 (tests only) equivalent of db/vendor/postgresql/V14: H2 has no partial indexes, so a generated
-- column holds the id only while the session is open; NULLs don't collide in a unique index.
-- Reservations' exclusion constraint has no H2 equivalent; the application check covers tests.
ALTER TABLE charging_sessions ADD COLUMN open_connector_id UUID
    GENERATED ALWAYS AS (CASE WHEN status IN ('REQUESTED', 'ACTIVE') THEN connector_id END);
ALTER TABLE charging_sessions ADD COLUMN open_vehicle_id UUID
    GENERATED ALWAYS AS (CASE WHEN status IN ('REQUESTED', 'ACTIVE') THEN vehicle_id END);
CREATE UNIQUE INDEX ux_charging_sessions_open_connector ON charging_sessions(open_connector_id);
CREATE UNIQUE INDEX ux_charging_sessions_open_vehicle ON charging_sessions(open_vehicle_id);
