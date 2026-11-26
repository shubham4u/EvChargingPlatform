-- Database-level guards behind the application's checks. Two requests racing for the same connector
-- or car both pass the "is it free?" check; these make exactly one of the inserts succeed.

-- At most one open (REQUESTED or ACTIVE) session per connector, and per vehicle.
CREATE UNIQUE INDEX ux_charging_sessions_open_connector ON charging_sessions(connector_id)
    WHERE status IN ('REQUESTED', 'ACTIVE');
CREATE UNIQUE INDEX ux_charging_sessions_open_vehicle ON charging_sessions(vehicle_id)
    WHERE status IN ('REQUESTED', 'ACTIVE');

-- No two open reservations on one connector with overlapping [start_time, expiration_time) windows.
-- btree_gist lets the exclusion constraint compare the UUID column with "=" next to the range "&&";
-- it is a trusted extension, so the database owner can create it.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE reservations ADD CONSTRAINT ex_reservations_charger_window
    EXCLUDE USING gist (charger_id WITH =, tstzrange(start_time, expiration_time, '[)') WITH &&)
    WHERE (status IN ('CREATED', 'ACTIVE'));
