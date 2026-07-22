package com.fooddelivery.customer.dto;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private UUID customerId;
    private UUID restaurantId;
    private String restaurantName;
    private OrderStatus status;
    private DeliveryStatus deliveryStatus;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private Double deliveryLat;
    private Double deliveryLng;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private UUID riderId;
    private String paymentIntent;
    private String pickupOtp;
    private String otp;
    private Long estimatedCompletionTime;
    private Long expiresAt;
}
