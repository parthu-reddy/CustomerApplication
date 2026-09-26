package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;

@Data
public class AdminOrderMoney {
    private UUID orderId;
    private BigDecimal totalAmount;
    private BigDecimal foodCost;
    private BigDecimal deliveryFee;
    private BigDecimal customerPlatformFee;
    
    private BigDecimal restaurantPayout;
    private BigDecimal restaurantPlatformFee;
    private BigDecimal restaurantDeliveryContribution;

    private BigDecimal driverGrossPayout;
    private BigDecimal driverTaxes;
    private BigDecimal driverNetPayout;
    private BigDecimal platformBonus;

    private BigDecimal sgst;
    private BigDecimal cgst;

    /**
     * How it was paid, and where the money is held. Without these an administrator cannot tell a
     * card capture from cash the rider collected, which is what decides how a refund is routed.
     */
    private com.fooddelivery.common.enums.PaymentMethod paymentMethod;
    private com.fooddelivery.common.constants.PaymentIntentStatus paymentStatus;
    private com.fooddelivery.common.enums.PaymentGateway gatewayName;
    private String gatewayOrderId;

    private java.util.List<RefundLine> refunds;

    private java.util.List<LedgerStatementLineDto> ledgerLines;

    /** One refund on this order, as the admin needs to see it. */
    @Data
    @lombok.Builder
    public static class RefundLine {
        private UUID id;
        private BigDecimal amount;
        private String status;
        private String destination;
        private String faultType;
        private String reasonCode;
        private String gatewayRefundId;
        private String failureReason;
        private java.time.Instant requestedAt;
        private java.time.Instant completedAt;
    }
}
