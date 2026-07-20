package com.fooddelivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.fooddelivery.common.enums.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    private UUID id;

    @Column(name = "payment_intent_id", nullable = false)
    private UUID paymentIntentId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
