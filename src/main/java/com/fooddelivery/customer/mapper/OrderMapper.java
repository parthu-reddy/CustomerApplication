package com.fooddelivery.customer.mapper;

import com.fooddelivery.customer.dto.OrderItemResponse;
import com.fooddelivery.customer.dto.OrderResponse;
import com.fooddelivery.order.entity.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class OrderMapper {
    
    public static OrderResponse mapToResponse(Order order) {
        // a partially-loaded or newly-built Order can have a null orderItems set
        List<OrderItemResponse> itemResponses = (order.getOrderItems() == null
                ? java.util.Set.<com.fooddelivery.order.entity.OrderItem>of()
                : order.getOrderItems()).stream()
            .map(item -> OrderItemResponse.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItemId())
                .name(item.getName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .build())
            .collect(Collectors.toList());
            
        BigDecimal calculatedItemTotal = itemResponses.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal itemTotal = order.getItemTotal() != null ? order.getItemTotal() : calculatedItemTotal;
        if (order.getSgst() == null || order.getCgst() == null || order.getDeliveryFee() == null || order.getCustomerPlatformFee() == null) {
            throw new IllegalArgumentException("Order financial fee components (SGST, CGST, DeliveryFee, PlatformFee) cannot be null");
        }
        
        BigDecimal sgst = order.getSgst();
        BigDecimal cgst = order.getCgst();
        BigDecimal deliveryFee = order.getDeliveryFee();
        BigDecimal customerPlatformFee = order.getCustomerPlatformFee();

        return OrderResponse.builder()
            .id(order.getId())
            .customerId(order.getCustomerId())
            .restaurantId(order.getRestaurantId())
            .restaurantName(order.getRestaurantName())
            .customerName(order.getCustomerName())
            .status(order.getStatus())
            .deliveryStatus(order.getDeliveryStatus())
            .totalAmount(order.getTotalAmount())
            .itemTotal(itemTotal)
            .customerPlatformFee(customerPlatformFee)
            .sgst(sgst)
            .cgst(cgst)
            .deliveryFee(deliveryFee)
            .pickupOtp(order.getPickupOtp())
            .otp(order.getOtp())
            .deliveryAddress(order.getDeliveryAddress())
            .deliveryLat(order.getDeliveryLat())
            .deliveryLng(order.getDeliveryLng())
            .distanceKm(order.getDistanceKm())
            .items(itemResponses)
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .estimatedCompletionTime(order.getEstimatedCompletionTime())
            .riderId(order.getDeliveryExecutiveId())
            .deliveryExecutiveId(order.getDeliveryExecutiveId())
            .paymentMethod(order.getPaymentMethod())
            .cancellationReason(order.getCancellationReason())
            .cashCollectedAmount(order.getCashCollectedAmount())
            .build();
    }
}
