CREATE INDEX IF NOT EXISTS idx_order_delivery_status ON orders(delivery_status);
CREATE INDEX IF NOT EXISTS idx_order_composite_del_exec ON orders(delivery_executive_id, delivery_status);
