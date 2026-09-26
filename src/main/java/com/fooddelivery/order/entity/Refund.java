package com.fooddelivery.order.entity;

import jakarta.persistence.*;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.RefundSource;
import com.fooddelivery.order.enums.InitiatorType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "payment_intent_id", nullable = false)
    private UUID paymentIntentId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @lombok.Builder.Default
        @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    private String currency = "INR";

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    @Column(name = "reason_text", length = 2000)
    private String reasonText;

    @Enumerated(EnumType.STRING)
    @Column(name = "fault_type", nullable = false, length = 32)
    private FaultType faultType;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination", nullable = false, length = 32)
    private RefundDestination destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private RefundSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiated_by_type", nullable = false, length = 16)
    private InitiatorType initiatedByType;

    @Column(name = "initiated_by_id")
    private UUID initiatedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RefundStatus status;

    @Column(name = "gateway_refund_id", length = 255)
    private String gatewayRefundId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "idempotency_key", nullable = false, length = 255, unique = true)
    private String idempotencyKey;

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "attempts", nullable = false)
    @lombok.Builder.Default
    private int attempts = 0;

    @lombok.Builder.Default
    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<RefundItem> refundItems = new HashSet<>();
}
