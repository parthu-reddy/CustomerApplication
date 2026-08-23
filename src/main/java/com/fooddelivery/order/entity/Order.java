package com.fooddelivery.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;

@Entity
@Table(name = "orders", indexes = {
    @jakarta.persistence.Index(name = "idx_order_customer", columnList = "customer_id"), 
    @jakarta.persistence.Index(name = "idx_order_delivery_exec", columnList = "delivery_executive_id"), 
    @jakarta.persistence.Index(name = "idx_order_status", columnList = "status"),
    @jakarta.persistence.Index(name = "idx_order_restaurant", columnList = "restaurant_id"),
    @jakarta.persistence.Index(name = "idx_order_delivery_status", columnList = "delivery_status"),
    @jakarta.persistence.Index(name = "idx_order_composite_del_exec", columnList = "delivery_executive_id, delivery_status")
})
@lombok.extern.slf4j.Slf4j
public class Order {
    

    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "customer_name")
    private String customerName;
    @Column(name = "restaurant_id")
    private UUID restaurantId;
    @Column(name = "restaurant_name")
    private String restaurantName;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status")
    private com.fooddelivery.common.enums.DeliveryStatus deliveryStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentIntentStatus paymentStatus;

    public void setStatus(OrderStatus status) {
        if (this.status != null && status != null) {
            if (status.getSequence() < this.status.getSequence()) {
                log.error("Invalid state transition: Attempted to move order {} backward from {} to {}", this.id, this.status, status);
                throw new IllegalStateException("Cannot move order status backward from " + this.status + " to " + status);
            }
        }
        if (this.status != status) {
            log.info("Customer order {} status changing from {} to {}", this.id, this.status, status);
        }
        this.status = status;
    }

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "item_total")
    private BigDecimal itemTotal;
    @Column(name = "customer_platform_fee")
    private BigDecimal customerPlatformFee;
    @Column(name = "restaurant_platform_fee")
    private BigDecimal restaurantPlatformFee;
    @Column(name = "platform_bonus")
    private BigDecimal platformBonus;
    @Column(name = "restaurant_delivery_contribution")
    private BigDecimal restaurantDeliveryContribution;
    @Column(name = "restaurant_payout")
    private BigDecimal restaurantPayout;
    @Column(name = "sgst")
    private BigDecimal sgst;
    @Column(name = "cgst")
    private BigDecimal cgst;
    @Column(name = "delivery_fee")
    private BigDecimal deliveryFee;
    @Column(name = "driver_gross_payout")
    private BigDecimal driverGrossPayout;
    @Column(name = "driver_taxes")
    private BigDecimal driverTaxes;
    @Column(name = "driver_net_payout")
    private BigDecimal driverNetPayout;

    @Column(name = "refunded_amount")
    private BigDecimal refundedAmount;
    @Column(name = "distance_km")
    private BigDecimal distanceKm;
    @JsonIgnore
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<OrderCharge> charges;
    @Column(name = "delivery_executive_id")
    private UUID deliveryExecutiveId;
    @Column(name = "delivery_address_id")
    private UUID deliveryAddressId;
    @Column(name = "delivery_lat")
    private Double deliveryLat;
    @Column(name = "delivery_lng")
    private Double deliveryLng;
    @Column(name = "delivery_address_text")
    private String deliveryAddress;
    @Column(name = "pickup_otp")
    private String pickupOtp;
    @Column(name = "otp")
    private String otp;
    @Column(name = "estimated_prep_time_minutes")
    private Integer estimatedPrepTimeMinutes;
    @Column(name = "estimated_completion_time")
    private Long estimatedCompletionTime;
    @Column(name = "cancellation_reason")
    private String cancellationReason;
    @Version
    @Column(name = "version")
    private Integer version;
    @JsonIgnore
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<OrderItem> orderItems;
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    
    private static Set<OrderCharge> $default$charges() {
        return new HashSet<>();
    }

    
    private static Set<OrderItem> $default$orderItems() {
        return new HashSet<>();
    }


    
    public static class OrderBuilder {
        
        private UUID id;
        
        private UUID customerId;
        
        private String customerName;
        
        private UUID restaurantId;
        
        private String restaurantName;
        
        private OrderStatus status;
        
        private com.fooddelivery.common.enums.DeliveryStatus deliveryStatus;
        
        private PaymentIntentStatus paymentStatus;
        
        private BigDecimal totalAmount;

        
        private BigDecimal itemTotal;
        
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

        
        private BigDecimal refundedAmount;
        
        private BigDecimal distanceKm;
        
        private boolean charges$set;
        
        private Set<OrderCharge> charges$value;
        
        private UUID deliveryExecutiveId;
        
        private UUID deliveryAddressId;
        
        private Double deliveryLat;
        
        private Double deliveryLng;
        
        private String deliveryAddress;
        
        private String pickupOtp;
        
        private String otp;
        
        private Integer estimatedPrepTimeMinutes;
        
        private Long estimatedCompletionTime;
        
        private String cancellationReason;
        
        private Integer version;
        
        private boolean orderItems$set;
        
        private Set<OrderItem> orderItems$value;
        
        private LocalDateTime createdAt;
        
        private LocalDateTime updatedAt;
        
        private LocalDateTime deliveredAt;

        
        OrderBuilder() {
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder customerId(final UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder customerName(final String customerName) {
            this.customerName = customerName;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder restaurantId(final UUID restaurantId) {
            this.restaurantId = restaurantId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder restaurantName(final String restaurantName) {
            this.restaurantName = restaurantName;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder status(final OrderStatus status) {
            this.status = status;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder deliveryStatus(final com.fooddelivery.common.enums.DeliveryStatus deliveryStatus) {
            this.deliveryStatus = deliveryStatus;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder paymentStatus(final PaymentIntentStatus paymentStatus) {
            this.paymentStatus = paymentStatus;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
public Order.OrderBuilder itemTotal(final BigDecimal itemTotal) {
            this.itemTotal = itemTotal;
            return this;
        }

        
        public Order.OrderBuilder customerPlatformFee(final BigDecimal customerPlatformFee) {
            this.customerPlatformFee = customerPlatformFee;
            return this;
        }

        
        public Order.OrderBuilder restaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
            this.restaurantPlatformFee = restaurantPlatformFee;
            return this;
        }

        
        public Order.OrderBuilder platformBonus(final BigDecimal platformBonus) {
            this.platformBonus = platformBonus;
            return this;
        }

        
        public Order.OrderBuilder restaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
            this.restaurantDeliveryContribution = restaurantDeliveryContribution;
            return this;
        }

        
        public Order.OrderBuilder restaurantPayout(final BigDecimal restaurantPayout) {
            this.restaurantPayout = restaurantPayout;
            return this;
        }

        
        public Order.OrderBuilder sgst(final BigDecimal sgst) {
            this.sgst = sgst;
            return this;
        }

        
        public Order.OrderBuilder cgst(final BigDecimal cgst) {
            this.cgst = cgst;
            return this;
        }

        
        public Order.OrderBuilder deliveryFee(final BigDecimal deliveryFee) {
            this.deliveryFee = deliveryFee;
            return this;
        }

        
        public Order.OrderBuilder driverGrossPayout(final BigDecimal driverGrossPayout) {
            this.driverGrossPayout = driverGrossPayout;
            return this;
        }

        
        public Order.OrderBuilder driverTaxes(final BigDecimal driverTaxes) {
            this.driverTaxes = driverTaxes;
            return this;
        }

        
        public Order.OrderBuilder driverNetPayout(final BigDecimal driverNetPayout) {
            this.driverNetPayout = driverNetPayout;
            return this;
        }

        public Order.OrderBuilder totalAmount(final BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        this.itemTotal = itemTotal;
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

            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder refundedAmount(final BigDecimal refundedAmount) {
            this.refundedAmount = refundedAmount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder distanceKm(final BigDecimal distanceKm) {
            this.distanceKm = distanceKm;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @JsonIgnore
        
        public Order.OrderBuilder charges(final Set<OrderCharge> charges) {
            this.charges$value = charges;
            charges$set = true;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder deliveryExecutiveId(final UUID deliveryExecutiveId) {
            this.deliveryExecutiveId = deliveryExecutiveId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder deliveryAddressId(final UUID deliveryAddressId) {
            this.deliveryAddressId = deliveryAddressId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder deliveryLat(final Double deliveryLat) {
            this.deliveryLat = deliveryLat;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder deliveryLng(final Double deliveryLng) {
            this.deliveryLng = deliveryLng;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder deliveryAddress(final String deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder pickupOtp(final String pickupOtp) {
            this.pickupOtp = pickupOtp;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder otp(final String otp) {
            this.otp = otp;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder estimatedPrepTimeMinutes(final Integer estimatedPrepTimeMinutes) {
            this.estimatedPrepTimeMinutes = estimatedPrepTimeMinutes;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder estimatedCompletionTime(final Long estimatedCompletionTime) {
            this.estimatedCompletionTime = estimatedCompletionTime;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder cancellationReason(final String cancellationReason) {
            this.cancellationReason = cancellationReason;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder version(final Integer version) {
            this.version = version;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @JsonIgnore
        
        public Order.OrderBuilder orderItems(final Set<OrderItem> orderItems) {
            this.orderItems$value = orderItems;
            orderItems$set = true;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder createdAt(final LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder updatedAt(final LocalDateTime updatedAt, final LocalDateTime deliveredAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public Order.OrderBuilder deliveredAt(final LocalDateTime deliveredAt) {
            this.deliveredAt = deliveredAt;
            return this;
        }

        
        public Order build() {
            Set<OrderCharge> charges$value = this.charges$value;
            if (!this.charges$set) charges$value = Order.$default$charges();
            Set<OrderItem> orderItems$value = this.orderItems$value;
            if (!this.orderItems$set) orderItems$value = Order.$default$orderItems();
            return new Order(this.id, this.customerId, this.customerName, this.restaurantId, this.restaurantName, this.status, this.deliveryStatus, this.paymentStatus, this.totalAmount, this.itemTotal, this.customerPlatformFee, this.restaurantPlatformFee, this.platformBonus, this.restaurantDeliveryContribution, this.restaurantPayout, this.sgst, this.cgst, this.deliveryFee, this.driverGrossPayout, this.driverTaxes, this.driverNetPayout, this.refundedAmount, this.distanceKm, charges$value, this.deliveryExecutiveId, this.deliveryAddressId, this.deliveryLat, this.deliveryLng, this.deliveryAddress, this.pickupOtp, this.otp, this.estimatedPrepTimeMinutes, this.estimatedCompletionTime, this.cancellationReason, this.version, orderItems$value, this.createdAt, this.updatedAt, this.deliveredAt);
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "Order.OrderBuilder(id=" + this.id + ", customerId=" + this.customerId + ", customerName=" + this.customerName + ", restaurantId=" + this.restaurantId + ", restaurantName=" + this.restaurantName + ", status=" + this.status + ", deliveryStatus=" + this.deliveryStatus + ", paymentStatus=" + this.paymentStatus + ", totalAmount=" + this.totalAmount + ", refundedAmount=" + this.refundedAmount + ", distanceKm=" + this.distanceKm + ", charges$value=" + this.charges$value + ", deliveryExecutiveId=" + this.deliveryExecutiveId + ", deliveryAddressId=" + this.deliveryAddressId + ", deliveryLat=" + this.deliveryLat + ", deliveryLng=" + this.deliveryLng + ", deliveryAddress=" + this.deliveryAddress + ", pickupOtp=" + this.pickupOtp + ", otp=" + this.otp + ", estimatedPrepTimeMinutes=" + this.estimatedPrepTimeMinutes + ", estimatedCompletionTime=" + this.estimatedCompletionTime + ", cancellationReason=" + this.cancellationReason + ", version=" + this.version + ", orderItems$value=" + this.orderItems$value + ", createdAt=" + this.createdAt + ", updatedAt=" + this.updatedAt + ")";
        }
    }

    
    public static Order.OrderBuilder builder() {
        return new Order.OrderBuilder();
    }

    
    public UUID getId() {
        return this.id;
    }

    
    public UUID getCustomerId() {
        return this.customerId;
    }

    
    public String getCustomerName() {
        return this.customerName;
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

    
    public PaymentIntentStatus getPaymentStatus() {
        return this.paymentStatus;
    }

    
public BigDecimal getItemTotal() {
        return this.itemTotal;
    }

    
    public void setItemTotal(final BigDecimal itemTotal) {
        this.itemTotal = itemTotal;
    }

    
    public BigDecimal getCustomerPlatformFee() {
        return this.customerPlatformFee;
    }

    
    public void setCustomerPlatformFee(final BigDecimal customerPlatformFee) {
        this.customerPlatformFee = customerPlatformFee;
    }

    
    public BigDecimal getRestaurantPlatformFee() {
        return this.restaurantPlatformFee;
    }

    
    public void setRestaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
        this.restaurantPlatformFee = restaurantPlatformFee;
    }

    
    public BigDecimal getPlatformBonus() {
        return this.platformBonus;
    }

    
    public void setPlatformBonus(final BigDecimal platformBonus) {
        this.platformBonus = platformBonus;
    }

    
    public BigDecimal getRestaurantDeliveryContribution() {
        return this.restaurantDeliveryContribution;
    }

    
    public void setRestaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
        this.restaurantDeliveryContribution = restaurantDeliveryContribution;
    }

    
    public BigDecimal getRestaurantPayout() {
        return this.restaurantPayout;
    }

    
    public void setRestaurantPayout(final BigDecimal restaurantPayout) {
        this.restaurantPayout = restaurantPayout;
    }

    
    public BigDecimal getSgst() {
        return this.sgst;
    }

    
    public void setSgst(final BigDecimal sgst) {
        this.sgst = sgst;
    }

    
    public BigDecimal getCgst() {
        return this.cgst;
    }

    
    public void setCgst(final BigDecimal cgst) {
        this.cgst = cgst;
    }

    
    public BigDecimal getDeliveryFee() {
        return this.deliveryFee;
    }

    
    public void setDeliveryFee(final BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
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

    public BigDecimal getTotalAmount() {
        return this.totalAmount;
    }

    
    public BigDecimal getRefundedAmount() {
        return this.refundedAmount;
    }

    
    public BigDecimal getDistanceKm() {
        return this.distanceKm;
    }

    
    public Set<OrderCharge> getCharges() {
        return this.charges;
    }

    
    public UUID getDeliveryExecutiveId() {
        return this.deliveryExecutiveId;
    }

    
    public UUID getDeliveryAddressId() {
        return this.deliveryAddressId;
    }

    
    public Double getDeliveryLat() {
        return this.deliveryLat;
    }

    
    public Double getDeliveryLng() {
        return this.deliveryLng;
    }

    
    public String getDeliveryAddress() {
        return this.deliveryAddress;
    }

    
    public String getPickupOtp() {
        return this.pickupOtp;
    }

    
    public String getOtp() {
        return this.otp;
    }

    
    public Integer getEstimatedPrepTimeMinutes() {
        return this.estimatedPrepTimeMinutes;
    }

    
    public Long getEstimatedCompletionTime() {
        return this.estimatedCompletionTime;
    }

    
    public String getCancellationReason() {
        return this.cancellationReason;
    }

    
    public Integer getVersion() {
        return this.version;
    }

    
    public Set<OrderItem> getOrderItems() {
        return this.orderItems;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public void setId(final UUID id) {
        this.id = id;
    }

    
    public void setCustomerId(final UUID customerId) {
        this.customerId = customerId;
    }

    
    public void setCustomerName(final String customerName) {
        this.customerName = customerName;
    }

    
    public void setRestaurantId(final UUID restaurantId) {
        this.restaurantId = restaurantId;
    }

    
    public void setRestaurantName(final String restaurantName) {
        this.restaurantName = restaurantName;
    }

    
    public void setDeliveryStatus(final com.fooddelivery.common.enums.DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    
    public void setPaymentStatus(final PaymentIntentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    
    public void setTotalAmount(final BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        this.itemTotal = itemTotal;
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

    }

    
    public void setRefundedAmount(final BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    
    public void setDistanceKm(final BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    @JsonIgnore
    
    public void setCharges(final Set<OrderCharge> charges) {
        this.charges = charges;
    }

    
    public void setDeliveryExecutiveId(final UUID deliveryExecutiveId) {
        this.deliveryExecutiveId = deliveryExecutiveId;
    }

    
    public void setDeliveryAddressId(final UUID deliveryAddressId) {
        this.deliveryAddressId = deliveryAddressId;
    }

    
    public void setDeliveryLat(final Double deliveryLat) {
        this.deliveryLat = deliveryLat;
    }

    
    public void setDeliveryLng(final Double deliveryLng) {
        this.deliveryLng = deliveryLng;
    }

    
    public void setDeliveryAddress(final String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    
    public void setPickupOtp(final String pickupOtp) {
        this.pickupOtp = pickupOtp;
    }

    
    public void setOtp(final String otp) {
        this.otp = otp;
    }

    
    public void setEstimatedPrepTimeMinutes(final Integer estimatedPrepTimeMinutes) {
        this.estimatedPrepTimeMinutes = estimatedPrepTimeMinutes;
    }

    
    public void setEstimatedCompletionTime(final Long estimatedCompletionTime) {
        this.estimatedCompletionTime = estimatedCompletionTime;
    }

    
    public void setCancellationReason(final String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    
    public void setVersion(final Integer version) {
        this.version = version;
    }

    @JsonIgnore
    
    public void setOrderItems(final Set<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    public void setUpdatedAt(final LocalDateTime updatedAt, final LocalDateTime deliveredAt) {
        this.updatedAt = updatedAt;
        this.deliveredAt = deliveredAt;
    }

    
    public LocalDateTime getDeliveredAt() {
        return this.deliveredAt;
    }

    
    public void setDeliveredAt(final LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    
    public Order() {
        this.charges = Order.$default$charges();
        this.orderItems = Order.$default$orderItems();
    }

    
    public Order(final UUID id, final UUID customerId, final String customerName, final UUID restaurantId, final String restaurantName, final OrderStatus status, final com.fooddelivery.common.enums.DeliveryStatus deliveryStatus, final PaymentIntentStatus paymentStatus, final BigDecimal totalAmount, final BigDecimal itemTotal, final BigDecimal customerPlatformFee, final BigDecimal restaurantPlatformFee, final BigDecimal platformBonus, final BigDecimal restaurantDeliveryContribution, final BigDecimal restaurantPayout, final BigDecimal sgst, final BigDecimal cgst, final BigDecimal deliveryFee, final BigDecimal driverGrossPayout, final BigDecimal driverTaxes, final BigDecimal driverNetPayout, final BigDecimal refundedAmount, final BigDecimal distanceKm, final Set<OrderCharge> charges, final UUID deliveryExecutiveId, final UUID deliveryAddressId, final Double deliveryLat, final Double deliveryLng, final String deliveryAddress, final String pickupOtp, final String otp, final Integer estimatedPrepTimeMinutes, final Long estimatedCompletionTime, final String cancellationReason, final Integer version, final Set<OrderItem> orderItems, final LocalDateTime createdAt, final LocalDateTime updatedAt, final LocalDateTime deliveredAt) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.status = status;
        this.deliveryStatus = deliveryStatus;
        this.paymentStatus = paymentStatus;
        this.totalAmount = totalAmount;
        this.itemTotal = itemTotal;
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

        this.refundedAmount = refundedAmount;
        this.distanceKm = distanceKm;
        this.charges = charges;
        this.deliveryExecutiveId = deliveryExecutiveId;
        this.deliveryAddressId = deliveryAddressId;
        this.deliveryLat = deliveryLat;
        this.deliveryLng = deliveryLng;
        this.deliveryAddress = deliveryAddress;
        this.pickupOtp = pickupOtp;
        this.otp = otp;
        this.estimatedPrepTimeMinutes = estimatedPrepTimeMinutes;
        this.estimatedCompletionTime = estimatedCompletionTime;
        this.cancellationReason = cancellationReason;
        this.version = version;
        this.orderItems = orderItems;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deliveredAt = deliveredAt;
    }
}
