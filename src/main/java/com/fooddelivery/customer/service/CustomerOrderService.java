package com.fooddelivery.customer.service;

import com.fooddelivery.customer.dto.OrderItemRequest;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.enums.OrderStatus;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import com.fooddelivery.order.service.PaymentGatewayOrchestrator;
import com.fooddelivery.restaurant.entity.MenuItem;
import com.fooddelivery.restaurant.repository.IMenuItemRepository;
import com.fooddelivery.restaurant.repository.IRestaurantRepository;
import com.fooddelivery.restaurant.entity.Restaurant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final IMenuItemRepository menuItemRepository;
    private final StringRedisTemplate redisTemplate;
    private final IRestaurantRepository restaurantRepository;
    private final PaymentGatewayOrchestrator paymentGatewayOrchestrator;

    public record OrderWithPayment(Order order, String paymentIntent) {}

    @org.springframework.transaction.annotation.Transactional
    public OrderWithPayment createOrderWithPayment(UUID customerId, UUID restaurantId, List<OrderItemRequest> requestedItems) {
        Order order = createOrder(customerId, restaurantId, requestedItems);
        String intent = paymentGatewayOrchestrator.generateUpiIntent(order);
        return new OrderWithPayment(order, intent);
    }

    @org.springframework.transaction.annotation.Transactional
    protected Order createOrder(UUID customerId, UUID restaurantId, List<OrderItemRequest> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }
        
        // Validate restaurant exists and is active
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));
        if (!restaurant.getIsActive()) {
            throw new IllegalArgumentException("Restaurant is not currently active: " + restaurant.getName());
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest req : requestedItems) {
            if (req.getQuantity() == null || req.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be strictly positive");
            }
        }

        try {
            List<UUID> menuItemIds = requestedItems.stream()
                    .map(OrderItemRequest::getMenuItemId)
                    .toList();
            
            List<MenuItem> fetchedItems = menuItemRepository.findAllById(menuItemIds);
            java.util.Map<UUID, MenuItem> menuItemMap = fetchedItems.stream()
                    .collect(java.util.stream.Collectors.toMap(MenuItem::getId, item -> item));

            for (OrderItemRequest req : requestedItems) {
                MenuItem menuItem = menuItemMap.get(req.getMenuItemId());
                if (menuItem == null) {
                    throw new IllegalArgumentException("Menu item not found: " + req.getMenuItemId());
                }
                
                if (!menuItem.getRestaurantId().equals(restaurantId)) {
                    throw new IllegalArgumentException("Menu item does not belong to the selected restaurant.");
                }
                if (!menuItem.getIsAvailable()) {
                    throw new IllegalArgumentException("Menu item is currently unavailable: " + menuItem.getName());
                }


                BigDecimal itemTotal = menuItem.getPrice().multiply(BigDecimal.valueOf(req.getQuantity()));
                totalAmount = totalAmount.add(itemTotal);

                OrderItem orderItem = OrderItem.builder()
                        .id(UUID.randomUUID())
                        // Will set order later
                        .menuItemId(menuItem.getId())
                        .quantity(req.getQuantity())
                        .price(menuItem.getPrice())
                        .build();
                
                orderItems.add(orderItem);
            }

            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .customerId(customerId)
                    .restaurantId(restaurantId)
                    .status(OrderStatus.CREATED)
                    .orderItems(new ArrayList<>())
                    .build();

            for (OrderItem item : orderItems) {
                item.setOrder(order);
            }
            order.setOrderItems(orderItems);
            order.setTotalAmount(totalAmount);
                    
            // OrderSagaOrchestrator will save the order and the outbox event in the same transaction
            orderSagaOrchestrator.startOrderSaga(order);
            
            log.info("Created order {} for customer {} with total amount {}", order.getId(), customerId, totalAmount);
            return order;
        } catch (Exception e) {
            log.error("Failed to create order", e);
            throw e;
        }
    }
}
