package com.fooddelivery.customer.dto;

import com.fooddelivery.common.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private UUID customerId;
    private UUID restaurantId;
    private String restaurantName;
    private OrderStatus status;
    private com.fooddelivery.common.enums.DeliveryStatus deliveryStatus;
    private BigDecimal totalAmount;
    private BigDecimal itemTotal;
    private BigDecimal foodCost;
    private BigDecimal customerPlatformFee;
    private BigDecimal restaurantPlatformFee;
    private BigDecimal platformBonus;
    private BigDecimal restaurantDeliveryContribution;
    private BigDecimal restaurantPayout;
    private BigDecimal sgst;
    private BigDecimal cgst;
    private BigDecimal deliveryFee;

    private BigDecimal driverGrossPayout;
    private BigDecimal driverTaxes;
    private BigDecimal driverNetPayout;

    private String deliveryAddress;
    private Double deliveryLat;
    private Double deliveryLng;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID riderId;
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
        this.driverGrossPayout = driverGrossPayout;
        this.driverTaxes = driverTaxes;
        this.driverNetPayout = driverNetPayout;

    }

    

    

    

    

    

    

    

    

    

    

    


    

    

    

    

    

    

    

    
}
