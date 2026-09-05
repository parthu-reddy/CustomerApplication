package com.fooddelivery.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.PaymentMethod;
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
@lombok.Getter
@lombok.Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Order {
    

    @Id
    @Column(name = "id")
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Column(name = "customer_id")
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private UUID customerId;
    @Column(name = "customer_name")
    private String customerName;
    @Column(name = "restaurant_id")
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private UUID restaurantId;
    @Column(name = "restaurant_name")
    private String restaurantName;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "cash_collected_amount")
    private BigDecimal cashCollectedAmount;

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
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
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

    /** The quote this order was priced from. */
    @Column(name = "quote_id")
    private UUID quoteId;

    /**
     * The rates in force when the quote was issued. Pricing config is {@code @RefreshScope}, so
     * without this an order cannot be reproduced once the live rates move — which is precisely what
     * a restaurant or driver payout dispute asks for.
     */
    @Column(name = "rate_base_price")
    private BigDecimal rateBasePrice;
    @Column(name = "rate_per_km")
    private BigDecimal ratePerKm;
    @Column(name = "rate_rest_max_contribution_percent")
    private BigDecimal rateRestMaxContributionPercent;
    @Column(name = "rate_fixed_platform_fee")
    private BigDecimal rateFixedPlatformFee;
    @Column(name = "rate_platform_excess_cut_percent")
    private BigDecimal ratePlatformExcessCutPercent;
    @Column(name = "rate_sgst_percent")
    private BigDecimal rateSgstPercent;
    @Column(name = "rate_cgst_percent")
    private BigDecimal rateCgstPercent;
    @Column(name = "rate_delivery_sgst_percent")
    private BigDecimal rateDeliverySgstPercent;
    @Column(name = "rate_delivery_cgst_percent")
    private BigDecimal rateDeliveryCgstPercent;

    /** Records which rate set produced this order's charges. */
    public void applyRates(com.fooddelivery.customer.model.PricingRates rates) {
        this.rateBasePrice = rates.basePrice();
        this.ratePerKm = rates.perKmRate();
        this.rateRestMaxContributionPercent = rates.restMaxContributionPercent();
        this.rateFixedPlatformFee = rates.fixedPlatformFee();
        this.ratePlatformExcessCutPercent = rates.platformExcessCutPercent();
        this.rateSgstPercent = rates.sgstPercent();
        this.rateCgstPercent = rates.cgstPercent();
        this.rateDeliverySgstPercent = rates.deliverySgstPercent();
        this.rateDeliveryCgstPercent = rates.deliveryCgstPercent();
    }


    @Column(name = "distance_km")
    private BigDecimal distanceKm;
    @lombok.Builder.Default
    @JsonIgnore
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<OrderCharge> charges = new HashSet<>();
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
    @lombok.Builder.Default
    @JsonIgnore
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<OrderItem> orderItems = new HashSet<>();
    @CreationTimestamp
    @Column(name = "created_at")
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    

    


    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    


    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    
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

    

    

    

    

    
    public void setUpdatedAt(final LocalDateTime updatedAt, final LocalDateTime deliveredAt) {
        this.updatedAt = updatedAt;
        this.deliveredAt = deliveredAt;
    }

    

    

    

    
}
