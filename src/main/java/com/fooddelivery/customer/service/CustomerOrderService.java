package com.fooddelivery.customer.service;

import com.fooddelivery.customer.dto.OrderItemRequest;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.common.enums.OrderStatus;
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
    
    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(50);

    @jakarta.annotation.PreDestroy
    public void cleanup() {
        executorService.shutdown();
    }
    private final StringRedisTemplate redisTemplate;
    private final PaymentGatewayOrchestrator paymentGatewayOrchestrator;
    private final com.fooddelivery.customer.repository.CustomerAddressRepository addressRepository;
    
    // Using a new RestTemplate for now
    private final RestTemplate restTemplate;
    
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;

    @org.springframework.beans.factory.annotation.Value("${restaurant-service.base-url:http://localhost:8094}")
    private String RESTAURANT_SERVICE_URL;

    @org.springframework.beans.factory.annotation.Value("${maps-service.base-url:http://localhost:8083}")
    private String MAPS_SERVICE_URL;

    public record OrderWithPayment(Order order, String paymentIntent) {}

    // DTOs for REST calls
    public record RestaurantDTO(UUID id, String name, Boolean isActive, Double lat, Double lng) {}
    public record MenuItemDTO(UUID id, UUID restaurantId, String name, BigDecimal price, Boolean isAvailable, Integer prepTimeMinutes) {}

    public OrderWithPayment createOrderWithPayment(UUID customerId, UUID restaurantId, UUID deliveryAddressId, List<OrderItemRequest> requestedItems) {
        Order order = createOrder(customerId, restaurantId, deliveryAddressId, requestedItems);
        
        try {
            String intent = paymentGatewayOrchestrator.generateUpiIntent(order);
            return new OrderWithPayment(order, intent);
        } catch (Exception e) {
            // COMPENSATION: The order was already committed to DB via startOrderSaga().
            // The Outbox already has an ORDER_CREATED event queued. We must explicitly
            // cancel the order and publish an ORDER_CANCELLED event to prevent a phantom
            // order from propagating through the system.
            log.error("PAYMENT_INTENT_FAILURE: Payment intent generation failed for Order {}. " +
                    "Compensating by cancelling order. CustomerId={}, RestaurantId={}, TotalAmount={}",
                    order.getId(), customerId, restaurantId, order.getTotalAmount(), e);
            
            transactionTemplate.executeWithoutResult(status -> {
                Order freshOrder = orderRepository.findById(order.getId()).orElse(null);
                if (freshOrder != null && freshOrder.getStatus() == OrderStatus.CREATED) {
                    freshOrder.setStatus(OrderStatus.CANCELLED);
                    freshOrder.setCancellationReason("Payment intent generation failed: " + e.getMessage());
                    orderRepository.save(freshOrder);
                    
                    // Insert compensation event into outbox so downstream consumers
                    // (restaurant, delivery) know this order is dead on arrival
                    com.fooddelivery.common.outbox.entity.OutboxEventEntity cancelEvent = com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder()
                            .id(UUID.randomUUID())
                            .aggregateType(com.fooddelivery.common.constants.AppConstants.AGGREGATE_ORDER)
                            .aggregateId(freshOrder.getId().toString())
                            .eventType(com.fooddelivery.common.constants.EventType.ORDER_CANCELLED)
                            .payload(String.format("{\"eventType\":\"ORDER_CANCELLED\", \"orderId\":\"%s\", \"reason\":\"Payment intent generation failed\"}", freshOrder.getId()))
                            .createdAt(java.time.LocalDateTime.now())
                            .build();
                    outboxEventRepository.save(cancelEvent);
                    
                    log.info("COMPENSATION_COMPLETE: Order {} cancelled and ORDER_CANCELLED outbox event saved.", freshOrder.getId());
                } else {
                    log.error("COMPENSATION_SKIPPED: Order {} not found or not in CREATED state (status={}). Cannot compensate.",
                            order.getId(), freshOrder != null ? freshOrder.getStatus() : "NOT_FOUND");
                }
            });
            
            throw new RuntimeException("Payment intent generation failed for order " + order.getId(), e);
        }
    }

    protected Order createOrder(UUID customerId, UUID restaurantId, UUID deliveryAddressId, List<OrderItemRequest> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }
        if (deliveryAddressId == null) {
            throw new IllegalArgumentException("Delivery address is required.");
        }
        
        try {
            // Fetch delivery address
            com.fooddelivery.customer.entity.CustomerAddress address = addressRepository.findById(deliveryAddressId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery address not found"));
            
            if (!address.getCustomerId().equals(customerId)) {
                throw new IllegalArgumentException("Address does not belong to customer");
            }

            for (OrderItemRequest req : requestedItems) {
                if (req.getQuantity() == null || req.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Quantity must be strictly positive");
                }
            }

            String menuIds = requestedItems.stream()
                .map(req -> req.getMenuItemId().toString())
                .collect(Collectors.joining(","));

            // 1. Launch Menu Fetch Async
            java.util.concurrent.CompletableFuture<ResponseEntity<List<MenuItemDTO>>> menuFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> 
                restTemplate.exchange(
                    RESTAURANT_SERVICE_URL + "/api/v1/restaurants/" + restaurantId + "/menu/batch?ids=" + menuIds,
                    HttpMethod.GET, null, new ParameterizedTypeReference<List<MenuItemDTO>>() {}),
                executorService
            );

            // 2. Launch Restaurant Fetch Async -> Maps Fetch Async
            java.util.concurrent.CompletableFuture<java.util.Map<String, Object>> restaurantDataFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                ResponseEntity<java.util.Map> restaurantResponse = restTemplate.getForEntity(
                    RESTAURANT_SERVICE_URL + "/api/v1/restaurants/" + restaurantId, java.util.Map.class);
                    
                if (!restaurantResponse.getStatusCode().is2xxSuccessful() || restaurantResponse.getBody() == null) {
                    throw new RuntimeException(new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + restaurantId));
                }
                
                java.util.Map<String, Object> responseBody = restaurantResponse.getBody();
                java.util.Map<String, Object> restaurantData = (java.util.Map<String, Object>) responseBody.get("data");
                
                if (restaurantData == null) {
                    throw new RuntimeException(new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + restaurantId));
                }
                
                Boolean isActive = (Boolean) restaurantData.get("isActive");
                if (isActive == null || !isActive) {
                    throw new RuntimeException(new IllegalArgumentException("Restaurant is not currently active: " + restaurantData.get("name")));
                }
                
                Double rLat = (Double) restaurantData.get("lat");
                Double rLng = (Double) restaurantData.get("lng");
                if (rLat == null || rLng == null) {
                    throw new RuntimeException(new IllegalStateException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_UNKNOWN));
                }
                
                return restaurantData;
            }, executorService);

            java.util.concurrent.CompletableFuture<Boolean> mapsFuture = restaurantDataFuture.thenApplyAsync(restaurantData -> {
                Double rLat = (Double) restaurantData.get("lat");
                Double rLng = (Double) restaurantData.get("lng");
                ResponseEntity<java.util.Map> mapsResponse = restTemplate.getForEntity(
                    MAPS_SERVICE_URL + "/api/fleet/availability/check?cityId=" + com.fooddelivery.common.constants.AppConstants.DEFAULT_CITY_ID + "&lat=" + rLat + "&lng=" + rLng + "&radius=" + com.fooddelivery.common.constants.AppConstants.MAX_DELIVERY_RADIUS_KM, java.util.Map.class);
                    
                if (mapsResponse.getStatusCode().is2xxSuccessful() && mapsResponse.getBody() != null) {
                    return Boolean.TRUE.equals(mapsResponse.getBody().get("available"));
                }
                return false;
            });

            // Wait for all async calls to complete
            try {
                java.util.concurrent.CompletableFuture.allOf(menuFuture, restaurantDataFuture, mapsFuture).join();
            } catch (java.util.concurrent.CompletionException ce) {
                if (ce.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) ce.getCause();
                }
                throw new RuntimeException(ce.getCause());
            }

            java.util.Map<String, Object> restaurantData = restaurantDataFuture.get();
            Double rLat = (Double) restaurantData.get("lat");
            Double rLng = (Double) restaurantData.get("lng");
            
            boolean hasDrivers = mapsFuture.get();
            if (!hasDrivers) {
                throw new com.fooddelivery.customer.exception.DeliveryPartnerUnavailableException(
                    com.fooddelivery.common.constants.AppConstants.ERROR_MSG_NO_DELIVERY_PARTNER_NEARBY, 
                    com.fooddelivery.common.constants.AppConstants.ERROR_NO_DELIVERY_PARTNER_NEARBY
                );
            }

            // Distance check (Haversine)
            double distance = calculateDistance(rLat, rLng, address.getLatitude(), address.getLongitude());
            if (distance > com.fooddelivery.common.constants.AppConstants.MAX_DELIVERY_RADIUS_KM) {
                throw new IllegalArgumentException("Delivery address is outside the " + com.fooddelivery.common.constants.AppConstants.MAX_DELIVERY_RADIUS_KM + "km radius. Distance: " + String.format("%.2f", distance) + " km.");
            }

            BigDecimal totalAmount = BigDecimal.ZERO;
            List<OrderItem> orderItems = new ArrayList<>();

            ResponseEntity<List<MenuItemDTO>> menuResponse = menuFuture.get();
            if (!menuResponse.getStatusCode().is2xxSuccessful() || menuResponse.getBody() == null) {
                throw new IllegalArgumentException("Failed to fetch menu items");
            }
            
            List<MenuItemDTO> fetchedItems = menuResponse.getBody();
            Map<UUID, MenuItemDTO> menuItemMap = fetchedItems.stream()
                    .collect(Collectors.toMap(MenuItemDTO::id, item -> item));

            int maxPrepTime = 15; // default minimum
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

                if (menuItem.prepTimeMinutes() != null && menuItem.prepTimeMinutes() > maxPrepTime) {
                    maxPrepTime = menuItem.prepTimeMinutes();
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
                    .deliveryAddressId(deliveryAddressId)
                    .deliveryAddress(formatAddress(address))
                    .deliveryLat(address.getLatitude())
                    .deliveryLng(address.getLongitude())
                    .status(OrderStatus.CREATED)
                    .orderItems(new ArrayList<>())
                    .estimatedPrepTimeMinutes(maxPrepTime)
                    .build();

            for (OrderItem item : orderItems) {
                item.setOrder(order);
            }
            order.setOrderItems(orderItems);
            order.setTotalAmount(totalAmount);
                    
            orderSagaOrchestrator.startOrderSaga(order);
            
            log.info("Created order {} for customer {} with total amount {}", order.getId(), customerId, totalAmount);
            return order;
        } catch (com.fooddelivery.customer.exception.DeliveryPartnerUnavailableException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create order", e);
            throw new RuntimeException("Failed to create order", e);
        }
    }

    public void handleDelayApproval(UUID orderId, boolean approved) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
            
        if (order.getStatus() != OrderStatus.AWAITING_DELAY_APPROVAL) {
            throw new IllegalStateException("Order is not awaiting delay approval. Current status: " + order.getStatus());
        }
        
        if (approved) {
            // we don't change the status, just let it stay AWAITING_DELAY_APPROVAL until restaurant sends ORDER_ACCEPTED
            transactionTemplate.executeWithoutResult(status -> {
                orderSagaOrchestrator.publishDelayApprovalEvent(order, true);
            });
        } else {
            transactionTemplate.executeWithoutResult(status -> {
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancellationReason("Customer manually rejected additional prep time request");
                orderRepository.save(order);
                orderSagaOrchestrator.publishDelayApprovalEvent(order, false);
            });
            
            // Refund the customer (makes HTTP call, so keep outside transaction)
            orderSagaOrchestrator.processRefund(order);
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // in km
    }
    
    private String formatAddress(com.fooddelivery.customer.entity.CustomerAddress address) {
        StringBuilder sb = new StringBuilder();
        if (address.getAddressLine1() != null) sb.append(address.getAddressLine1());
        if (address.getAddressLine2() != null && !address.getAddressLine2().isEmpty()) {
            sb.append(", ").append(address.getAddressLine2());
        }
        if (address.getCity() != null) sb.append(", ").append(address.getCity());
        if (address.getState() != null) sb.append(", ").append(address.getState());
        if (address.getZipCode() != null) sb.append(" - ").append(address.getZipCode());
        return sb.toString();
    }
}
