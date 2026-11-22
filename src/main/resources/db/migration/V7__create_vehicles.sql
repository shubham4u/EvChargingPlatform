CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    vin VARCHAR(17) NOT NULL UNIQUE,
    make VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_vehicles_owner ON vehicles(owner_user_id);
