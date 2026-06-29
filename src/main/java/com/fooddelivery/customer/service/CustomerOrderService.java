package com.fooddelivery.customer.service;

import com.fooddelivery.customer.dto.OrderItemRequest;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.enums.OrderStatus;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import com.fooddelivery.order.service.PaymentGatewayOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final StringRedisTemplate redisTemplate;
    private final PaymentGatewayOrchestrator paymentGatewayOrchestrator;
    
    // Using a new RestTemplate for now
    private final RestTemplate restTemplate = new RestTemplate();
    private final String RESTAURANT_SERVICE_URL = "http://localhost:8094";

    public record OrderWithPayment(Order order, String paymentIntent) {}

    // DTOs for REST calls
    public record RestaurantDTO(UUID id, String name, Boolean isActive) {}
    public record MenuItemDTO(UUID id, UUID restaurantId, String name, BigDecimal price, Boolean isAvailable) {}

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
        
        try {
            // Validate restaurant exists and is active via REST
            ResponseEntity<RestaurantDTO> restaurantResponse = restTemplate.getForEntity(
                RESTAURANT_SERVICE_URL + "/api/v1/restaurants/" + restaurantId, RestaurantDTO.class);
                
            if (!restaurantResponse.getStatusCode().is2xxSuccessful() || restaurantResponse.getBody() == null) {
                throw new IllegalArgumentException("Restaurant not found: " + restaurantId);
            }
            
            RestaurantDTO restaurant = restaurantResponse.getBody();
            if (!restaurant.isActive()) {
                throw new IllegalArgumentException("Restaurant is not currently active: " + restaurant.name());
            }

            BigDecimal totalAmount = BigDecimal.ZERO;
            List<OrderItem> orderItems = new ArrayList<>();

            for (OrderItemRequest req : requestedItems) {
                if (req.getQuantity() == null || req.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Quantity must be strictly positive");
                }
            }

            // Fetch menu items via REST
            String menuIds = requestedItems.stream()
                .map(req -> req.getMenuItemId().toString())
                .collect(Collectors.joining(","));
                
            ResponseEntity<List<MenuItemDTO>> menuResponse = restTemplate.exchange(
                RESTAURANT_SERVICE_URL + "/api/v1/restaurants/" + restaurantId + "/menu/batch?ids=" + menuIds,
                HttpMethod.GET, null, new ParameterizedTypeReference<List<MenuItemDTO>>() {});

            if (!menuResponse.getStatusCode().is2xxSuccessful() || menuResponse.getBody() == null) {
                throw new IllegalArgumentException("Failed to fetch menu items");
            }
            
            List<MenuItemDTO> fetchedItems = menuResponse.getBody();
            Map<UUID, MenuItemDTO> menuItemMap = fetchedItems.stream()
                    .collect(Collectors.toMap(MenuItemDTO::id, item -> item));

            for (OrderItemRequest req : requestedItems) {
                MenuItemDTO menuItem = menuItemMap.get(req.getMenuItemId());
                if (menuItem == null) {
                    throw new IllegalArgumentException("Menu item not found: " + req.getMenuItemId());
                }
                
                if (!menuItem.restaurantId().equals(restaurantId)) {
                    throw new IllegalArgumentException("Menu item does not belong to the selected restaurant.");
                }
                if (!menuItem.isAvailable()) {
                    throw new IllegalArgumentException("Menu item is currently unavailable: " + menuItem.name());
                }

                BigDecimal itemTotal = menuItem.price().multiply(BigDecimal.valueOf(req.getQuantity()));
                totalAmount = totalAmount.add(itemTotal);

                OrderItem orderItem = OrderItem.builder()
                        .id(UUID.randomUUID())
                        .menuItemId(menuItem.id())
                        .quantity(req.getQuantity())
                        .price(menuItem.price())
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
                    
            orderSagaOrchestrator.startOrderSaga(order);
            
            log.info("Created order {} for customer {} with total amount {}", order.getId(), customerId, totalAmount);
            return order;
        } catch (Exception e) {
            log.error("Failed to create order", e);
            throw new RuntimeException("Failed to create order", e);
        }
    }
}
