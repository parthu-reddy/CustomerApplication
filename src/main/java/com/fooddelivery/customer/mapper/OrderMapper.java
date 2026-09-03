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
        BigDecimal sgst = order.getSgst() != null ? order.getSgst() : BigDecimal.ZERO;
        BigDecimal cgst = order.getCgst() != null ? order.getCgst() : BigDecimal.ZERO;
        BigDecimal deliveryFee = order.getDeliveryFee() != null ? order.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal customerPlatformFee = order.getCustomerPlatformFee() != null ? order.getCustomerPlatformFee() : BigDecimal.ZERO;
        BigDecimal restaurantPlatformFee = order.getRestaurantPlatformFee() != null ? order.getRestaurantPlatformFee() : BigDecimal.ZERO;
        BigDecimal platformBonus = order.getPlatformBonus() != null ? order.getPlatformBonus() : BigDecimal.ZERO;
        BigDecimal restaurantDeliveryContribution = order.getRestaurantDeliveryContribution() != null ? order.getRestaurantDeliveryContribution() : BigDecimal.ZERO;
        BigDecimal restaurantPayout = order.getRestaurantPayout() != null ? order.getRestaurantPayout() : BigDecimal.ZERO;
        BigDecimal driverGrossPayout = order.getDriverGrossPayout() != null ? order.getDriverGrossPayout() : BigDecimal.ZERO;
        BigDecimal driverTaxes = order.getDriverTaxes() != null ? order.getDriverTaxes() : BigDecimal.ZERO;
        BigDecimal driverNetPayout = order.getDriverNetPayout() != null ? order.getDriverNetPayout() : BigDecimal.ZERO;

        return OrderResponse.builder()
            .id(order.getId())
            .customerId(order.getCustomerId())
            .restaurantId(order.getRestaurantId())
            .restaurantName(order.getRestaurantName())
            .status(order.getStatus())
            .deliveryStatus(order.getDeliveryStatus())
            .totalAmount(order.getTotalAmount())
            .itemTotal(itemTotal)
            .foodCost(calculatedItemTotal)
            .customerPlatformFee(customerPlatformFee)
            .restaurantPlatformFee(restaurantPlatformFee)
            .platformBonus(platformBonus)
            .restaurantDeliveryContribution(restaurantDeliveryContribution)
            .restaurantPayout(restaurantPayout)
            .sgst(sgst)
            .cgst(cgst)
            .deliveryFee(deliveryFee)
            .driverGrossPayout(driverGrossPayout)
            .driverTaxes(driverTaxes)
            .driverNetPayout(driverNetPayout)
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
            .build();
    }
}
