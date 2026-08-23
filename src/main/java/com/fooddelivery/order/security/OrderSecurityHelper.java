package com.fooddelivery.order.security;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class OrderSecurityHelper {
    private final IOrderRepository orderRepository;

    public boolean isOrderParticipant(UUID orderId, String userId) {
        if (userId == null) return false;
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return false;
        
        return (order.getCustomerId() != null && order.getCustomerId().toString().equals(userId)) ||
               (order.getRestaurantId() != null && order.getRestaurantId().toString().equals(userId)) ||
               (order.getDeliveryExecutiveId() != null && order.getDeliveryExecutiveId().toString().equals(userId));
    }

    public OrderSecurityHelper(final IOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
}
