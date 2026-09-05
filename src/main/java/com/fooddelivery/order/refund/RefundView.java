package com.fooddelivery.order.refund;

import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class RefundView {
    private UUID id;
    private UUID orderId;
    private BigDecimal amount;
    private RefundStatus status;
    private RefundDestination destination;
    private PaymentMethod method;
    private String reasonCode;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expectedBy;
}
