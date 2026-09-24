-- The restaurant's delay request: how many more minutes it asked for, and why.
-- OrderDelayApprovalRequestedEvent has always carried both; the order dropped them, so the
-- customer was asked to approve a delay without being told how long it was.
-- Nullable: only orders that went through a delay request have them.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS requested_delay_minutes INTEGER;
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS delay_reason VARCHAR(500);
