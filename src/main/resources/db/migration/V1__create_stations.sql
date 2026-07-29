CREATE TABLE stations (
    id UUID PRIMARY KEY,
    external_reference VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
