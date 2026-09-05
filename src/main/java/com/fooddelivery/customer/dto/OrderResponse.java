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
    @com.fasterxml.jackson.annotation.JsonProperty("total")
    private BigDecimal totalAmount;
    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("subtotal")
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


    

    

    

    

    

    

    

    

    

    











    

    

    

    

    

    

    

    


    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    


    

    

    
    public void setDeliveryFee(final BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    

    

    

    

    

    

    

    

    

    

    


    

    

    

    

    

    

    

    
}
