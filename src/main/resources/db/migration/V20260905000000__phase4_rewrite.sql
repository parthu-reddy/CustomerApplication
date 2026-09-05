ALTER TABLE orders ADD COLUMN payment_method VARCHAR(50);
ALTER TABLE orders ADD COLUMN cash_collected_amount DECIMAL(10, 2);
ALTER TABLE orders DROP COLUMN refunded_amount;

ALTER TABLE order_items DROP COLUMN refunded_quantity;

ALTER TABLE payment_intents ADD COLUMN payment_method VARCHAR(50);
ALTER TABLE payment_intents ADD COLUMN gateway_name VARCHAR(50);

-- Drop old refunds table and recreate
DROP TRIGGER IF EXISTS trg_refunds_update_payment_intents ON refunds;
DROP FUNCTION IF EXISTS update_refunded_amount;

DROP TABLE refunds;

CREATE TABLE refunds (
  id uuid PRIMARY KEY, 
  order_id uuid not null references orders, 
  payment_intent_id uuid not null references payment_intents,
  amount numeric(14,2) not null check (amount > 0), 
  currency char(3) not null default 'INR',
  reason_code varchar(40) not null, 
  reason_text varchar(2000),
  fault_type varchar(32) not null,                 
  destination varchar(32) not null,                
  source varchar(32) not null,                     
  initiated_by_type varchar(16) not null, 
  initiated_by_id uuid,
  status varchar(16) not null,                     
  gateway_refund_id varchar(255), 
  failure_reason varchar(1000),
  ticket_id uuid references support_tickets, 
  idempotency_key varchar(255) not null unique,
  ledger_transaction_id uuid, 
  created_at timestamptz not null default now(), 
  updated_at timestamptz not null default now(), 
  completed_at timestamptz,
  attempts INT NOT NULL DEFAULT 0
);

CREATE TABLE refund_items (
  id uuid PRIMARY KEY, 
  refund_id uuid not null references refunds, 
  order_item_id uuid not null references order_items, 
  quantity int not null check (quantity > 0)
);

CREATE INDEX idx_refunds_payment_intent_id ON refunds(payment_intent_id);

-- Drop other old tables moved to LedgerService
DROP TABLE IF EXISTS ledgers;
DROP TABLE IF EXISTS ledger_entries;
DROP TABLE IF EXISTS ledger_accounts;
DROP TABLE IF EXISTS webhook_deliveries;
DROP TABLE IF EXISTS processed_events;
