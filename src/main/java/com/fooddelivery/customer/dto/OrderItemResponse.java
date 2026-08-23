package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItemResponse {
    private UUID id;
    private UUID menuItemId;
    private String name;
    private Integer quantity;
    private BigDecimal price;


    
    public static class OrderItemResponseBuilder {
        
        private UUID id;
        
        private UUID menuItemId;
        
        private String name;
        
        private Integer quantity;
        
        private BigDecimal price;

        
        OrderItemResponseBuilder() {
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItemResponse.OrderItemResponseBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItemResponse.OrderItemResponseBuilder menuItemId(final UUID menuItemId) {
            this.menuItemId = menuItemId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItemResponse.OrderItemResponseBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItemResponse.OrderItemResponseBuilder quantity(final Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderItemResponse.OrderItemResponseBuilder price(final BigDecimal price) {
            this.price = price;
            return this;
        }

        
        public OrderItemResponse build() {
            return new OrderItemResponse(this.id, this.menuItemId, this.name, this.quantity, this.price);
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "OrderItemResponse.OrderItemResponseBuilder(id=" + this.id + ", menuItemId=" + this.menuItemId + ", name=" + this.name + ", quantity=" + this.quantity + ", price=" + this.price + ")";
        }
    }

    
    public static OrderItemResponse.OrderItemResponseBuilder builder() {
        return new OrderItemResponse.OrderItemResponseBuilder();
    }

    
    public UUID getId() {
        return this.id;
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

    
    public void setId(final UUID id) {
        this.id = id;
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

    @java.lang.Override
    
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

    
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof OrderItemResponse;
    }

    @java.lang.Override
    
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
    
    public java.lang.String toString() {
        return "OrderItemResponse(id=" + this.getId() + ", menuItemId=" + this.getMenuItemId() + ", name=" + this.getName() + ", quantity=" + this.getQuantity() + ", price=" + this.getPrice() + ")";
    }

    
    public OrderItemResponse() {
    }

    
    public OrderItemResponse(final UUID id, final UUID menuItemId, final String name, final Integer quantity, final BigDecimal price) {
        this.id = id;
        this.menuItemId = menuItemId;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }
}
