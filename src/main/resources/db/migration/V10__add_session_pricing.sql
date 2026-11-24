-- Sprint 9: the tariff a session is billed at (locked when it starts) and its final cost.
ALTER TABLE charging_sessions ADD COLUMN tariff_id UUID;
ALTER TABLE charging_sessions ADD COLUMN total_cost NUMERIC(12, 2);
ALTER TABLE charging_sessions ADD COLUMN currency VARCHAR(3);
