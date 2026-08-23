package com.fooddelivery.customer.security;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@lombok.RequiredArgsConstructor
public class CustomerSecurityHelper {
    private final IOrderRepository orderRepository;

    public boolean isOrderOwner(UUID orderId, String userId) {
        if (userId == null) return false;
        Order order = orderRepository.findById(orderId).orElse(null);
        return order != null && order.getCustomerId().toString().equals(userId);
    }

    

}
