CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30),
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
