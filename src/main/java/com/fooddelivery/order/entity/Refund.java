package com.fooddelivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.fooddelivery.common.enums.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@lombok.Getter
@lombok.Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Refund {
    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "payment_intent_id", nullable = false)
    private UUID paymentIntentId;
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private RefundStatus status;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;


    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    
}
