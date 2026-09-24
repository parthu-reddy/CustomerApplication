-- Phase 7 A3: the customer's tip for the rider, in rupees. Part of total_amount (what was
-- charged); paid to the rider in full on delivery as its own ledger leg.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tip_amount NUMERIC(10, 2) NOT NULL DEFAULT 0;
