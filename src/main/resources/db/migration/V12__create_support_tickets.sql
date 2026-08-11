-- Support tickets for customer refund requests on delivered orders
CREATE TABLE support_tickets (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    order_id RAW(16) NOT NULL,
    customer_id RAW(16) NOT NULL,
    reason VARCHAR2(2000) NOT NULL,
    status VARCHAR2(50) DEFAULT 'OPEN' NOT NULL,
    resolution_notes VARCHAR2(2000),
    resolved_by RAW(16),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT fk_support_ticket_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_support_ticket_order ON support_tickets(order_id);
CREATE INDEX idx_support_ticket_customer ON support_tickets(customer_id);
CREATE INDEX idx_support_ticket_status ON support_tickets(status);
