-- V7__add_check_constraints.sql
ALTER TABLE customer_addresses ADD CONSTRAINT chk_customer_addr_lat CHECK (latitude >= -90 AND latitude <= 90);
ALTER TABLE customer_addresses ADD CONSTRAINT chk_customer_addr_lng CHECK (longitude >= -180 AND longitude <= 180);

ALTER TABLE orders ADD CONSTRAINT chk_orders_est_prep_time CHECK (estimated_prep_time_minutes >= 0);
ALTER TABLE orders ADD CONSTRAINT chk_orders_est_comp_time CHECK (estimated_completion_time >= 0);

ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_entries_amount CHECK (amount >= 0);
ALTER TABLE ledgers ADD CONSTRAINT chk_ledgers_amount CHECK (amount >= 0);
