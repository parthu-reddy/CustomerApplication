package com.fooddelivery.customer.dto;

import java.util.UUID;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class OrderItemRequest {
    @NotNull
    private UUID menuItemId;
    @NotNull
    @Min(1)
    private Integer quantity;


    @java.lang.SuppressWarnings("all")
    public static class OrderItemRequestBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID menuItemId;
        @java.lang.SuppressWarnings("all")
        private Integer quantity;

        @java.lang.SuppressWarnings("all")
        OrderItemRequestBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItemRequest.OrderItemRequestBuilder menuItemId(final UUID menuItemId) {
            this.menuItemId = menuItemId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderItemRequest.OrderItemRequestBuilder quantity(final Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public OrderItemRequest build() {
            return new OrderItemRequest(this.menuItemId, this.quantity);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "OrderItemRequest.OrderItemRequestBuilder(menuItemId=" + this.menuItemId + ", quantity=" + this.quantity + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static OrderItemRequest.OrderItemRequestBuilder builder() {
        return new OrderItemRequest.OrderItemRequestBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getMenuItemId() {
        return this.menuItemId;
    }

    @java.lang.SuppressWarnings("all")
    public Integer getQuantity() {
        return this.quantity;
    }

    @java.lang.SuppressWarnings("all")
    public void setMenuItemId(final UUID menuItemId) {
        this.menuItemId = menuItemId;
    }

    @java.lang.SuppressWarnings("all")
    public void setQuantity(final Integer quantity) {
        this.quantity = quantity;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof OrderItemRequest)) return false;
        final OrderItemRequest other = (OrderItemRequest) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$quantity = this.getQuantity();
        final java.lang.Object other$quantity = other.getQuantity();
        if (this$quantity == null ? other$quantity != null : !this$quantity.equals(other$quantity)) return false;
        final java.lang.Object this$menuItemId = this.getMenuItemId();
        final java.lang.Object other$menuItemId = other.getMenuItemId();
        if (this$menuItemId == null ? other$menuItemId != null : !this$menuItemId.equals(other$menuItemId)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof OrderItemRequest;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $quantity = this.getQuantity();
        result = result * PRIME + ($quantity == null ? 43 : $quantity.hashCode());
        final java.lang.Object $menuItemId = this.getMenuItemId();
        result = result * PRIME + ($menuItemId == null ? 43 : $menuItemId.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "OrderItemRequest(menuItemId=" + this.getMenuItemId() + ", quantity=" + this.getQuantity() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public OrderItemRequest() {
    }

    @java.lang.SuppressWarnings("all")
    public OrderItemRequest(final UUID menuItemId, final Integer quantity) {
        this.menuItemId = menuItemId;
        this.quantity = quantity;
    }
}
