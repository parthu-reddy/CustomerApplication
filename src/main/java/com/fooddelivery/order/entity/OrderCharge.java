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


    
    public static class OrderChargeBuilder {
        
        private UUID id;
        
        private Order order;
        
        private ChargeCategory category;
        
        private ChargeEntityType payerType;
        
        private UUID payerId;
        
        private ChargeEntityType payeeType;
        
        private UUID payeeId;
        
        private BigDecimal amount;
        
        private String description;

        
        OrderChargeBuilder() {
        }

        /**
         * @return {@code this}.
         */
        
        public OrderCharge.OrderChargeBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @JsonIgnore
        
        public OrderCharge.OrderChargeBuilder order(final Order order) {
            this.order = order;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderCharge.OrderChargeBuilder category(final ChargeCategory category) {
            this.category = category;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderCharge.OrderChargeBuilder payerType(final ChargeEntityType payerType) {
            this.payerType = payerType;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderCharge.OrderChargeBuilder payerId(final UUID payerId) {
            this.payerId = payerId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderCharge.OrderChargeBuilder payeeType(final ChargeEntityType payeeType) {
            this.payeeType = payeeType;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderCharge.OrderChargeBuilder payeeId(final UUID payeeId) {
            this.payeeId = payeeId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderCharge.OrderChargeBuilder amount(final BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderCharge.OrderChargeBuilder description(final String description) {
            this.description = description;
            return this;
        }

        
        public OrderCharge build() {
            return new OrderCharge(this.id, this.order, this.category, this.payerType, this.payerId, this.payeeType, this.payeeId, this.amount, this.description);
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "OrderCharge.OrderChargeBuilder(id=" + this.id + ", order=" + this.order + ", category=" + this.category + ", payerType=" + this.payerType + ", payerId=" + this.payerId + ", payeeType=" + this.payeeType + ", payeeId=" + this.payeeId + ", amount=" + this.amount + ", description=" + this.description + ")";
        }
    }

    
    public static OrderCharge.OrderChargeBuilder builder() {
        return new OrderCharge.OrderChargeBuilder();
    }

    
    public UUID getId() {
        return this.id;
    }

    
    public Order getOrder() {
        return this.order;
    }

    
    public ChargeCategory getCategory() {
        return this.category;
    }

    
    public ChargeEntityType getPayerType() {
        return this.payerType;
    }

    
    public UUID getPayerId() {
        return this.payerId;
    }

    
    public ChargeEntityType getPayeeType() {
        return this.payeeType;
    }

    
    public UUID getPayeeId() {
        return this.payeeId;
    }

    
    public BigDecimal getAmount() {
        return this.amount;
    }

    
    public String getDescription() {
        return this.description;
    }

    
    public void setId(final UUID id) {
        this.id = id;
    }

    @JsonIgnore
    
    public void setOrder(final Order order) {
        this.order = order;
    }

    
    public void setCategory(final ChargeCategory category) {
        this.category = category;
    }

    
    public void setPayerType(final ChargeEntityType payerType) {
        this.payerType = payerType;
    }

    
    public void setPayerId(final UUID payerId) {
        this.payerId = payerId;
    }

    
    public void setPayeeType(final ChargeEntityType payeeType) {
        this.payeeType = payeeType;
    }

    
    public void setPayeeId(final UUID payeeId) {
        this.payeeId = payeeId;
    }

    
    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    
    public void setDescription(final String description) {
        this.description = description;
    }

    
    public OrderCharge() {
    }

    
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
