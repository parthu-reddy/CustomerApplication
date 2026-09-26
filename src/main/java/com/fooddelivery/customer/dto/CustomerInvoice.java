package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A GST tax invoice for one delivered order, as the customer's "Tax invoice" screen prints it.
 *
 * <p>Every amount is the order's own, so the invoice adds up to exactly what was charged: the
 * food (the taxable value, SAC 996331 restaurant service) with CGST and SGST on it, then the
 * delivery and platform fees, which this platform's pricing does not tax. The rates are derived
 * from the amounts charged, not from today's configuration, so an old invoice cannot drift.
 */
@lombok.Data
@lombok.Builder
// Absent, not null: the UI's generated schema is .partial() (undefined allowed, null not), and
// an unconfigured operator or a supplier without an FSSAI number is simply not on the invoice.
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class CustomerInvoice {
    private String invoiceNumber;
    private Instant issuedAt;
    private UUID orderId;
    private Instant orderPlacedAt;

    private Party supplier;
    /** The e-commerce operator; null until its details are configured. */
    private Party operator;

    private String customerName;
    private String deliveryAddress;

    private List<Line> lines;
    private BigDecimal taxableValue;
    private BigDecimal cgstRatePercent;
    private BigDecimal cgstAmount;
    private BigDecimal sgstRatePercent;
    private BigDecimal sgstAmount;
    private BigDecimal deliveryFee;
    private BigDecimal platformFee;
    private BigDecimal total;
    private String paymentMethod;

    @lombok.Data
    @lombok.Builder
    public static class Party {
        private String legalName;
        private String tradeName;
        private String gstin;
        private String fssaiLicenseNumber;
        private String address;
    }

    @lombok.Data
    @lombok.Builder
    public static class Line {
        private String description;
        /** Services Accounting Code: 996331, restaurant services. */
        private String sac;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
    }
}
