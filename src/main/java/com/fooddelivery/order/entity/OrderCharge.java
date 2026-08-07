package com.fooddelivery.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.order.enums.ChargeEntityType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_charges")
public class OrderCharge {
    @Id
    @Column(name = "id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private ChargeCategory category;
    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type")
    private ChargeEntityType payerType;
    @Column(name = "payer_id")
    private UUID payerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type")
    private ChargeEntityType payeeType;
    @Column(name = "payee_id")
    private UUID payeeId;
    @Column(name = "amount")
    private BigDecimal amount;
    @Column(name = "description")
    private String description;


    @java.lang.SuppressWarnings("all")
    public static class OrderChargeBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID id;
        @java.lang.SuppressWarnings("all")
        private Order order;
        @java.lang.SuppressWarnings("all")
        private ChargeCategory category;
        @java.lang.SuppressWarnings("all")
        private ChargeEntityType payerType;
        @java.lang.SuppressWarnings("all")
        private UUID payerId;
        @java.lang.SuppressWarnings("all")
        private ChargeEntityType payeeType;
        @java.lang.SuppressWarnings("all")
        private UUID payeeId;
        @java.lang.SuppressWarnings("all")
        private BigDecimal amount;
        @java.lang.SuppressWarnings("all")
        private String description;

        @java.lang.SuppressWarnings("all")
        OrderChargeBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderCharge.OrderChargeBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @JsonIgnore
        @java.lang.SuppressWarnings("all")
        public OrderCharge.OrderChargeBuilder order(final Order order) {
            this.order = order;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderCharge.OrderChargeBuilder category(final ChargeCategory category) {
            this.category = category;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderCharge.OrderChargeBuilder payerType(final ChargeEntityType payerType) {
            this.payerType = payerType;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderCharge.OrderChargeBuilder payerId(final UUID payerId) {
            this.payerId = payerId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderCharge.OrderChargeBuilder payeeType(final ChargeEntityType payeeType) {
            this.payeeType = payeeType;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderCharge.OrderChargeBuilder payeeId(final UUID payeeId) {
            this.payeeId = payeeId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderCharge.OrderChargeBuilder amount(final BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderCharge.OrderChargeBuilder description(final String description) {
            this.description = description;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public OrderCharge build() {
            return new OrderCharge(this.id, this.order, this.category, this.payerType, this.payerId, this.payeeType, this.payeeId, this.amount, this.description);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "OrderCharge.OrderChargeBuilder(id=" + this.id + ", order=" + this.order + ", category=" + this.category + ", payerType=" + this.payerType + ", payerId=" + this.payerId + ", payeeType=" + this.payeeType + ", payeeId=" + this.payeeId + ", amount=" + this.amount + ", description=" + this.description + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static OrderCharge.OrderChargeBuilder builder() {
        return new OrderCharge.OrderChargeBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public Order getOrder() {
        return this.order;
    }

    @java.lang.SuppressWarnings("all")
    public ChargeCategory getCategory() {
        return this.category;
    }

    @java.lang.SuppressWarnings("all")
    public ChargeEntityType getPayerType() {
        return this.payerType;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getPayerId() {
        return this.payerId;
    }

    @java.lang.SuppressWarnings("all")
    public ChargeEntityType getPayeeType() {
        return this.payeeType;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getPayeeId() {
        return this.payeeId;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getAmount() {
        return this.amount;
    }

    @java.lang.SuppressWarnings("all")
    public String getDescription() {
        return this.description;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @JsonIgnore
    @java.lang.SuppressWarnings("all")
    public void setOrder(final Order order) {
        this.order = order;
    }

    @java.lang.SuppressWarnings("all")
    public void setCategory(final ChargeCategory category) {
        this.category = category;
    }

    @java.lang.SuppressWarnings("all")
    public void setPayerType(final ChargeEntityType payerType) {
        this.payerType = payerType;
    }

    @java.lang.SuppressWarnings("all")
    public void setPayerId(final UUID payerId) {
        this.payerId = payerId;
    }

    @java.lang.SuppressWarnings("all")
    public void setPayeeType(final ChargeEntityType payeeType) {
        this.payeeType = payeeType;
    }

    @java.lang.SuppressWarnings("all")
    public void setPayeeId(final UUID payeeId) {
        this.payeeId = payeeId;
    }

    @java.lang.SuppressWarnings("all")
    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    @java.lang.SuppressWarnings("all")
    public void setDescription(final String description) {
        this.description = description;
    }

    @java.lang.SuppressWarnings("all")
    public OrderCharge() {
    }

    @java.lang.SuppressWarnings("all")
    public OrderCharge(final UUID id, final Order order, final ChargeCategory category, final ChargeEntityType payerType, final UUID payerId, final ChargeEntityType payeeType, final UUID payeeId, final BigDecimal amount, final String description) {
        this.id = id;
        this.order = order;
        this.category = category;
        this.payerType = payerType;
        this.payerId = payerId;
        this.payeeType = payeeType;
        this.payeeId = payeeId;
        this.amount = amount;
        this.description = description;
    }
}
