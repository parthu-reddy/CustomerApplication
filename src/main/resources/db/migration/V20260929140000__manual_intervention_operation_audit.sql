-- Manual dispatch actions cross service boundaries through Kafka. Persist the latest authorised
-- operation on the order so consumers can reject a stale command after an administrator changes
-- their decision before the earlier event is consumed.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS manual_intervention_operation_id VARCHAR(192),
    ADD COLUMN IF NOT EXISTS manual_intervention_requested_driver_id UUID,
    ADD COLUMN IF NOT EXISTS manual_intervention_requested_by UUID,
    ADD COLUMN IF NOT EXISTS manual_intervention_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS manual_intervention_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS manual_intervention_failure_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS manual_intervention_failed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_orders_manual_intervention_operation
    ON orders (manual_intervention_operation_id)
    WHERE manual_intervention_operation_id IS NOT NULL;
