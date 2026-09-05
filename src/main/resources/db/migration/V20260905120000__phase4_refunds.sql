ALTER TABLE orders ADD COLUMN payment_method VARCHAR(16) NOT NULL DEFAULT 'CREDIT_CARD';
ALTER TABLE orders DROP COLUMN refunded_amount;

ALTER TABLE order_items DROP COLUMN refunded_quantity;

ALTER TABLE payment_intents ALTER COLUMN gateway_name TYPE VARCHAR(32);
ALTER TABLE payment_intents ADD COLUMN payment_method VARCHAR(16) NOT NULL DEFAULT 'CREDIT_CARD';
ALTER TABLE payment_intents DROP COLUMN retry_count;

DROP TRIGGER IF EXISTS trg_refunds_update_payment_intents ON refunds;
DROP FUNCTION IF EXISTS update_refunded_amount;

DROP TABLE refunds CASCADE;

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
    quantity INT NOT NULL CHECK (quantity > 0)
);

DROP TABLE ledger_entries CASCADE;
DROP TABLE ledgers CASCADE;
DROP TABLE ledger_accounts CASCADE;
DROP TABLE webhook_deliveries CASCADE;
DROP TABLE processed_events CASCADE;
