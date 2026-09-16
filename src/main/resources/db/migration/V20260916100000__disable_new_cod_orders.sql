-- The application enum no longer contains cash-on-delivery states. Refuse startup instead of
-- silently relabelling financial history; operations must resolve/archive those rows explicitly.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM orders
        WHERE payment_method IS NULL OR payment_method NOT IN ('CARD', 'UPI', 'WALLET')
    ) OR EXISTS (
        SELECT 1 FROM payment_intents
        WHERE payment_method IS NULL OR payment_method NOT IN ('CARD', 'UPI', 'WALLET')
    ) THEN
        RAISE EXCEPTION 'Prepaid-only migration blocked: unsupported payment-method rows exist';
    END IF;
END $$;

ALTER TABLE orders DROP COLUMN IF EXISTS cash_collected_amount;

ALTER TABLE orders
    ALTER COLUMN payment_method SET NOT NULL,
    ADD CONSTRAINT chk_orders_payment_method_prepaid
    CHECK (payment_method IN ('CARD', 'UPI', 'WALLET'));

ALTER TABLE payment_intents
    ALTER COLUMN payment_method SET NOT NULL,
    ADD CONSTRAINT chk_payment_intents_method_prepaid
    CHECK (payment_method IN ('CARD', 'UPI', 'WALLET'));
