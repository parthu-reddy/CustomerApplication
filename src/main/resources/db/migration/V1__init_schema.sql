-- Source: V1__init_schema.sql
CREATE EXTENSION IF NOT EXISTS postgis; 

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    restaurant_id UUID NOT NULL,
    delivery_executive_id UUID,
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    version INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_restaurant_id ON orders(restaurant_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'UNPROCESSED',
    processed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_outbox_status_polling ON outbox_events(status, created_at) WHERE status IN ('UNPROCESSED', 'FAILED');

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    menu_item_id UUID NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);

CREATE TABLE ledgers (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    transaction_ref VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    balance_after DECIMAL(10, 2) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ledgers_account_id_created ON ledgers(account_id, created_at DESC);

CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    internal_order_id UUID NOT NULL REFERENCES orders(id),
    gateway_order_id VARCHAR(255),
    amount DECIMAL(15,2) NOT NULL,
    refunded_amount DECIMAL(15,2) DEFAULT 0.00,
    status VARCHAR(50) DEFAULT 'INITIATED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_positive_amount CHECK (amount > 0),
    CONSTRAINT chk_refund_limits CHECK (refunded_amount <= amount)
);
CREATE INDEX idx_payment_intents_internal_order_id ON payment_intents(internal_order_id);
CREATE INDEX IF NOT EXISTS idx_payment_intents_status_created_at ON payment_intents(status, created_at);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_intent_id UUID NOT NULL REFERENCES payment_intents(id),
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PROCESSED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_refund_positive CHECK (amount > 0)
);
CREATE INDEX idx_refunds_payment_intent_id ON refunds(payment_intent_id);

CREATE OR REPLACE FUNCTION update_refunded_amount()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE payment_intents
        SET refunded_amount = COALESCE(refunded_amount, 0) + NEW.amount
        WHERE id = NEW.payment_intent_id;
    ELSIF TG_OP = 'UPDATE' THEN
        UPDATE payment_intents
        SET refunded_amount = COALESCE(refunded_amount, 0) - OLD.amount + NEW.amount
        WHERE id = NEW.payment_intent_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE payment_intents
        SET refunded_amount = COALESCE(refunded_amount, 0) - OLD.amount
        WHERE id = OLD.payment_intent_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_refunds_update_payment_intents
AFTER INSERT OR UPDATE OR DELETE ON refunds
FOR EACH ROW EXECUTE FUNCTION update_refunded_amount();

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(50) NOT NULL,
    owner_id UUID NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    lock_version INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX idx_ledger_accounts_owner ON ledger_accounts(owner_id, owner_type);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    account_id UUID REFERENCES ledger_accounts(id),
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_id ON ledger_entries(account_id);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    masked_payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


-- Source: V2__add_estimated_prep_time_to_orders.sql
ALTER TABLE orders ADD COLUMN estimated_prep_time_minutes INTEGER DEFAULT 15;


-- Source: V3__add_cancellation_reason_to_orders.sql
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(255);


-- Source: V4__add_gateway_name_to_payment_intents.sql
ALTER TABLE payment_intents ADD COLUMN gateway_name VARCHAR(100);


-- Source: V5__add_customer_address.sql
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


-- Source: V6__add_delivery_address_text.sql
ALTER TABLE orders ADD COLUMN delivery_address_text VARCHAR(1000);


-- Source: V7__add_ledger_account_constraint.sql
-- Remove duplicates keeping one
DELETE FROM ledger_accounts a USING (
    SELECT id,
           ROW_NUMBER() OVER(PARTITION BY owner_id, owner_type ORDER BY id) as rn
    FROM ledger_accounts
) b
WHERE a.id = b.id AND b.rn > 1;

ALTER TABLE ledger_accounts
ADD CONSTRAINT uk_ledger_account_owner UNIQUE (owner_id, owner_type);


-- Source: V10__add_retry_count_to_outbox.sql
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS retry_count INT DEFAULT 0;


