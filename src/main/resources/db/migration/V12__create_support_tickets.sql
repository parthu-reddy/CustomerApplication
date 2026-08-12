-- Support tickets for customer refund requests on delivered orders
CREATE TABLE support_tickets (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    status VARCHAR(50) DEFAULT 'OPEN' NOT NULL,
    resolution_notes VARCHAR(2000),
    resolved_by UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT fk_support_ticket_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_support_ticket_order ON support_tickets(order_id);
CREATE INDEX idx_support_ticket_customer ON support_tickets(customer_id);
CREATE INDEX idx_support_ticket_status ON support_tickets(status);
