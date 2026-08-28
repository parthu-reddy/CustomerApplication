CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    label VARCHAR(50),
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude >= -90 AND latitude <= 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude >= -180 AND longitude <= 180),
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    restaurant_id UUID NOT NULL,
    restaurant_name VARCHAR(255),
    delivery_executive_id UUID,
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL CHECK (total_amount >= 0),
    estimated_prep_time_minutes INTEGER DEFAULT 15 CHECK (estimated_prep_time_minutes >= 0),
    cancellation_reason VARCHAR(255),
    delivery_lat DOUBLE PRECISION,
    delivery_lng DOUBLE PRECISION,
    delivery_address_id UUID REFERENCES customer_addresses(id),
    delivery_address_text VARCHAR(1000),
    version INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    pickup_otp VARCHAR(255),
    estimated_completion_time BIGINT CHECK (estimated_completion_time >= 0),
    otp VARCHAR(255),
    delivery_status VARCHAR(50),
    payment_status VARCHAR(50),
    distance_km DECIMAL(10,2),
    refunded_amount NUMERIC(10, 2) DEFAULT 0.00,
    customer_name VARCHAR(255),
    delivered_at TIMESTAMP,
    item_total DECIMAL(10,2),
    customer_platform_fee DECIMAL(10,2),
    restaurant_platform_fee DECIMAL(10,2),
    platform_bonus DECIMAL(10,2),
    restaurant_delivery_contribution DECIMAL(10,2),
    restaurant_payout DECIMAL(10,2),
    sgst DECIMAL(10,2),
    cgst DECIMAL(10,2),
    delivery_fee DECIMAL(10,2),
    driver_gross_payout DECIMAL(10,2),
    driver_taxes DECIMAL(10,2),
    driver_net_payout DECIMAL(10,2)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    menu_item_id UUID NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    price DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    name VARCHAR(255),
    refunded_quantity INT DEFAULT 0
);

CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    internal_order_id UUID NOT NULL REFERENCES orders(id),
    gateway_order_id VARCHAR(255),
    gateway_name VARCHAR(100),
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    refunded_amount DECIMAL(15,2) DEFAULT 0.00 CHECK (refunded_amount <= amount),
    status VARCHAR(50) DEFAULT 'INITIATED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- When the row last changed state. RefundRetrySweeper needs this to find intents STUCK in
    -- REFUND_PENDING: created_at is when the payment began, which for an old order refunded today
    -- would look stale and cause a healthy in-flight refund to be retried.
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0
);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_intent_id UUID NOT NULL REFERENCES payment_intents(id),
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(50) DEFAULT 'PROCESSED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(50) NOT NULL,
    owner_id UUID NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    lock_version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ledger_account_owner UNIQUE (owner_id, owner_type)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    account_id UUID REFERENCES ledger_accounts(id),
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ledgers (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    transaction_ref VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL CHECK (amount >= 0),
    balance_after DECIMAL(10, 2) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    masked_payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_charges (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    category VARCHAR(50) NOT NULL,
    payer_type VARCHAR(50) NOT NULL,
    payer_id UUID,
    payee_type VARCHAR(50) NOT NULL,
    payee_id UUID,
    amount DECIMAL(10,2) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT fk_order_charges_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE processed_events (
    event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);




















CREATE UNIQUE INDEX idx_ledger_accounts_owner ON ledger_accounts(owner_id, owner_type);





















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

-- Support tickets for customer refund requests on delivered orders
CREATE TABLE support_tickets (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    status VARCHAR(50) DEFAULT 'OPEN' NOT NULL,
    resolution_notes VARCHAR(2000),
    resolved_by UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    chat_session_id UUID,
    requested_refund_items TEXT,
    refund_amount DECIMAL(10, 2),
    restaurant_comments VARCHAR(2000),
    rider_comments VARCHAR(2000),
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_support_ticket_order FOREIGN KEY (order_id) REFERENCES orders(id)
);








































CREATE INDEX idx_customer_addresses_customer_id ON customer_addresses(customer_id);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);

CREATE INDEX idx_orders_restaurant_id ON orders(restaurant_id);

CREATE INDEX idx_orders_status ON orders(status);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

CREATE INDEX idx_order_items_menu_item_id ON order_items(menu_item_id);

CREATE INDEX idx_payment_intents_internal_order_id ON payment_intents(internal_order_id);

CREATE INDEX idx_payment_intents_status_created_at ON payment_intents(status, created_at);

CREATE INDEX idx_refunds_payment_intent_id ON refunds(payment_intent_id);

CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries(transaction_id);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);

CREATE INDEX idx_ledgers_account_id_created ON ledgers(account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_orders_customer_status_created ON orders(customer_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_orders_restaurant_id ON orders(restaurant_id);

CREATE INDEX idx_support_ticket_order ON support_tickets(order_id);

CREATE INDEX idx_support_ticket_customer ON support_tickets(customer_id);

CREATE INDEX idx_support_ticket_status ON support_tickets(status);

CREATE INDEX IF NOT EXISTS idx_order_delivery_status ON orders(delivery_status);

CREATE INDEX IF NOT EXISTS idx_order_composite_del_exec ON orders(delivery_executive_id, delivery_status);