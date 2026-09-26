package com.fooddelivery.order.refund;

import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
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
    private Instant requestedAt;
    private Instant completedAt;
    private Instant expectedBy;
}
