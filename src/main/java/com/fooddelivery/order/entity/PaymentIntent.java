package com.fooddelivery.order.entity;

import jakarta.persistence.*;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.enums.PaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_intents")
@lombok.Getter
@lombok.Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PaymentIntent {
    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "internal_order_id", nullable = false)
    private UUID internalOrderId;
    @Column(name = "gateway_order_id")
    private String gatewayOrderId;
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentIntentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_name")
    private PaymentGateway gatewayName;
    


    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /**
     * Maintained by Hibernate on every update. RefundRetrySweeper sweeps on this, not createdAt:
     * an intent sitting in REFUND_PENDING is stuck if it has not changed recently, regardless of
     * how long ago the payment itself was created.
     */
    @org.hibernate.annotations.UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    


}
