CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    label VARCHAR(50),
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_customer_addresses_customer_id ON customer_addresses(customer_id);

ALTER TABLE orders ADD COLUMN delivery_lat DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN delivery_lng DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN delivery_address_id UUID REFERENCES customer_addresses(id);
