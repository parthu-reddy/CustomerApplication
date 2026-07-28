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

ALTER TABLE orders DROP COLUMN customer_delivery_fee;
ALTER TABLE orders DROP COLUMN platform_revenue_amount;
ALTER TABLE orders DROP COLUMN driver_payout_amount;
ALTER TABLE orders DROP COLUMN restaurant_payout_amount;
