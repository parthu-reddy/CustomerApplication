-- Phase 7 A5: an arrival estimate for the customer. Both nullable -- orders placed before this,
-- or with no route from the maps service, simply have no estimate.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_travel_seconds INTEGER;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS handed_over_at BIGINT;
