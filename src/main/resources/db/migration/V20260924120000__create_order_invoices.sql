-- Phase 7 A6: GST tax invoices for delivered orders.
-- One row per order, written once when the invoice is first issued. The supplier fields are a
-- snapshot: an invoice must not change when the restaurant later edits its details.
CREATE SEQUENCE IF NOT EXISTS order_invoice_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS order_invoices (
    order_id            UUID PRIMARY KEY REFERENCES orders(id),
    -- GST rule 46: at most 16 characters, unique within the financial year.
    invoice_number      VARCHAR(16)  NOT NULL UNIQUE,
    issued_at           TIMESTAMP    NOT NULL,
    supplier_legal_name VARCHAR(255),
    supplier_trade_name VARCHAR(255),
    supplier_gstin      VARCHAR(15),
    supplier_fssai      VARCHAR(20)
);
