package com.fooddelivery.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

import lombok.extern.slf4j.Slf4j;

@Entity
@Table(name = "orders", indexes = {
    @jakarta.persistence.Index(name = "idx_order_customer", columnList = "customerId"),
    @jakarta.persistence.Index(name = "idx_order_delivery_exec", columnList = "deliveryExecutiveId"),
    @jakarta.persistence.Index(name = "idx_order_status", columnList = "status")
})
@lombok.Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
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
    
    @Column(name = "refunded_amount")
    private BigDecimal refundedAmount;
    
    @Column(name = "distance_km")
    private BigDecimal distanceKm;
    
    @JsonIgnore
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
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
    
    @JsonIgnore
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private Set<OrderItem> orderItems = new HashSet<>();
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
