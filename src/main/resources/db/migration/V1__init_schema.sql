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
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(50),
    distance_km DECIMAL(10,2),
    cash_collected_amount DECIMAL(10, 2),

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
    driver_net_payout DECIMAL(10,2),
    -- The quote this order was priced from, and the rates that were in force when it was issued.
    -- Pricing config is @RefreshScope, so without this snapshot an order cannot be repriced or
    -- audited after the live rates move.
    quote_id UUID,
    rate_base_price DECIMAL(10,2),
    rate_per_km DECIMAL(10,2),
    rate_rest_max_contribution_percent DECIMAL(6,4),
    rate_fixed_platform_fee DECIMAL(10,2),
    rate_platform_excess_cut_percent DECIMAL(6,4),
    rate_sgst_percent DECIMAL(6,4),
    rate_cgst_percent DECIMAL(6,4),
    rate_delivery_sgst_percent DECIMAL(6,4),
    rate_delivery_cgst_percent DECIMAL(6,4)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    menu_item_id UUID NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    price DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    name VARCHAR(255)
);

CREATE TABLE order_quotes (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    restaurant_id UUID NOT NULL,
    delivery_address_id UUID NOT NULL REFERENCES customer_addresses(id),
    -- pricing inputs, replayed at checkout
    item_total DECIMAL(10,2) NOT NULL CHECK (item_total >= 0),
    distance_km DECIMAL(10,2) NOT NULL CHECK (distance_km >= 0),
    -- output, kept only to assert the replay reproduces it
    quoted_customer_total DECIMAL(10,2) NOT NULL CHECK (quoted_customer_total >= 0),
    rate_base_price DECIMAL(10,2) NOT NULL,
    rate_per_km DECIMAL(10,2) NOT NULL,
    rate_rest_max_contribution_percent DECIMAL(6,4) NOT NULL,
    rate_fixed_platform_fee DECIMAL(10,2) NOT NULL,
    rate_platform_excess_cut_percent DECIMAL(6,4) NOT NULL,
    rate_sgst_percent DECIMAL(6,4) NOT NULL,
    rate_cgst_percent DECIMAL(6,4) NOT NULL,
    rate_delivery_sgst_percent DECIMAL(6,4) NOT NULL,
    rate_delivery_cgst_percent DECIMAL(6,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    consumed_order_id UUID
);

CREATE INDEX idx_order_quotes_customer ON order_quotes(customer_id);
CREATE INDEX idx_order_quotes_expiry ON order_quotes(expires_at) WHERE consumed_at IS NULL;

CREATE TABLE order_quote_items (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL REFERENCES order_quotes(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL,
    name VARCHAR(255),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10,2) NOT NULL CHECK (unit_price >= 0),
    prep_time_minutes INT
);

CREATE INDEX idx_order_quote_items_quote ON order_quote_items(quote_id);

CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    internal_order_id UUID NOT NULL REFERENCES orders(id),
    gateway_order_id VARCHAR(255),
    gateway_name VARCHAR(100),
    payment_method VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(50) DEFAULT 'INITIATED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- When the row last changed state. RefundRetrySweeper needs this to find intents STUCK in
    -- REFUND_PENDING: created_at is when the payment began, which for an old order refunded today
    -- would look stale and cause a healthy in-flight refund to be retried.
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    payment_intent_id UUID NOT NULL REFERENCES payment_intents(id),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    reason_code VARCHAR(40) NOT NULL,
    reason_text VARCHAR(2000),
    fault_type VARCHAR(32) NOT NULL,
    destination VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    initiated_by_type VARCHAR(16) NOT NULL,
    initiated_by_id UUID,
    status VARCHAR(16) NOT NULL,
    gateway_refund_id VARCHAR(255),
    failure_reason VARCHAR(1000),
    ticket_id UUID,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    ledger_transaction_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    attempts INT NOT NULL DEFAULT 0
);

CREATE TABLE refund_items (
    id UUID PRIMARY KEY,
    refund_id UUID NOT NULL REFERENCES refunds(id),
    order_item_id UUID NOT NULL REFERENCES order_items(id),
    quantity INTEGER NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
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




CREATE INDEX IF NOT EXISTS idx_orders_customer_status_created ON orders(customer_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_orders_restaurant_id ON orders(restaurant_id);

CREATE INDEX idx_support_ticket_order ON support_tickets(order_id);

CREATE INDEX idx_support_ticket_customer ON support_tickets(customer_id);

CREATE INDEX idx_support_ticket_status ON support_tickets(status);

CREATE INDEX IF NOT EXISTS idx_order_delivery_status ON orders(delivery_status);

CREATE INDEX IF NOT EXISTS idx_order_composite_del_exec ON orders(delivery_executive_id, delivery_status);