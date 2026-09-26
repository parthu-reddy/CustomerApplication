package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@lombok.Data
public class CustomerReceipt {
    private List<ReceiptItem> items;
    private BigDecimal itemTotal;
    private BigDecimal deliveryFee;
    private BigDecimal platformFee;
    private BigDecimal sgst;
    private BigDecimal cgst;
    private BigDecimal total;
    private String paymentMethod;
    private Instant paidAt;
    private List<com.fooddelivery.order.refund.RefundView> refunds;
    private BigDecimal storeCreditUsed;

    @lombok.Data
    public static class ReceiptItem {
        private String name;
        private int quantity;
        private BigDecimal price;
    }
}
