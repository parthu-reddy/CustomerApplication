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


    
    public static class OrderItemRequestBuilder {
        
        private UUID menuItemId;
        
        private Integer quantity;

        
        OrderItemRequestBuilder() {
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItemRequest.OrderItemRequestBuilder menuItemId(final UUID menuItemId) {
            this.menuItemId = menuItemId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItemRequest.OrderItemRequestBuilder quantity(final Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        
        public OrderItemRequest build() {
            return new OrderItemRequest(this.menuItemId, this.quantity);
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "OrderItemRequest.OrderItemRequestBuilder(menuItemId=" + this.menuItemId + ", quantity=" + this.quantity + ")";
        }
    }

    
    public static OrderItemRequest.OrderItemRequestBuilder builder() {
        return new OrderItemRequest.OrderItemRequestBuilder();
    }

    
    public UUID getMenuItemId() {
        return this.menuItemId;
    }

    
    public Integer getQuantity() {
        return this.quantity;
    }

    
    public void setMenuItemId(final UUID menuItemId) {
        this.menuItemId = menuItemId;
    }

    
    public void setQuantity(final Integer quantity) {
        this.quantity = quantity;
    }

    @java.lang.Override
    
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

    
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof OrderItemRequest;
    }

    @java.lang.Override
    
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
    
    public java.lang.String toString() {
        return "OrderItemRequest(menuItemId=" + this.getMenuItemId() + ", quantity=" + this.getQuantity() + ")";
    }

    
    public OrderItemRequest() {
    }

    
    public OrderItemRequest(final UUID menuItemId, final Integer quantity) {
        this.menuItemId = menuItemId;
        this.quantity = quantity;
    }
}
