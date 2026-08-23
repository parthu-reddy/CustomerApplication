package com.fooddelivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @Column(name = "id")
    private UUID id;
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "menu_item_id")
    private UUID menuItemId;
    @Column(name = "name")
    private String name;
    @Column(name = "quantity")
    private Integer quantity;
    @Column(name = "price")
    private BigDecimal price;
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Column(name = "refunded_quantity")
    private Integer refundedQuantity = 0;


    
    public static class OrderItemBuilder {
        
        private UUID id;
        
        private Order order;
        
        private UUID menuItemId;
        
        private String name;
        
        private Integer quantity;
        
        private BigDecimal price;
        
        private LocalDateTime createdAt;
        
        private LocalDateTime updatedAt;
        
        private Integer refundedQuantity;

        
        OrderItemBuilder() {
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItem.OrderItemBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItem.OrderItemBuilder order(final Order order) {
            this.order = order;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItem.OrderItemBuilder menuItemId(final UUID menuItemId) {
            this.menuItemId = menuItemId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItem.OrderItemBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItem.OrderItemBuilder quantity(final Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItem.OrderItemBuilder price(final BigDecimal price) {
            this.price = price;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItem.OrderItemBuilder createdAt(final LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItem.OrderItemBuilder updatedAt(final LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItem.OrderItemBuilder refundedQuantity(final Integer refundedQuantity) {
            this.refundedQuantity = refundedQuantity;
            return this;
        }

        
        public OrderItem build() {
            return new OrderItem(this.id, this.order, this.menuItemId, this.name, this.quantity, this.price, this.createdAt, this.updatedAt, this.refundedQuantity);
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "OrderItem.OrderItemBuilder(id=" + this.id + ", order=" + this.order + ", menuItemId=" + this.menuItemId + ", name=" + this.name + ", quantity=" + this.quantity + ", price=" + this.price + ", createdAt=" + this.createdAt + ", updatedAt=" + this.updatedAt + ", refundedQuantity=" + this.refundedQuantity + ")";
        }
    }

    
    public static OrderItem.OrderItemBuilder builder() {
        return new OrderItem.OrderItemBuilder();
    }

    
    public UUID getId() {
        return this.id;
    }

    
    public Order getOrder() {
        return this.order;
    }

    
    public UUID getMenuItemId() {
        return this.menuItemId;
    }

    
    public String getName() {
        return this.name;
    }

    
    public Integer getQuantity() {
        return this.quantity;
    }

    
    public BigDecimal getPrice() {
        return this.price;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public Integer getRefundedQuantity() {
        return this.refundedQuantity;
    }

    
    public void setId(final UUID id) {
        this.id = id;
    }

    
    public void setOrder(final Order order) {
        this.order = order;
    }

    
    public void setMenuItemId(final UUID menuItemId) {
        this.menuItemId = menuItemId;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setQuantity(final Integer quantity) {
        this.quantity = quantity;
    }

    
    public void setPrice(final BigDecimal price) {
        this.price = price;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    
    public void setRefundedQuantity(final Integer refundedQuantity) {
        this.refundedQuantity = refundedQuantity;
    }

    
    public OrderItem() {
    }

    
    public OrderItem(final UUID id, final Order order, final UUID menuItemId, final String name, final Integer quantity, final BigDecimal price, final LocalDateTime createdAt, final LocalDateTime updatedAt, final Integer refundedQuantity) {
        this.id = id;
        this.order = order;
        this.menuItemId = menuItemId;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.refundedQuantity = refundedQuantity;
    }
}
