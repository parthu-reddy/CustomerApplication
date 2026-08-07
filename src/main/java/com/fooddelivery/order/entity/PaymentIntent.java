package com.fooddelivery.order.entity;

import jakarta.persistence.*;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_intents")
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
    @Column(name = "gateway_name")
    private String gatewayName;
    @Column(name = "created_at")
    private LocalDateTime createdAt;


    @java.lang.SuppressWarnings("all")
    public static class PaymentIntentBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID id;
        @java.lang.SuppressWarnings("all")
        private UUID internalOrderId;
        @java.lang.SuppressWarnings("all")
        private String gatewayOrderId;
        @java.lang.SuppressWarnings("all")
        private BigDecimal amount;
        @java.lang.SuppressWarnings("all")
        private PaymentIntentStatus status;
        @java.lang.SuppressWarnings("all")
        private String gatewayName;
        @java.lang.SuppressWarnings("all")
        private LocalDateTime createdAt;

        @java.lang.SuppressWarnings("all")
        PaymentIntentBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PaymentIntent.PaymentIntentBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PaymentIntent.PaymentIntentBuilder internalOrderId(final UUID internalOrderId) {
            this.internalOrderId = internalOrderId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PaymentIntent.PaymentIntentBuilder gatewayOrderId(final String gatewayOrderId) {
            this.gatewayOrderId = gatewayOrderId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PaymentIntent.PaymentIntentBuilder amount(final BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PaymentIntent.PaymentIntentBuilder status(final PaymentIntentStatus status) {
            this.status = status;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PaymentIntent.PaymentIntentBuilder gatewayName(final String gatewayName) {
            this.gatewayName = gatewayName;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PaymentIntent.PaymentIntentBuilder createdAt(final LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PaymentIntent build() {
            return new PaymentIntent(this.id, this.internalOrderId, this.gatewayOrderId, this.amount, this.status, this.gatewayName, this.createdAt);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "PaymentIntent.PaymentIntentBuilder(id=" + this.id + ", internalOrderId=" + this.internalOrderId + ", gatewayOrderId=" + this.gatewayOrderId + ", amount=" + this.amount + ", status=" + this.status + ", gatewayName=" + this.gatewayName + ", createdAt=" + this.createdAt + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static PaymentIntent.PaymentIntentBuilder builder() {
        return new PaymentIntent.PaymentIntentBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getInternalOrderId() {
        return this.internalOrderId;
    }

    @java.lang.SuppressWarnings("all")
    public String getGatewayOrderId() {
        return this.gatewayOrderId;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getAmount() {
        return this.amount;
    }

    @java.lang.SuppressWarnings("all")
    public PaymentIntentStatus getStatus() {
        return this.status;
    }

    @java.lang.SuppressWarnings("all")
    public String getGatewayName() {
        return this.gatewayName;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setInternalOrderId(final UUID internalOrderId) {
        this.internalOrderId = internalOrderId;
    }

    @java.lang.SuppressWarnings("all")
    public void setGatewayOrderId(final String gatewayOrderId) {
        this.gatewayOrderId = gatewayOrderId;
    }

    @java.lang.SuppressWarnings("all")
    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    @java.lang.SuppressWarnings("all")
    public void setStatus(final PaymentIntentStatus status) {
        this.status = status;
    }

    @java.lang.SuppressWarnings("all")
    public void setGatewayName(final String gatewayName) {
        this.gatewayName = gatewayName;
    }

    @java.lang.SuppressWarnings("all")
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof PaymentIntent)) return false;
        final PaymentIntent other = (PaymentIntent) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$internalOrderId = this.getInternalOrderId();
        final java.lang.Object other$internalOrderId = other.getInternalOrderId();
        if (this$internalOrderId == null ? other$internalOrderId != null : !this$internalOrderId.equals(other$internalOrderId)) return false;
        final java.lang.Object this$gatewayOrderId = this.getGatewayOrderId();
        final java.lang.Object other$gatewayOrderId = other.getGatewayOrderId();
        if (this$gatewayOrderId == null ? other$gatewayOrderId != null : !this$gatewayOrderId.equals(other$gatewayOrderId)) return false;
        final java.lang.Object this$amount = this.getAmount();
        final java.lang.Object other$amount = other.getAmount();
        if (this$amount == null ? other$amount != null : !this$amount.equals(other$amount)) return false;
        final java.lang.Object this$status = this.getStatus();
        final java.lang.Object other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
        final java.lang.Object this$gatewayName = this.getGatewayName();
        final java.lang.Object other$gatewayName = other.getGatewayName();
        if (this$gatewayName == null ? other$gatewayName != null : !this$gatewayName.equals(other$gatewayName)) return false;
        final java.lang.Object this$createdAt = this.getCreatedAt();
        final java.lang.Object other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof PaymentIntent;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $internalOrderId = this.getInternalOrderId();
        result = result * PRIME + ($internalOrderId == null ? 43 : $internalOrderId.hashCode());
        final java.lang.Object $gatewayOrderId = this.getGatewayOrderId();
        result = result * PRIME + ($gatewayOrderId == null ? 43 : $gatewayOrderId.hashCode());
        final java.lang.Object $amount = this.getAmount();
        result = result * PRIME + ($amount == null ? 43 : $amount.hashCode());
        final java.lang.Object $status = this.getStatus();
        result = result * PRIME + ($status == null ? 43 : $status.hashCode());
        final java.lang.Object $gatewayName = this.getGatewayName();
        result = result * PRIME + ($gatewayName == null ? 43 : $gatewayName.hashCode());
        final java.lang.Object $createdAt = this.getCreatedAt();
        result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "PaymentIntent(id=" + this.getId() + ", internalOrderId=" + this.getInternalOrderId() + ", gatewayOrderId=" + this.getGatewayOrderId() + ", amount=" + this.getAmount() + ", status=" + this.getStatus() + ", gatewayName=" + this.getGatewayName() + ", createdAt=" + this.getCreatedAt() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public PaymentIntent() {
    }

    @java.lang.SuppressWarnings("all")
    public PaymentIntent(final UUID id, final UUID internalOrderId, final String gatewayOrderId, final BigDecimal amount, final PaymentIntentStatus status, final String gatewayName, final LocalDateTime createdAt) {
        this.id = id;
        this.internalOrderId = internalOrderId;
        this.gatewayOrderId = gatewayOrderId;
        this.amount = amount;
        this.status = status;
        this.gatewayName = gatewayName;
        this.createdAt = createdAt;
    }
}
