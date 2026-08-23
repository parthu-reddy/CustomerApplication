package com.fooddelivery.customer.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import com.fooddelivery.common.enums.PaymentMethod;

public class OrderRequest {
    @NotNull
    private UUID customerId;
    private String customerName;
    @NotNull
    private UUID restaurantId;
    @NotNull
    private UUID deliveryAddressId;
    private PaymentMethod paymentMethod;
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;


    
    public static class OrderRequestBuilder {
        
        private UUID customerId;
        
        private String customerName;
        
        private UUID restaurantId;
        
        private UUID deliveryAddressId;
        
        private PaymentMethod paymentMethod;
        
        private List<OrderItemRequest> items;

        
        OrderRequestBuilder() {
        }

        /**
         * @return {@code this}.
         */
        
        public OrderRequest.OrderRequestBuilder customerId(final UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderRequest.OrderRequestBuilder customerName(final String customerName) {
            this.customerName = customerName;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderRequest.OrderRequestBuilder restaurantId(final UUID restaurantId) {
            this.restaurantId = restaurantId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderRequest.OrderRequestBuilder deliveryAddressId(final UUID deliveryAddressId) {
            this.deliveryAddressId = deliveryAddressId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public OrderRequest.OrderRequestBuilder items(final List<OrderItemRequest> items) {
            this.items = items;
            return this;
        }

        
        public OrderRequest build() {
            return new OrderRequest(this.customerId, this.customerName, this.restaurantId, this.deliveryAddressId, this.paymentMethod, this.items);
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "OrderRequest.OrderRequestBuilder(customerId=" + this.customerId + ", customerName=" + this.customerName + ", restaurantId=" + this.restaurantId + ", deliveryAddressId=" + this.deliveryAddressId + ", paymentMethod=" + this.paymentMethod + ", items=" + this.items + ")";
        }
    }

    
    public static OrderRequest.OrderRequestBuilder builder() {
        return new OrderRequest.OrderRequestBuilder();
    }

    
    public UUID getCustomerId() {
        return this.customerId;
    }

    
    public String getCustomerName() {
        return this.customerName;
    }

    
    public UUID getRestaurantId() {
        return this.restaurantId;
    }

    
    public UUID getDeliveryAddressId() {
        return this.deliveryAddressId;
    }

    
    public PaymentMethod getPaymentMethod() {
        return this.paymentMethod;
    }

    
    public List<OrderItemRequest> getItems() {
        return this.items;
    }

    
    public void setCustomerId(final UUID customerId) {
        this.customerId = customerId;
    }

    
    public void setCustomerName(final String customerName) {
        this.customerName = customerName;
    }

    
    public void setRestaurantId(final UUID restaurantId) {
        this.restaurantId = restaurantId;
    }

    
    public void setDeliveryAddressId(final UUID deliveryAddressId) {
        this.deliveryAddressId = deliveryAddressId;
    }

    
    public void setPaymentMethod(final PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    
    public void setItems(final List<OrderItemRequest> items) {
        this.items = items;
    }

    @java.lang.Override
    
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
        final java.lang.Object this$paymentMethod = this.getPaymentMethod();
        final java.lang.Object other$paymentMethod = other.getPaymentMethod();
        if (this$paymentMethod == null ? other$paymentMethod != null : !this$paymentMethod.equals(other$paymentMethod)) return false;
        final java.lang.Object this$items = this.getItems();
        final java.lang.Object other$items = other.getItems();
        if (this$items == null ? other$items != null : !this$items.equals(other$items)) return false;
        return true;
    }

    
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof OrderRequest;
    }

    @java.lang.Override
    
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
        final java.lang.Object $paymentMethod = this.getPaymentMethod();
        result = result * PRIME + ($paymentMethod == null ? 43 : $paymentMethod.hashCode());
        final java.lang.Object $items = this.getItems();
        result = result * PRIME + ($items == null ? 43 : $items.hashCode());
        return result;
    }

    @java.lang.Override
    
    public java.lang.String toString() {
        return "OrderRequest(customerId=" + this.getCustomerId() + ", customerName=" + this.getCustomerName() + ", restaurantId=" + this.getRestaurantId() + ", deliveryAddressId=" + this.getDeliveryAddressId() + ", paymentMethod=" + this.getPaymentMethod() + ", items=" + this.getItems() + ")";
    }

    
    public OrderRequest() {
    }

    
    public OrderRequest(final UUID customerId, final String customerName, final UUID restaurantId, final UUID deliveryAddressId, final PaymentMethod paymentMethod, final List<OrderItemRequest> items) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.restaurantId = restaurantId;
        this.deliveryAddressId = deliveryAddressId;
        this.paymentMethod = paymentMethod;
        this.items = items;
    }
}
