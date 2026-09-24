package com.fooddelivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The tax invoice issued for one delivered order. Amounts and lines are the order's own (fixed
 * once delivered); what is stored here is what must never change after issue -- the number, the
 * date, and the supplier as it stood then.
 */
@Entity
@Table(name = "order_invoices")
@lombok.Getter
@lombok.Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderInvoice {
    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 16)
    private String invoiceNumber;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "supplier_legal_name")
    private String supplierLegalName;

    @Column(name = "supplier_trade_name")
    private String supplierTradeName;

    @Column(name = "supplier_gstin", length = 15)
    private String supplierGstin;

    @Column(name = "supplier_fssai", length = 20)
    private String supplierFssai;
}
