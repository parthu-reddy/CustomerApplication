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
    @java.lang.SuppressWarnings("all")

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

    @java.lang.SuppressWarnings("all")
    private static Set<OrderCharge> $default$charges() {
        return new HashSet<>();
    }

    @java.lang.SuppressWarnings("all")
    private static Set<OrderItem> $default$orderItems() {
        return new HashSet<>();
    }


    @java.lang.SuppressWarnings("all")
    public static class OrderBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID id;
        @java.lang.SuppressWarnings("all")
        private UUID customerId;
        @java.lang.SuppressWarnings("all")
        private String customerName;
        @java.lang.SuppressWarnings("all")
        private UUID restaurantId;
        @java.lang.SuppressWarnings("all")
        private String restaurantName;
        @java.lang.SuppressWarnings("all")
        private OrderStatus status;
        @java.lang.SuppressWarnings("all")
        private com.fooddelivery.common.enums.DeliveryStatus deliveryStatus;
        @java.lang.SuppressWarnings("all")
        private PaymentIntentStatus paymentStatus;
        @java.lang.SuppressWarnings("all")
        private BigDecimal totalAmount;

        @java.lang.SuppressWarnings("all")
        private BigDecimal itemTotal;
        @java.lang.SuppressWarnings("all")
        private BigDecimal customerPlatformFee;
        @java.lang.SuppressWarnings("all")
        private BigDecimal restaurantPlatformFee;
        @java.lang.SuppressWarnings("all")
        private BigDecimal platformBonus;
        @java.lang.SuppressWarnings("all")
        private BigDecimal restaurantDeliveryContribution;
        @java.lang.SuppressWarnings("all")
        private BigDecimal restaurantPayout;
        @java.lang.SuppressWarnings("all")
        private BigDecimal sgst;
        @java.lang.SuppressWarnings("all")
        private BigDecimal cgst;
        @java.lang.SuppressWarnings("all")
        private BigDecimal deliveryFee;
        @java.lang.SuppressWarnings("all")
        private BigDecimal driverGrossPayout;
        @java.lang.SuppressWarnings("all")
        private BigDecimal driverTaxes;
        @java.lang.SuppressWarnings("all")
        private BigDecimal driverNetPayout;

        @java.lang.SuppressWarnings("all")
        private BigDecimal refundedAmount;
        @java.lang.SuppressWarnings("all")
        private BigDecimal distanceKm;
        @java.lang.SuppressWarnings("all")
        private boolean charges$set;
        @java.lang.SuppressWarnings("all")
        private Set<OrderCharge> charges$value;
        @java.lang.SuppressWarnings("all")
        private UUID deliveryExecutiveId;
        @java.lang.SuppressWarnings("all")
        private UUID deliveryAddressId;
        @java.lang.SuppressWarnings("all")
        private Double deliveryLat;
        @java.lang.SuppressWarnings("all")
        private Double deliveryLng;
        @java.lang.SuppressWarnings("all")
        private String deliveryAddress;
        @java.lang.SuppressWarnings("all")
        private String pickupOtp;
        @java.lang.SuppressWarnings("all")
        private String otp;
        @java.lang.SuppressWarnings("all")
        private Integer estimatedPrepTimeMinutes;
        @java.lang.SuppressWarnings("all")
        private Long estimatedCompletionTime;
        @java.lang.SuppressWarnings("all")
        private String cancellationReason;
        @java.lang.SuppressWarnings("all")
        private Integer version;
        @java.lang.SuppressWarnings("all")
        private boolean orderItems$set;
        @java.lang.SuppressWarnings("all")
        private Set<OrderItem> orderItems$value;
        @java.lang.SuppressWarnings("all")
        private LocalDateTime createdAt;
        @java.lang.SuppressWarnings("all")
        private LocalDateTime updatedAt;
        @java.lang.SuppressWarnings("all")
        private LocalDateTime deliveredAt;

        @java.lang.SuppressWarnings("all")
        OrderBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder customerId(final UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder customerName(final String customerName) {
            this.customerName = customerName;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder restaurantId(final UUID restaurantId) {
            this.restaurantId = restaurantId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder restaurantName(final String restaurantName) {
            this.restaurantName = restaurantName;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder status(final OrderStatus status) {
            this.status = status;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder deliveryStatus(final com.fooddelivery.common.enums.DeliveryStatus deliveryStatus) {
            this.deliveryStatus = deliveryStatus;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder paymentStatus(final PaymentIntentStatus paymentStatus) {
            this.paymentStatus = paymentStatus;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
public Order.OrderBuilder itemTotal(final BigDecimal itemTotal) {
            this.itemTotal = itemTotal;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder customerPlatformFee(final BigDecimal customerPlatformFee) {
            this.customerPlatformFee = customerPlatformFee;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder restaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
            this.restaurantPlatformFee = restaurantPlatformFee;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder platformBonus(final BigDecimal platformBonus) {
            this.platformBonus = platformBonus;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder restaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
            this.restaurantDeliveryContribution = restaurantDeliveryContribution;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder restaurantPayout(final BigDecimal restaurantPayout) {
            this.restaurantPayout = restaurantPayout;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder sgst(final BigDecimal sgst) {
            this.sgst = sgst;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder cgst(final BigDecimal cgst) {
            this.cgst = cgst;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder deliveryFee(final BigDecimal deliveryFee) {
            this.deliveryFee = deliveryFee;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder driverGrossPayout(final BigDecimal driverGrossPayout) {
            this.driverGrossPayout = driverGrossPayout;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder driverTaxes(final BigDecimal driverTaxes) {
            this.driverTaxes = driverTaxes;
            return this;
        }

        @java.lang.SuppressWarnings("all")
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
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder refundedAmount(final BigDecimal refundedAmount) {
            this.refundedAmount = refundedAmount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder distanceKm(final BigDecimal distanceKm) {
            this.distanceKm = distanceKm;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @JsonIgnore
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder charges(final Set<OrderCharge> charges) {
            this.charges$value = charges;
            charges$set = true;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder deliveryExecutiveId(final UUID deliveryExecutiveId) {
            this.deliveryExecutiveId = deliveryExecutiveId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder deliveryAddressId(final UUID deliveryAddressId) {
            this.deliveryAddressId = deliveryAddressId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder deliveryLat(final Double deliveryLat) {
            this.deliveryLat = deliveryLat;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder deliveryLng(final Double deliveryLng) {
            this.deliveryLng = deliveryLng;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder deliveryAddress(final String deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder pickupOtp(final String pickupOtp) {
            this.pickupOtp = pickupOtp;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder otp(final String otp) {
            this.otp = otp;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder estimatedPrepTimeMinutes(final Integer estimatedPrepTimeMinutes) {
            this.estimatedPrepTimeMinutes = estimatedPrepTimeMinutes;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder estimatedCompletionTime(final Long estimatedCompletionTime) {
            this.estimatedCompletionTime = estimatedCompletionTime;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder cancellationReason(final String cancellationReason) {
            this.cancellationReason = cancellationReason;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder version(final Integer version) {
            this.version = version;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @JsonIgnore
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder orderItems(final Set<OrderItem> orderItems) {
            this.orderItems$value = orderItems;
            orderItems$set = true;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder createdAt(final LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder updatedAt(final LocalDateTime updatedAt, final LocalDateTime deliveredAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Order.OrderBuilder deliveredAt(final LocalDateTime deliveredAt) {
            this.deliveredAt = deliveredAt;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Order build() {
            Set<OrderCharge> charges$value = this.charges$value;
            if (!this.charges$set) charges$value = Order.$default$charges();
            Set<OrderItem> orderItems$value = this.orderItems$value;
            if (!this.orderItems$set) orderItems$value = Order.$default$orderItems();
            return new Order(this.id, this.customerId, this.customerName, this.restaurantId, this.restaurantName, this.status, this.deliveryStatus, this.paymentStatus, this.totalAmount, this.itemTotal, this.customerPlatformFee, this.restaurantPlatformFee, this.platformBonus, this.restaurantDeliveryContribution, this.restaurantPayout, this.sgst, this.cgst, this.deliveryFee, this.driverGrossPayout, this.driverTaxes, this.driverNetPayout, this.refundedAmount, this.distanceKm, charges$value, this.deliveryExecutiveId, this.deliveryAddressId, this.deliveryLat, this.deliveryLng, this.deliveryAddress, this.pickupOtp, this.otp, this.estimatedPrepTimeMinutes, this.estimatedCompletionTime, this.cancellationReason, this.version, orderItems$value, this.createdAt, this.updatedAt, this.deliveredAt);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "Order.OrderBuilder(id=" + this.id + ", customerId=" + this.customerId + ", customerName=" + this.customerName + ", restaurantId=" + this.restaurantId + ", restaurantName=" + this.restaurantName + ", status=" + this.status + ", deliveryStatus=" + this.deliveryStatus + ", paymentStatus=" + this.paymentStatus + ", totalAmount=" + this.totalAmount + ", refundedAmount=" + this.refundedAmount + ", distanceKm=" + this.distanceKm + ", charges$value=" + this.charges$value + ", deliveryExecutiveId=" + this.deliveryExecutiveId + ", deliveryAddressId=" + this.deliveryAddressId + ", deliveryLat=" + this.deliveryLat + ", deliveryLng=" + this.deliveryLng + ", deliveryAddress=" + this.deliveryAddress + ", pickupOtp=" + this.pickupOtp + ", otp=" + this.otp + ", estimatedPrepTimeMinutes=" + this.estimatedPrepTimeMinutes + ", estimatedCompletionTime=" + this.estimatedCompletionTime + ", cancellationReason=" + this.cancellationReason + ", version=" + this.version + ", orderItems$value=" + this.orderItems$value + ", createdAt=" + this.createdAt + ", updatedAt=" + this.updatedAt + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static Order.OrderBuilder builder() {
        return new Order.OrderBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getCustomerId() {
        return this.customerId;
    }

    @java.lang.SuppressWarnings("all")
    public String getCustomerName() {
        return this.customerName;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getRestaurantId() {
        return this.restaurantId;
    }

    @java.lang.SuppressWarnings("all")
    public String getRestaurantName() {
        return this.restaurantName;
    }

    @java.lang.SuppressWarnings("all")
    public OrderStatus getStatus() {
        return this.status;
    }

    @java.lang.SuppressWarnings("all")
    public com.fooddelivery.common.enums.DeliveryStatus getDeliveryStatus() {
        return this.deliveryStatus;
    }

    @java.lang.SuppressWarnings("all")
    public PaymentIntentStatus getPaymentStatus() {
        return this.paymentStatus;
    }

    @java.lang.SuppressWarnings("all")
public BigDecimal getItemTotal() {
        return this.itemTotal;
    }

    @java.lang.SuppressWarnings("all")
    public void setItemTotal(final BigDecimal itemTotal) {
        this.itemTotal = itemTotal;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getCustomerPlatformFee() {
        return this.customerPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public void setCustomerPlatformFee(final BigDecimal customerPlatformFee) {
        this.customerPlatformFee = customerPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getRestaurantPlatformFee() {
        return this.restaurantPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
        this.restaurantPlatformFee = restaurantPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getPlatformBonus() {
        return this.platformBonus;
    }

    @java.lang.SuppressWarnings("all")
    public void setPlatformBonus(final BigDecimal platformBonus) {
        this.platformBonus = platformBonus;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getRestaurantDeliveryContribution() {
        return this.restaurantDeliveryContribution;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
        this.restaurantDeliveryContribution = restaurantDeliveryContribution;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getRestaurantPayout() {
        return this.restaurantPayout;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestaurantPayout(final BigDecimal restaurantPayout) {
        this.restaurantPayout = restaurantPayout;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getSgst() {
        return this.sgst;
    }

    @java.lang.SuppressWarnings("all")
    public void setSgst(final BigDecimal sgst) {
        this.sgst = sgst;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getCgst() {
        return this.cgst;
    }

    @java.lang.SuppressWarnings("all")
    public void setCgst(final BigDecimal cgst) {
        this.cgst = cgst;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getDeliveryFee() {
        return this.deliveryFee;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveryFee(final BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getDriverGrossPayout() {
        return this.driverGrossPayout;
    }

    @java.lang.SuppressWarnings("all")
    public void setDriverGrossPayout(final BigDecimal driverGrossPayout) {
        this.driverGrossPayout = driverGrossPayout;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getDriverTaxes() {
        return this.driverTaxes;
    }

    @java.lang.SuppressWarnings("all")
    public void setDriverTaxes(final BigDecimal driverTaxes) {
        this.driverTaxes = driverTaxes;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getDriverNetPayout() {
        return this.driverNetPayout;
    }

    @java.lang.SuppressWarnings("all")
    public void setDriverNetPayout(final BigDecimal driverNetPayout) {
        this.driverNetPayout = driverNetPayout;
    }

    public BigDecimal getTotalAmount() {
        return this.totalAmount;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getRefundedAmount() {
        return this.refundedAmount;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getDistanceKm() {
        return this.distanceKm;
    }

    @java.lang.SuppressWarnings("all")
    public Set<OrderCharge> getCharges() {
        return this.charges;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getDeliveryExecutiveId() {
        return this.deliveryExecutiveId;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getDeliveryAddressId() {
        return this.deliveryAddressId;
    }

    @java.lang.SuppressWarnings("all")
    public Double getDeliveryLat() {
        return this.deliveryLat;
    }

    @java.lang.SuppressWarnings("all")
    public Double getDeliveryLng() {
        return this.deliveryLng;
    }

    @java.lang.SuppressWarnings("all")
    public String getDeliveryAddress() {
        return this.deliveryAddress;
    }

    @java.lang.SuppressWarnings("all")
    public String getPickupOtp() {
        return this.pickupOtp;
    }

    @java.lang.SuppressWarnings("all")
    public String getOtp() {
        return this.otp;
    }

    @java.lang.SuppressWarnings("all")
    public Integer getEstimatedPrepTimeMinutes() {
        return this.estimatedPrepTimeMinutes;
    }

    @java.lang.SuppressWarnings("all")
    public Long getEstimatedCompletionTime() {
        return this.estimatedCompletionTime;
    }

    @java.lang.SuppressWarnings("all")
    public String getCancellationReason() {
        return this.cancellationReason;
    }

    @java.lang.SuppressWarnings("all")
    public Integer getVersion() {
        return this.version;
    }

    @java.lang.SuppressWarnings("all")
    public Set<OrderItem> getOrderItems() {
        return this.orderItems;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setCustomerId(final UUID customerId) {
        this.customerId = customerId;
    }

    @java.lang.SuppressWarnings("all")
    public void setCustomerName(final String customerName) {
        this.customerName = customerName;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestaurantId(final UUID restaurantId) {
        this.restaurantId = restaurantId;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestaurantName(final String restaurantName) {
        this.restaurantName = restaurantName;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveryStatus(final com.fooddelivery.common.enums.DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    @java.lang.SuppressWarnings("all")
    public void setPaymentStatus(final PaymentIntentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    @java.lang.SuppressWarnings("all")
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

    @java.lang.SuppressWarnings("all")
    public void setRefundedAmount(final BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    @java.lang.SuppressWarnings("all")
    public void setDistanceKm(final BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    @JsonIgnore
    @java.lang.SuppressWarnings("all")
    public void setCharges(final Set<OrderCharge> charges) {
        this.charges = charges;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveryExecutiveId(final UUID deliveryExecutiveId) {
        this.deliveryExecutiveId = deliveryExecutiveId;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveryAddressId(final UUID deliveryAddressId) {
        this.deliveryAddressId = deliveryAddressId;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveryLat(final Double deliveryLat) {
        this.deliveryLat = deliveryLat;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveryLng(final Double deliveryLng) {
        this.deliveryLng = deliveryLng;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveryAddress(final String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    @java.lang.SuppressWarnings("all")
    public void setPickupOtp(final String pickupOtp) {
        this.pickupOtp = pickupOtp;
    }

    @java.lang.SuppressWarnings("all")
    public void setOtp(final String otp) {
        this.otp = otp;
    }

    @java.lang.SuppressWarnings("all")
    public void setEstimatedPrepTimeMinutes(final Integer estimatedPrepTimeMinutes) {
        this.estimatedPrepTimeMinutes = estimatedPrepTimeMinutes;
    }

    @java.lang.SuppressWarnings("all")
    public void setEstimatedCompletionTime(final Long estimatedCompletionTime) {
        this.estimatedCompletionTime = estimatedCompletionTime;
    }

    @java.lang.SuppressWarnings("all")
    public void setCancellationReason(final String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    @java.lang.SuppressWarnings("all")
    public void setVersion(final Integer version) {
        this.version = version;
    }

    @JsonIgnore
    @java.lang.SuppressWarnings("all")
    public void setOrderItems(final Set<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    @java.lang.SuppressWarnings("all")
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setUpdatedAt(final LocalDateTime updatedAt, final LocalDateTime deliveredAt) {
        this.updatedAt = updatedAt;
        this.deliveredAt = deliveredAt;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getDeliveredAt() {
        return this.deliveredAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveredAt(final LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    @java.lang.SuppressWarnings("all")
    public Order() {
        this.charges = Order.$default$charges();
        this.orderItems = Order.$default$orderItems();
    }

    @java.lang.SuppressWarnings("all")
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
