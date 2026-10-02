-- refund_items.amount was NOT NULL but no code ever wrote or read it: RefundItem maps only the
-- order item and quantity, and quantity is all ITEM_ALREADY_REFUNDED needs. Every item-level
-- refund therefore failed its insert, including every admin approval of an item support ticket.
-- A per-item amount is not even defined for a reduced support award; the refund's own amount is
-- the money. Remove the column rather than invent an allocation.
ALTER TABLE refund_items DROP COLUMN IF EXISTS amount;
