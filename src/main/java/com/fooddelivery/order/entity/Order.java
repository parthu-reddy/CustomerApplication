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
import com.fooddelivery.common.enums.DeliveryStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class Order {
    @Id
    private UUID id;
    
    private UUID customerId;
    private UUID restaurantId;
    private String restaurantName;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

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
    private Integer version;
    
    @JsonIgnore
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
