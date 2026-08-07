package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItemResponse {
    private UUID id;
    private UUID menuItemId;
    private String name;
    private Integer quantity;
    private BigDecimal price;


    @java.lang.SuppressWarnings("all")
    public static class OrderItemResponseBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID id;
        @java.lang.SuppressWarnings("all")
        private UUID menuItemId;
        @java.lang.SuppressWarnings("all")
        private String name;
        @java.lang.SuppressWarnings("all")
        private Integer quantity;
        @java.lang.SuppressWarnings("all")
        private BigDecimal price;

        @java.lang.SuppressWarnings("all")
        OrderItemResponseBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItemResponse.OrderItemResponseBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItemResponse.OrderItemResponseBuilder menuItemId(final UUID menuItemId) {
            this.menuItemId = menuItemId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItemResponse.OrderItemResponseBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItemResponse.OrderItemResponseBuilder quantity(final Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItemResponse.OrderItemResponseBuilder price(final BigDecimal price) {
            this.price = price;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public OrderItemResponse build() {
            return new OrderItemResponse(this.id, this.menuItemId, this.name, this.quantity, this.price);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "OrderItemResponse.OrderItemResponseBuilder(id=" + this.id + ", menuItemId=" + this.menuItemId + ", name=" + this.name + ", quantity=" + this.quantity + ", price=" + this.price + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static OrderItemResponse.OrderItemResponseBuilder builder() {
        return new OrderItemResponse.OrderItemResponseBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
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
    public void setId(final UUID id) {
        this.id = id;
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

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof OrderItemResponse)) return false;
        final OrderItemResponse other = (OrderItemResponse) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$quantity = this.getQuantity();
        final java.lang.Object other$quantity = other.getQuantity();
        if (this$quantity == null ? other$quantity != null : !this$quantity.equals(other$quantity)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$menuItemId = this.getMenuItemId();
        final java.lang.Object other$menuItemId = other.getMenuItemId();
        if (this$menuItemId == null ? other$menuItemId != null : !this$menuItemId.equals(other$menuItemId)) return false;
        final java.lang.Object this$name = this.getName();
        final java.lang.Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final java.lang.Object this$price = this.getPrice();
        final java.lang.Object other$price = other.getPrice();
        if (this$price == null ? other$price != null : !this$price.equals(other$price)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof OrderItemResponse;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $quantity = this.getQuantity();
        result = result * PRIME + ($quantity == null ? 43 : $quantity.hashCode());
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $menuItemId = this.getMenuItemId();
        result = result * PRIME + ($menuItemId == null ? 43 : $menuItemId.hashCode());
        final java.lang.Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final java.lang.Object $price = this.getPrice();
        result = result * PRIME + ($price == null ? 43 : $price.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "OrderItemResponse(id=" + this.getId() + ", menuItemId=" + this.getMenuItemId() + ", name=" + this.getName() + ", quantity=" + this.getQuantity() + ", price=" + this.getPrice() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public OrderItemResponse() {
    }

    @java.lang.SuppressWarnings("all")
    public OrderItemResponse(final UUID id, final UUID menuItemId, final String name, final Integer quantity, final BigDecimal price) {
        this.id = id;
        this.menuItemId = menuItemId;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }
}
