package com.fooddelivery.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.enums.OrderStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedRefundDto {
    private UUID refundId;
    private UUID orderId;
    private BigDecimal amount;
    private RefundStatus status;
    private String errorMessage;
    private OffsetDateTime createdAt;
    
    // Additional order context
    private UUID customerName; // the controller maps customerId to customerName
    private UUID restaurantId;
    private OrderStatus orderStatus;
    private BigDecimal totalAmount;
}
