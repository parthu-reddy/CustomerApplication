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


    @java.lang.SuppressWarnings("all")
    public static class OrderItemBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID id;
        @java.lang.SuppressWarnings("all")
        private Order order;
        @java.lang.SuppressWarnings("all")
        private UUID menuItemId;
        @java.lang.SuppressWarnings("all")
        private String name;
        @java.lang.SuppressWarnings("all")
        private Integer quantity;
        @java.lang.SuppressWarnings("all")
        private BigDecimal price;
        @java.lang.SuppressWarnings("all")
        private LocalDateTime createdAt;
        @java.lang.SuppressWarnings("all")
        private LocalDateTime updatedAt;
        @java.lang.SuppressWarnings("all")
        private Integer refundedQuantity;

        @java.lang.SuppressWarnings("all")
        OrderItemBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItem.OrderItemBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItem.OrderItemBuilder order(final Order order) {
            this.order = order;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItem.OrderItemBuilder menuItemId(final UUID menuItemId) {
            this.menuItemId = menuItemId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItem.OrderItemBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItem.OrderItemBuilder quantity(final Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItem.OrderItemBuilder price(final BigDecimal price) {
            this.price = price;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItem.OrderItemBuilder createdAt(final LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItem.OrderItemBuilder updatedAt(final LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItem.OrderItemBuilder refundedQuantity(final Integer refundedQuantity) {
            this.refundedQuantity = refundedQuantity;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public OrderItem build() {
            return new OrderItem(this.id, this.order, this.menuItemId, this.name, this.quantity, this.price, this.createdAt, this.updatedAt, this.refundedQuantity);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "OrderItem.OrderItemBuilder(id=" + this.id + ", order=" + this.order + ", menuItemId=" + this.menuItemId + ", name=" + this.name + ", quantity=" + this.quantity + ", price=" + this.price + ", createdAt=" + this.createdAt + ", updatedAt=" + this.updatedAt + ", refundedQuantity=" + this.refundedQuantity + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static OrderItem.OrderItemBuilder builder() {
        return new OrderItem.OrderItemBuilder();
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
    public UUID getMenuItemId() {
        return this.menuItemId;
    }

    @java.lang.SuppressWarnings("all")
    public String getName() {
        return this.name;
    }

    @java.lang.SuppressWarnings("all")
    public Integer getQuantity() {
        return this.quantity;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getPrice() {
        return this.price;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    @java.lang.SuppressWarnings("all")
    public Integer getRefundedQuantity() {
        return this.refundedQuantity;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setOrder(final Order order) {
        this.order = order;
    }

    @java.lang.SuppressWarnings("all")
    public void setMenuItemId(final UUID menuItemId) {
        this.menuItemId = menuItemId;
    }

    @java.lang.SuppressWarnings("all")
    public void setName(final String name) {
        this.name = name;
    }

    @java.lang.SuppressWarnings("all")
    public void setQuantity(final Integer quantity) {
        this.quantity = quantity;
    }

    @java.lang.SuppressWarnings("all")
    public void setPrice(final BigDecimal price) {
        this.price = price;
    }

    @java.lang.SuppressWarnings("all")
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setRefundedQuantity(final Integer refundedQuantity) {
        this.refundedQuantity = refundedQuantity;
    }

    @java.lang.SuppressWarnings("all")
    public OrderItem() {
    }

    @java.lang.SuppressWarnings("all")
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
