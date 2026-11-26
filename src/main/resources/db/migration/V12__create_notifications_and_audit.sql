-- Driver inbox: one row per notified event (event_id unique, so redelivery can't notify twice).
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(255) NOT NULL,
    subject_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at);

-- Append-only copy of every domain event, as published.
CREATE TABLE audit_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    kafka_partition INTEGER NOT NULL,
    kafka_offset BIGINT NOT NULL,
    event_key VARCHAR(100),
    event_type VARCHAR(100),
    occurred_at TIMESTAMPTZ,
    trace_id VARCHAR(32),
    payload VARCHAR(8000) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_events_key ON audit_events(event_key, occurred_at);
CREATE INDEX idx_audit_events_type ON audit_events(event_type, occurred_at);
CREATE INDEX idx_audit_events_trace ON audit_events(trace_id);

-- Station reports: sessions per station over a period.
CREATE INDEX idx_charging_sessions_station_created ON charging_sessions(station_id, created_at);
