package com.fooddelivery.customer.dto;

import com.fooddelivery.common.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderResponse {
    @NotNull
    private UUID id;
    @NotNull
    private UUID customerId;
    @NotNull
    private UUID restaurantId;
    @NotNull
    private String restaurantName;
    @NotNull
    private OrderStatus status;
    @NotNull
    private com.fooddelivery.common.enums.DeliveryStatus deliveryStatus;
    @NotNull
    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private BigDecimal totalAmount;
    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private BigDecimal itemTotal;
    

    @NotNull
    private BigDecimal customerPlatformFee;
    @NotNull
    private BigDecimal sgst;
    @NotNull
    private BigDecimal cgst;
    @NotNull
    private BigDecimal deliveryFee;

    @NotNull
    private String deliveryAddress;
    private Double deliveryLat;
    private Double deliveryLng;
    @NotNull
    private List<OrderItemResponse> items;
    @NotNull
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID riderId;
    private UUID deliveryExecutiveId;
    private String customerName;
    private String deliveryExecutiveName;
    private String paymentIntent;
    private String pickupOtp;
    private String otp;
    private Long estimatedCompletionTime;
    @com.fasterxml.jackson.annotation.JsonProperty("expiresAt")
    private Long expiresAt;
    private Long remainingPingSeconds;
    private BigDecimal distanceKm;

    /**
     * How the order is paid for.
     *
     * <p>The customer's order view could not tell a prepaid order from a cash one, so it could not
     * say "pay in cash on delivery" -- and the UI patched the gap by declaring paymentMethod on its
     * own Order type, over a DTO that never sent it.
     */
    private com.fooddelivery.common.enums.PaymentMethod paymentMethod;

    /** Why a terminal order ended, set by the state that ended it. Null while the order is live. */
    private String cancellationReason;

}
