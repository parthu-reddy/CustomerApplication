package com.fooddelivery.customer.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

public class OrderRequest {
    @NotNull
    private UUID customerId;
    private String customerName;
    @NotNull
    private UUID restaurantId;
    @NotNull
    private UUID deliveryAddressId;
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;


    @java.lang.SuppressWarnings("all")
    public static class OrderRequestBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID customerId;
        @java.lang.SuppressWarnings("all")
        private String customerName;
        @java.lang.SuppressWarnings("all")
        private UUID restaurantId;
        @java.lang.SuppressWarnings("all")
        private UUID deliveryAddressId;
        @java.lang.SuppressWarnings("all")
        private List<OrderItemRequest> items;

        @java.lang.SuppressWarnings("all")
        OrderRequestBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderRequest.OrderRequestBuilder customerId(final UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderRequest.OrderRequestBuilder customerName(final String customerName) {
            this.customerName = customerName;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderRequest.OrderRequestBuilder restaurantId(final UUID restaurantId) {
            this.restaurantId = restaurantId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderRequest.OrderRequestBuilder deliveryAddressId(final UUID deliveryAddressId) {
            this.deliveryAddressId = deliveryAddressId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public OrderRequest.OrderRequestBuilder items(final List<OrderItemRequest> items) {
            this.items = items;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public OrderRequest build() {
            return new OrderRequest(this.customerId, this.customerName, this.restaurantId, this.deliveryAddressId, this.items);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "OrderRequest.OrderRequestBuilder(customerId=" + this.customerId + ", customerName=" + this.customerName + ", restaurantId=" + this.restaurantId + ", deliveryAddressId=" + this.deliveryAddressId + ", items=" + this.items + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static OrderRequest.OrderRequestBuilder builder() {
        return new OrderRequest.OrderRequestBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getCustomerId() {
        return this.customerId;
    }

    @java.lang.SuppressWarnings("all")
    public String getCustomerName() {
        return this.customerName;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getRestaurantId() {
        return this.restaurantId;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getDeliveryAddressId() {
        return this.deliveryAddressId;
    }

    @java.lang.SuppressWarnings("all")
    public List<OrderItemRequest> getItems() {
        return this.items;
    }

    @java.lang.SuppressWarnings("all")
    public void setCustomerId(final UUID customerId) {
        this.customerId = customerId;
    }

    @java.lang.SuppressWarnings("all")
    public void setCustomerName(final String customerName) {
        this.customerName = customerName;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestaurantId(final UUID restaurantId) {
        this.restaurantId = restaurantId;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveryAddressId(final UUID deliveryAddressId) {
        this.deliveryAddressId = deliveryAddressId;
    }

    @java.lang.SuppressWarnings("all")
    public void setItems(final List<OrderItemRequest> items) {
        this.items = items;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof OrderRequest)) return false;
        final OrderRequest other = (OrderRequest) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$customerId = this.getCustomerId();
        final java.lang.Object other$customerId = other.getCustomerId();
        if (this$customerId == null ? other$customerId != null : !this$customerId.equals(other$customerId)) return false;
        final java.lang.Object this$customerName = this.getCustomerName();
        final java.lang.Object other$customerName = other.getCustomerName();
        if (this$customerName == null ? other$customerName != null : !this$customerName.equals(other$customerName)) return false;
        final java.lang.Object this$restaurantId = this.getRestaurantId();
        final java.lang.Object other$restaurantId = other.getRestaurantId();
        if (this$restaurantId == null ? other$restaurantId != null : !this$restaurantId.equals(other$restaurantId)) return false;
        final java.lang.Object this$deliveryAddressId = this.getDeliveryAddressId();
        final java.lang.Object other$deliveryAddressId = other.getDeliveryAddressId();
        if (this$deliveryAddressId == null ? other$deliveryAddressId != null : !this$deliveryAddressId.equals(other$deliveryAddressId)) return false;
        final java.lang.Object this$items = this.getItems();
        final java.lang.Object other$items = other.getItems();
        if (this$items == null ? other$items != null : !this$items.equals(other$items)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof OrderRequest;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $customerId = this.getCustomerId();
        result = result * PRIME + ($customerId == null ? 43 : $customerId.hashCode());
        final java.lang.Object $customerName = this.getCustomerName();
        result = result * PRIME + ($customerName == null ? 43 : $customerName.hashCode());
        final java.lang.Object $restaurantId = this.getRestaurantId();
        result = result * PRIME + ($restaurantId == null ? 43 : $restaurantId.hashCode());
        final java.lang.Object $deliveryAddressId = this.getDeliveryAddressId();
        result = result * PRIME + ($deliveryAddressId == null ? 43 : $deliveryAddressId.hashCode());
        final java.lang.Object $items = this.getItems();
        result = result * PRIME + ($items == null ? 43 : $items.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "OrderRequest(customerId=" + this.getCustomerId() + ", customerName=" + this.getCustomerName() + ", restaurantId=" + this.getRestaurantId() + ", deliveryAddressId=" + this.getDeliveryAddressId() + ", items=" + this.getItems() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public OrderRequest() {
    }

    @java.lang.SuppressWarnings("all")
    public OrderRequest(final UUID customerId, final String customerName, final UUID restaurantId, final UUID deliveryAddressId, final List<OrderItemRequest> items) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.restaurantId = restaurantId;
        this.deliveryAddressId = deliveryAddressId;
        this.items = items;
    }
}
