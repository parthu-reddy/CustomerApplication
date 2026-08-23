package com.fooddelivery.customer.dto;

import com.fooddelivery.common.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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


    
    public static class OrderResponseBuilder {
        
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
        
        private Long expiresAt;
        
        private Long remainingPingSeconds;
        
        private BigDecimal distanceKm;

        
        OrderResponseBuilder() {
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder customerId(final UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder restaurantId(final UUID restaurantId) {
            this.restaurantId = restaurantId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder restaurantName(final String restaurantName) {
            this.restaurantName = restaurantName;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder status(final OrderStatus status) {
            this.status = status;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder deliveryStatus(final com.fooddelivery.common.enums.DeliveryStatus deliveryStatus) {
            this.deliveryStatus = deliveryStatus;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder totalAmount(final BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        

        public OrderResponse.OrderResponseBuilder foodCost(final BigDecimal foodCost) {
            this.foodCost = foodCost;
            return this;
        }

        public OrderResponse.OrderResponseBuilder customerPlatformFee(final BigDecimal customerPlatformFee) {
            this.customerPlatformFee = customerPlatformFee;
            return this;
        }

        public OrderResponse.OrderResponseBuilder restaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
            this.restaurantPlatformFee = restaurantPlatformFee;
            return this;
        }

        public OrderResponse.OrderResponseBuilder platformBonus(final BigDecimal platformBonus) {
            this.platformBonus = platformBonus;
            return this;
        }

        public OrderResponse.OrderResponseBuilder restaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
            this.restaurantDeliveryContribution = restaurantDeliveryContribution;
            return this;
        }

        public OrderResponse.OrderResponseBuilder restaurantPayout(final BigDecimal restaurantPayout) {
            this.restaurantPayout = restaurantPayout;
            return this;
        }
        public OrderResponse.OrderResponseBuilder itemTotal(final BigDecimal itemTotal) {
            this.itemTotal = itemTotal;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder sgst(final BigDecimal sgst) {
            this.sgst = sgst;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder cgst(final BigDecimal cgst) {
            this.cgst = cgst;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
public OrderResponse.OrderResponseBuilder driverGrossPayout(final BigDecimal driverGrossPayout) {
            this.driverGrossPayout = driverGrossPayout;
            return this;
        }

        
        public OrderResponse.OrderResponseBuilder driverTaxes(final BigDecimal driverTaxes) {
            this.driverTaxes = driverTaxes;
            return this;
        }

        
        public OrderResponse.OrderResponseBuilder driverNetPayout(final BigDecimal driverNetPayout) {
            this.driverNetPayout = driverNetPayout;
            return this;
        }

        public OrderResponse.OrderResponseBuilder deliveryFee(final BigDecimal deliveryFee) {
            this.deliveryFee = deliveryFee;
        this.driverGrossPayout = driverGrossPayout;
        this.driverTaxes = driverTaxes;
        this.driverNetPayout = driverNetPayout;

            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder deliveryAddress(final String deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder deliveryLat(final Double deliveryLat) {
            this.deliveryLat = deliveryLat;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder deliveryLng(final Double deliveryLng) {
            this.deliveryLng = deliveryLng;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder items(final List<OrderItemResponse> items) {
            this.items = items;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder createdAt(final LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder updatedAt(final LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder riderId(final UUID riderId) {
            this.riderId = riderId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder paymentIntent(final String paymentIntent) {
            this.paymentIntent = paymentIntent;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder pickupOtp(final String pickupOtp) {
            this.pickupOtp = pickupOtp;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder otp(final String otp) {
            this.otp = otp;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder estimatedCompletionTime(final Long estimatedCompletionTime) {
            this.estimatedCompletionTime = estimatedCompletionTime;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @com.fasterxml.jackson.annotation.JsonProperty("expiresAt")
        
        public OrderResponse.OrderResponseBuilder expiresAt(final Long expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder remainingPingSeconds(final Long remainingPingSeconds) {
            this.remainingPingSeconds = remainingPingSeconds;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderResponse.OrderResponseBuilder distanceKm(final BigDecimal distanceKm) {
            this.distanceKm = distanceKm;
            return this;
        }

        
        public OrderResponse build() {
            return new OrderResponse(this.id, this.customerId, this.restaurantId, this.restaurantName, this.status, this.deliveryStatus, this.totalAmount, this.itemTotal, this.foodCost, this.customerPlatformFee, this.restaurantPlatformFee, this.platformBonus, this.restaurantDeliveryContribution, this.restaurantPayout, this.sgst, this.cgst, this.deliveryFee, this.driverGrossPayout, this.driverTaxes, this.driverNetPayout, this.deliveryAddress, this.deliveryLat, this.deliveryLng, this.items, this.createdAt, this.updatedAt, this.riderId, this.paymentIntent, this.pickupOtp, this.otp, this.estimatedCompletionTime, this.expiresAt, this.remainingPingSeconds, this.distanceKm);
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "OrderResponse.OrderResponseBuilder(id=" + this.id + ", customerId=" + this.customerId + ", restaurantId=" + this.restaurantId + ", restaurantName=" + this.restaurantName + ", status=" + this.status + ", deliveryStatus=" + this.deliveryStatus + ", totalAmount=" + this.totalAmount + ", itemTotal=" + this.itemTotal + ", sgst=" + this.sgst + ", cgst=" + this.cgst + ", deliveryFee=" + this.deliveryFee + ", deliveryAddress=" + this.deliveryAddress + ", deliveryLat=" + this.deliveryLat + ", deliveryLng=" + this.deliveryLng + ", items=" + this.items + ", createdAt=" + this.createdAt + ", updatedAt=" + this.updatedAt + ", riderId=" + this.riderId + ", paymentIntent=" + this.paymentIntent + ", pickupOtp=" + this.pickupOtp + ", otp=" + this.otp + ", estimatedCompletionTime=" + this.estimatedCompletionTime + ", expiresAt=" + this.expiresAt + ", remainingPingSeconds=" + this.remainingPingSeconds + ", distanceKm=" + this.distanceKm + ")";
        }
    }

    
    public static OrderResponse.OrderResponseBuilder builder() {
        return new OrderResponse.OrderResponseBuilder();
    }

    
    public UUID getId() {
        return this.id;
    }

    
    public UUID getCustomerId() {
        return this.customerId;
    }

    
    public UUID getRestaurantId() {
        return this.restaurantId;
    }

    
    public String getRestaurantName() {
        return this.restaurantName;
    }

    
    public OrderStatus getStatus() {
        return this.status;
    }

    
    public com.fooddelivery.common.enums.DeliveryStatus getDeliveryStatus() {
        return this.deliveryStatus;
    }

    
    public BigDecimal getTotalAmount() {
        return this.totalAmount;
    }

    

    public BigDecimal getFoodCost() {
        return this.foodCost;
    }

    public BigDecimal getCustomerPlatformFee() {
        return this.customerPlatformFee;
    }

    public BigDecimal getRestaurantPlatformFee() {
        return this.restaurantPlatformFee;
    }

    public BigDecimal getPlatformBonus() {
        return this.platformBonus;
    }

    public BigDecimal getRestaurantDeliveryContribution() {
        return this.restaurantDeliveryContribution;
    }

    public void setCustomerPlatformFee(final BigDecimal customerPlatformFee) {
        this.customerPlatformFee = customerPlatformFee;
    }

    public void setRestaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
        this.restaurantPlatformFee = restaurantPlatformFee;
    }

    public void setPlatformBonus(final BigDecimal platformBonus) {
        this.platformBonus = platformBonus;
    }

    public void setRestaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
        this.restaurantDeliveryContribution = restaurantDeliveryContribution;
    }

    public void setRestaurantPayout(final BigDecimal restaurantPayout) {
        this.restaurantPayout = restaurantPayout;
    }
    public BigDecimal getItemTotal() {
        return this.itemTotal;
    }

    
    public BigDecimal getSgst() {
        return this.sgst;
    }

    
    public BigDecimal getCgst() {
        return this.cgst;
    }

    
public BigDecimal getDriverGrossPayout() {
        return this.driverGrossPayout;
    }

    
    public void setDriverGrossPayout(final BigDecimal driverGrossPayout) {
        this.driverGrossPayout = driverGrossPayout;
    }

    
    public BigDecimal getDriverTaxes() {
        return this.driverTaxes;
    }

    
    public void setDriverTaxes(final BigDecimal driverTaxes) {
        this.driverTaxes = driverTaxes;
    }

    
    public BigDecimal getDriverNetPayout() {
        return this.driverNetPayout;
    }

    
    public void setDriverNetPayout(final BigDecimal driverNetPayout) {
        this.driverNetPayout = driverNetPayout;
    }

    public BigDecimal getDeliveryFee() {
        return this.deliveryFee;
    }

    
    public String getDeliveryAddress() {
        return this.deliveryAddress;
    }

    
    public Double getDeliveryLat() {
        return this.deliveryLat;
    }

    
    public Double getDeliveryLng() {
        return this.deliveryLng;
    }

    
    public List<OrderItemResponse> getItems() {
        return this.items;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public UUID getRiderId() {
        return this.riderId;
    }

    
    public String getPaymentIntent() {
        return this.paymentIntent;
    }

    
    public String getPickupOtp() {
        return this.pickupOtp;
    }

    
    public String getOtp() {
        return this.otp;
    }

    
    public Long getEstimatedCompletionTime() {
        return this.estimatedCompletionTime;
    }

    
    public Long getExpiresAt() {
        return this.expiresAt;
    }

    
    public Long getRemainingPingSeconds() {
        return this.remainingPingSeconds;
    }

    
    public BigDecimal getDistanceKm() {
        return this.distanceKm;
    }

    
    public void setId(final UUID id) {
        this.id = id;
    }

    
    public void setCustomerId(final UUID customerId) {
        this.customerId = customerId;
    }

    
    public void setRestaurantId(final UUID restaurantId) {
        this.restaurantId = restaurantId;
    }

    
    public void setRestaurantName(final String restaurantName) {
        this.restaurantName = restaurantName;
    }

    
    public void setStatus(final OrderStatus status) {
        this.status = status;
    }

    
    public void setDeliveryStatus(final com.fooddelivery.common.enums.DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    
    public void setTotalAmount(final BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    
    public void setItemTotal(final BigDecimal itemTotal) {
        this.itemTotal = itemTotal;
    }

    public void setFoodCost(final BigDecimal foodCost) {
        this.foodCost = foodCost;
    }

    
    public void setSgst(final BigDecimal sgst) {
        this.sgst = sgst;
    }

    
    public void setCgst(final BigDecimal cgst) {
        this.cgst = cgst;
    }

    
    public void setDeliveryFee(final BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
        this.driverGrossPayout = driverGrossPayout;
        this.driverTaxes = driverTaxes;
        this.driverNetPayout = driverNetPayout;

    }

    
    public void setDeliveryAddress(final String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    
    public void setDeliveryLat(final Double deliveryLat) {
        this.deliveryLat = deliveryLat;
    }

    
    public void setDeliveryLng(final Double deliveryLng) {
        this.deliveryLng = deliveryLng;
    }

    
    public void setItems(final List<OrderItemResponse> items) {
        this.items = items;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    
    public void setRiderId(final UUID riderId) {
        this.riderId = riderId;
    }

    
    public void setPaymentIntent(final String paymentIntent) {
        this.paymentIntent = paymentIntent;
    }

    
    public void setPickupOtp(final String pickupOtp) {
        this.pickupOtp = pickupOtp;
    }

    
    public void setOtp(final String otp) {
        this.otp = otp;
    }

    
    public void setEstimatedCompletionTime(final Long estimatedCompletionTime) {
        this.estimatedCompletionTime = estimatedCompletionTime;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("expiresAt")
    
    public void setExpiresAt(final Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    
    public void setRemainingPingSeconds(final Long remainingPingSeconds) {
        this.remainingPingSeconds = remainingPingSeconds;
    }

    
    public void setDistanceKm(final BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    @java.lang.Override
    
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof OrderResponse)) return false;
        final OrderResponse other = (OrderResponse) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$deliveryLat = this.getDeliveryLat();
        final java.lang.Object other$deliveryLat = other.getDeliveryLat();
        if (this$deliveryLat == null ? other$deliveryLat != null : !this$deliveryLat.equals(other$deliveryLat)) return false;
        final java.lang.Object this$deliveryLng = this.getDeliveryLng();
        final java.lang.Object other$deliveryLng = other.getDeliveryLng();
        if (this$deliveryLng == null ? other$deliveryLng != null : !this$deliveryLng.equals(other$deliveryLng)) return false;
        final java.lang.Object this$estimatedCompletionTime = this.getEstimatedCompletionTime();
        final java.lang.Object other$estimatedCompletionTime = other.getEstimatedCompletionTime();
        if (this$estimatedCompletionTime == null ? other$estimatedCompletionTime != null : !this$estimatedCompletionTime.equals(other$estimatedCompletionTime)) return false;
        final java.lang.Object this$expiresAt = this.getExpiresAt();
        final java.lang.Object other$expiresAt = other.getExpiresAt();
        if (this$expiresAt == null ? other$expiresAt != null : !this$expiresAt.equals(other$expiresAt)) return false;
        final java.lang.Object this$remainingPingSeconds = this.getRemainingPingSeconds();
        final java.lang.Object other$remainingPingSeconds = other.getRemainingPingSeconds();
        if (this$remainingPingSeconds == null ? other$remainingPingSeconds != null : !this$remainingPingSeconds.equals(other$remainingPingSeconds)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$customerId = this.getCustomerId();
        final java.lang.Object other$customerId = other.getCustomerId();
        if (this$customerId == null ? other$customerId != null : !this$customerId.equals(other$customerId)) return false;
        final java.lang.Object this$restaurantId = this.getRestaurantId();
        final java.lang.Object other$restaurantId = other.getRestaurantId();
        if (this$restaurantId == null ? other$restaurantId != null : !this$restaurantId.equals(other$restaurantId)) return false;
        final java.lang.Object this$restaurantName = this.getRestaurantName();
        final java.lang.Object other$restaurantName = other.getRestaurantName();
        if (this$restaurantName == null ? other$restaurantName != null : !this$restaurantName.equals(other$restaurantName)) return false;
        final java.lang.Object this$status = this.getStatus();
        final java.lang.Object other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
        final java.lang.Object this$deliveryStatus = this.getDeliveryStatus();
        final java.lang.Object other$deliveryStatus = other.getDeliveryStatus();
        if (this$deliveryStatus == null ? other$deliveryStatus != null : !this$deliveryStatus.equals(other$deliveryStatus)) return false;
        final java.lang.Object this$totalAmount = this.getTotalAmount();
        final java.lang.Object other$totalAmount = other.getTotalAmount();
        if (this$totalAmount == null ? other$totalAmount != null : !this$totalAmount.equals(other$totalAmount)) return false;
        final java.lang.Object this$itemTotal = this.getItemTotal();
        final java.lang.Object other$itemTotal = other.getItemTotal();
        if (this$itemTotal == null ? other$itemTotal != null : !this$itemTotal.equals(other$itemTotal)) return false;
        final java.lang.Object this$sgst = this.getSgst();
        final java.lang.Object other$sgst = other.getSgst();
        if (this$sgst == null ? other$sgst != null : !this$sgst.equals(other$sgst)) return false;
        final java.lang.Object this$cgst = this.getCgst();
        final java.lang.Object other$cgst = other.getCgst();
        if (this$cgst == null ? other$cgst != null : !this$cgst.equals(other$cgst)) return false;
        final java.lang.Object this$deliveryFee = this.getDeliveryFee();
        final java.lang.Object other$deliveryFee = other.getDeliveryFee();
        if (this$deliveryFee == null ? other$deliveryFee != null : !this$deliveryFee.equals(other$deliveryFee)) return false;
        final java.lang.Object this$deliveryAddress = this.getDeliveryAddress();
        final java.lang.Object other$deliveryAddress = other.getDeliveryAddress();
        if (this$deliveryAddress == null ? other$deliveryAddress != null : !this$deliveryAddress.equals(other$deliveryAddress)) return false;
        final java.lang.Object this$items = this.getItems();
        final java.lang.Object other$items = other.getItems();
        if (this$items == null ? other$items != null : !this$items.equals(other$items)) return false;
        final java.lang.Object this$createdAt = this.getCreatedAt();
        final java.lang.Object other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
        final java.lang.Object this$updatedAt = this.getUpdatedAt();
        final java.lang.Object other$updatedAt = other.getUpdatedAt();
        if (this$updatedAt == null ? other$updatedAt != null : !this$updatedAt.equals(other$updatedAt)) return false;
        final java.lang.Object this$riderId = this.getRiderId();
        final java.lang.Object other$riderId = other.getRiderId();
        if (this$riderId == null ? other$riderId != null : !this$riderId.equals(other$riderId)) return false;
        final java.lang.Object this$paymentIntent = this.getPaymentIntent();
        final java.lang.Object other$paymentIntent = other.getPaymentIntent();
        if (this$paymentIntent == null ? other$paymentIntent != null : !this$paymentIntent.equals(other$paymentIntent)) return false;
        final java.lang.Object this$pickupOtp = this.getPickupOtp();
        final java.lang.Object other$pickupOtp = other.getPickupOtp();
        if (this$pickupOtp == null ? other$pickupOtp != null : !this$pickupOtp.equals(other$pickupOtp)) return false;
        final java.lang.Object this$otp = this.getOtp();
        final java.lang.Object other$otp = other.getOtp();
        if (this$otp == null ? other$otp != null : !this$otp.equals(other$otp)) return false;
        final java.lang.Object this$distanceKm = this.getDistanceKm();
        final java.lang.Object other$distanceKm = other.getDistanceKm();
        if (this$distanceKm == null ? other$distanceKm != null : !this$distanceKm.equals(other$distanceKm)) return false;
        return true;
    }

    
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof OrderResponse;
    }

    @java.lang.Override
    
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $deliveryLat = this.getDeliveryLat();
        result = result * PRIME + ($deliveryLat == null ? 43 : $deliveryLat.hashCode());
        final java.lang.Object $deliveryLng = this.getDeliveryLng();
        result = result * PRIME + ($deliveryLng == null ? 43 : $deliveryLng.hashCode());
        final java.lang.Object $estimatedCompletionTime = this.getEstimatedCompletionTime();
        result = result * PRIME + ($estimatedCompletionTime == null ? 43 : $estimatedCompletionTime.hashCode());
        final java.lang.Object $expiresAt = this.getExpiresAt();
        result = result * PRIME + ($expiresAt == null ? 43 : $expiresAt.hashCode());
        final java.lang.Object $remainingPingSeconds = this.getRemainingPingSeconds();
        result = result * PRIME + ($remainingPingSeconds == null ? 43 : $remainingPingSeconds.hashCode());
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $customerId = this.getCustomerId();
        result = result * PRIME + ($customerId == null ? 43 : $customerId.hashCode());
        final java.lang.Object $restaurantId = this.getRestaurantId();
        result = result * PRIME + ($restaurantId == null ? 43 : $restaurantId.hashCode());
        final java.lang.Object $restaurantName = this.getRestaurantName();
        result = result * PRIME + ($restaurantName == null ? 43 : $restaurantName.hashCode());
        final java.lang.Object $status = this.getStatus();
        result = result * PRIME + ($status == null ? 43 : $status.hashCode());
        final java.lang.Object $deliveryStatus = this.getDeliveryStatus();
        result = result * PRIME + ($deliveryStatus == null ? 43 : $deliveryStatus.hashCode());
        final java.lang.Object $totalAmount = this.getTotalAmount();
        result = result * PRIME + ($totalAmount == null ? 43 : $totalAmount.hashCode());
        final java.lang.Object $itemTotal = this.getItemTotal();
        result = result * PRIME + ($itemTotal == null ? 43 : $itemTotal.hashCode());
        final java.lang.Object $sgst = this.getSgst();
        result = result * PRIME + ($sgst == null ? 43 : $sgst.hashCode());
        final java.lang.Object $cgst = this.getCgst();
        result = result * PRIME + ($cgst == null ? 43 : $cgst.hashCode());
        final java.lang.Object $deliveryFee = this.getDeliveryFee();
        result = result * PRIME + ($deliveryFee == null ? 43 : $deliveryFee.hashCode());
        final java.lang.Object $deliveryAddress = this.getDeliveryAddress();
        result = result * PRIME + ($deliveryAddress == null ? 43 : $deliveryAddress.hashCode());
        final java.lang.Object $items = this.getItems();
        result = result * PRIME + ($items == null ? 43 : $items.hashCode());
        final java.lang.Object $createdAt = this.getCreatedAt();
        result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
        final java.lang.Object $updatedAt = this.getUpdatedAt();
        result = result * PRIME + ($updatedAt == null ? 43 : $updatedAt.hashCode());
        final java.lang.Object $riderId = this.getRiderId();
        result = result * PRIME + ($riderId == null ? 43 : $riderId.hashCode());
        final java.lang.Object $paymentIntent = this.getPaymentIntent();
        result = result * PRIME + ($paymentIntent == null ? 43 : $paymentIntent.hashCode());
        final java.lang.Object $pickupOtp = this.getPickupOtp();
        result = result * PRIME + ($pickupOtp == null ? 43 : $pickupOtp.hashCode());
        final java.lang.Object $otp = this.getOtp();
        result = result * PRIME + ($otp == null ? 43 : $otp.hashCode());
        final java.lang.Object $distanceKm = this.getDistanceKm();
        result = result * PRIME + ($distanceKm == null ? 43 : $distanceKm.hashCode());
        return result;
    }

    @java.lang.Override
    
    public java.lang.String toString() {
        return "OrderResponse(id=" + this.getId() + ", customerId=" + this.getCustomerId() + ", restaurantId=" + this.getRestaurantId() + ", restaurantName=" + this.getRestaurantName() + ", status=" + this.getStatus() + ", deliveryStatus=" + this.getDeliveryStatus() + ", totalAmount=" + this.getTotalAmount() + ", itemTotal=" + this.getItemTotal() + ", sgst=" + this.getSgst() + ", cgst=" + this.getCgst() + ", deliveryFee=" + this.getDeliveryFee() + ", deliveryAddress=" + this.getDeliveryAddress() + ", deliveryLat=" + this.getDeliveryLat() + ", deliveryLng=" + this.getDeliveryLng() + ", items=" + this.getItems() + ", createdAt=" + this.getCreatedAt() + ", updatedAt=" + this.getUpdatedAt() + ", riderId=" + this.getRiderId() + ", paymentIntent=" + this.getPaymentIntent() + ", pickupOtp=" + this.getPickupOtp() + ", otp=" + this.getOtp() + ", estimatedCompletionTime=" + this.getEstimatedCompletionTime() + ", expiresAt=" + this.getExpiresAt() + ", remainingPingSeconds=" + this.getRemainingPingSeconds() + ", distanceKm=" + this.getDistanceKm() + ")";
    }

    
    public OrderResponse() {
    }

    
    public OrderResponse(final UUID id, final UUID customerId, final UUID restaurantId, final String restaurantName, final OrderStatus status, final com.fooddelivery.common.enums.DeliveryStatus deliveryStatus, final BigDecimal totalAmount, final BigDecimal itemTotal, final BigDecimal foodCost, final BigDecimal customerPlatformFee, final BigDecimal restaurantPlatformFee, final BigDecimal platformBonus, final BigDecimal restaurantDeliveryContribution, final BigDecimal restaurantPayout, final BigDecimal sgst, final BigDecimal cgst, final BigDecimal deliveryFee, final BigDecimal driverGrossPayout, final BigDecimal driverTaxes, final BigDecimal driverNetPayout, final String deliveryAddress, final Double deliveryLat, final Double deliveryLng, final List<OrderItemResponse> items, final LocalDateTime createdAt, final LocalDateTime updatedAt, final UUID riderId, final String paymentIntent, final String pickupOtp, final String otp, final Long estimatedCompletionTime, final Long expiresAt, final Long remainingPingSeconds, final BigDecimal distanceKm) {
        this.id = id;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.status = status;
        this.deliveryStatus = deliveryStatus;
        this.totalAmount = totalAmount;
        this.itemTotal = itemTotal;
        this.foodCost = foodCost;
        this.customerPlatformFee = customerPlatformFee;
        this.restaurantPlatformFee = restaurantPlatformFee;
        this.platformBonus = platformBonus;
        this.restaurantDeliveryContribution = restaurantDeliveryContribution;
        this.restaurantPayout = restaurantPayout;
        this.sgst = sgst;
        this.cgst = cgst;
        this.deliveryFee = deliveryFee;
        this.driverGrossPayout = driverGrossPayout;
        this.driverTaxes = driverTaxes;
        this.driverNetPayout = driverNetPayout;

        this.deliveryAddress = deliveryAddress;
        this.deliveryLat = deliveryLat;
        this.deliveryLng = deliveryLng;
        this.items = items;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.riderId = riderId;
        this.paymentIntent = paymentIntent;
        this.pickupOtp = pickupOtp;
        this.otp = otp;
        this.estimatedCompletionTime = estimatedCompletionTime;
        this.expiresAt = expiresAt;
        this.remainingPingSeconds = remainingPingSeconds;
        this.distanceKm = distanceKm;
    }
}
