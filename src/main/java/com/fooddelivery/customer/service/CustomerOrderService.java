package com.fooddelivery.customer.service;

import com.fooddelivery.customer.dto.OrderItemRequest;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import com.fooddelivery.order.service.PaymentGatewayOrchestrator;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@lombok.extern.slf4j.Slf4j
public class CustomerOrderService {
    

    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(50);

    @jakarta.annotation.PreDestroy
    public void cleanup() {
        executorService.shutdown();
    }

    private final StringRedisTemplate redisTemplate;
    private final com.fooddelivery.common.lock.RedisLock redisLock;
    private final PaymentGatewayOrchestrator paymentGatewayOrchestrator;
    private final com.fooddelivery.customer.repository.CustomerAddressRepository addressRepository;
    private final com.fooddelivery.customer.client.RestaurantClient restaurantClient;
    private final com.fooddelivery.common.client.MapsServiceClient mapsClient;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;
    private final DynamicPricingService dynamicPricingService;
    private final com.fooddelivery.common.client.WalletServiceClient walletServiceClient;


    public record OrderWithPayment(Order order, String paymentIntent) {
    }

    // DTOs for REST calls
    public record RestaurantDTO(UUID id, String name, Boolean isActive, Double lat, Double lng) {
    }


    public record MenuItemDTO(UUID id, UUID restaurantId, String name, BigDecimal price, Boolean isAvailable, Integer prepTimeMinutes) {
    }

    public List<Order> getOrdersByCustomer(UUID customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    private static final List<com.fooddelivery.common.enums.OrderStatus> REFUND_STATUSES = List.of(com.fooddelivery.common.enums.OrderStatus.CANCELLED, com.fooddelivery.common.enums.OrderStatus.CANCELLED_BY_RESTAURANT, com.fooddelivery.common.enums.OrderStatus.CANCELLED_BY_RESTAURANT);

    public org.springframework.data.domain.Page<Order> getActiveOrdersPaginated(UUID customerId, org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findActiveOrdersForCustomer(customerId, REFUND_STATUSES, java.util.List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED, com.fooddelivery.common.enums.DeliveryStatus.FAILED, com.fooddelivery.common.enums.DeliveryStatus.CANCELLED), pageable);
    }

    public org.springframework.data.domain.Page<Order> getRefundOrdersPaginated(UUID customerId, org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByCustomerIdAndStatusInOrderByCreatedAtDesc(customerId, REFUND_STATUSES, pageable);
    }

    public org.springframework.data.domain.Page<Order> getOrderHistoryPaginated(UUID customerId, org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findHistoryOrdersForCustomer(customerId, REFUND_STATUSES, java.util.List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED, com.fooddelivery.common.enums.DeliveryStatus.FAILED, com.fooddelivery.common.enums.DeliveryStatus.CANCELLED), pageable);
    }

    public Order getOrderByIdAndCustomer(UUID orderId, UUID customerId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId).orElseThrow(() -> new RuntimeException("Order not found or access denied"));
    }

    public Order getOrderById(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public List<Order> getOrdersByIdsAndCustomer(List<UUID> orderIds, UUID customerId) {
        return orderRepository.findByIdInAndCustomerId(orderIds, customerId);
    }

    @io.micrometer.observation.annotation.Observed(name = "process.order", contextualName = "order-creation")
    public java.util.concurrent.CompletableFuture<OrderWithPayment> createOrderWithPayment(com.fooddelivery.customer.dto.OrderRequest request) {
        return createOrder(request).thenApply(order -> {
            try {
                com.fooddelivery.common.enums.PaymentMethod method = request.getPaymentMethod();
                if (method == null) {
                    method = com.fooddelivery.common.enums.PaymentMethod.WALLET; // fallback
                }
                String intent = paymentGatewayOrchestrator.generateIntent(order, method);

                if (method == com.fooddelivery.common.enums.PaymentMethod.WALLET || method == com.fooddelivery.common.enums.PaymentMethod.COD) {
                    if (method == com.fooddelivery.common.enums.PaymentMethod.WALLET) {
                        com.fooddelivery.common.dto.wallet.TransactionRequest txReq = new com.fooddelivery.common.dto.wallet.TransactionRequest();
                        txReq.setAmount(order.getTotalAmount());
                        txReq.setReferenceId(intent);
                        txReq.setDescription("Order " + order.getId());
                        walletServiceClient.debit("CUSTOMER", order.getCustomerId(), txReq, "CustomerService");
                    }
                    String payload = String.format("{\"eventType\":\"PAYMENT_COMPLETED\", \"orderId\":\"%s\", \"gatewayOrderId\":\"%s\"}", order.getId(), intent);
                    com.fooddelivery.common.outbox.entity.OutboxEventEntity evt = com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.PAYMENT).aggregateId(intent).eventType(com.fooddelivery.common.constants.EventType.PAYMENT_COMPLETED).payload(payload).createdAt(java.time.LocalDateTime.now()).build();
                    outboxEventRepository.save(evt);
                    log.info("Saved PAYMENT_COMPLETED event for order: {}", order.getId());
                }

                return new OrderWithPayment(order, intent);
            } catch (Exception e) {
                // COMPENSATION: The order was already committed to DB via startOrderSaga().
                // The Outbox already has an ORDER_CREATED event queued. We must explicitly
                // cancel the order and publish an ORDER_CANCELLED event to prevent a phantom
                // order from propagating through the system.
                log.error("PAYMENT_INTENT_FAILURE: Payment intent generation failed for Order {}. " + "Compensating by cancelling order. CustomerId={}, RestaurantId={}, TotalAmount={}", order.getId(), request.getCustomerId(), request.getRestaurantId(), order.getTotalAmount(), e);
                transactionTemplate.executeWithoutResult(status -> {
                    Order freshOrder = orderRepository.findById(order.getId()).orElse(null);
                    if (freshOrder != null && freshOrder.getStatus() == OrderStatus.CREATED) {
                        freshOrder.setStatus(OrderStatus.CANCELLED);
                        freshOrder.setCancellationReason("Payment intent generation failed: " + e.getMessage());
                        orderRepository.save(freshOrder);
                        // Insert compensation event into outbox so downstream consumers
                        // (restaurant, delivery) know this order is dead on arrival
                        com.fooddelivery.common.outbox.entity.OutboxEventEntity cancelEvent = com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(freshOrder.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.ORDER_CANCELLED).payload(String.format("{\"eventType\":\"ORDER_CANCELLED\", \"orderId\":\"%s\", \"reason\":\"Payment intent generation failed\"}", freshOrder.getId())).createdAt(java.time.LocalDateTime.now()).build();
                        log.info("Triggering event: {} for order: {}", com.fooddelivery.common.constants.EventType.ORDER_CANCELLED.name(), freshOrder.getId());
                        outboxEventRepository.save(cancelEvent);
                        log.info("COMPENSATION_COMPLETE: Order {} cancelled and ORDER_CANCELLED outbox event saved.", freshOrder.getId());
                    } else {
                        log.error("COMPENSATION_SKIPPED: Order {} not found or not in CREATED state (status={}). Cannot compensate.", order.getId(), freshOrder != null ? freshOrder.getStatus() : "NOT_FOUND");
                    }
                });
                throw new java.util.concurrent.CompletionException(new RuntimeException("Payment intent generation failed for order " + order.getId(), e));
            }
        });
    }

    protected java.util.concurrent.CompletableFuture<Order> createOrder(com.fooddelivery.customer.dto.OrderRequest request) {
        UUID customerId = request.getCustomerId();
        UUID restaurantId = request.getRestaurantId();
        UUID deliveryAddressId = request.getDeliveryAddressId();
        List<OrderItemRequest> requestedItems = request.getItems();
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }
        if (deliveryAddressId == null) {
            throw new IllegalArgumentException("Delivery address is required.");
        }
        try {
            // Fetch delivery address
            com.fooddelivery.customer.entity.CustomerAddress address = addressRepository.findById(deliveryAddressId).orElseThrow(() -> new IllegalArgumentException("Delivery address not found"));
            if (!address.getCustomerId().equals(customerId)) {
                throw new IllegalArgumentException("Address does not belong to customer");
            }
            for (OrderItemRequest req : requestedItems) {
                if (req.getQuantity() == null || req.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Quantity must be strictly positive");
                }
            }
            String menuIds = requestedItems.stream().map(req -> req.getMenuItemId().toString()).collect(Collectors.joining(","));
            org.springframework.web.context.request.RequestAttributes requestAttributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            org.springframework.security.core.context.SecurityContext securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
            // 1. Launch Menu Fetch Async
            java.util.concurrent.CompletableFuture<List<MenuItemDTO>> menuFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(requestAttributes);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
                try {
                    return restaurantClient.getMenuItemsBatch(restaurantId, menuIds);
                } finally {
                    org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            }, executorService).orTimeout(3, java.util.concurrent.TimeUnit.SECONDS);
            // 2. Launch Restaurant Fetch Async -> Maps Fetch Async
            // 2. Launch Restaurant Fetch Async
            java.util.concurrent.CompletableFuture<java.util.Map<String, Object>> restaurantDataFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(requestAttributes);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
                try {
                    java.util.Map<String, Object> responseBody = restaurantClient.getRestaurantById(restaurantId);
                    if (responseBody == null) {
                        throw new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + restaurantId);
                    }
                    java.util.Map<String, Object> restaurantData = (java.util.Map<String, Object>) responseBody.get("data");
                    if (restaurantData == null) {
                        throw new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + restaurantId);
                    }
                    Boolean isActive = (Boolean) restaurantData.get("isActive");
                    if (isActive == null || !isActive) {
                        throw new IllegalArgumentException("Restaurant is not currently active: " + restaurantData.get("name"));
                    }
                    Boolean isOpen = (Boolean) restaurantData.get("isOpen");
                    if (isOpen != null && !isOpen) {
                        throw new IllegalArgumentException("Restaurant is currently closed for the day or shift: " + restaurantData.get("name"));
                    }
                    Double rLat = (Double) restaurantData.get("lat");
                    Double rLng = (Double) restaurantData.get("lng");
                    if (rLat == null || rLng == null) {
                        throw new IllegalStateException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_UNKNOWN);
                    }
                    return restaurantData;
                } finally {
                    org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            }, executorService).orTimeout(3, java.util.concurrent.TimeUnit.SECONDS);
            // 3. Launch Maps Fetch Async
            java.util.concurrent.CompletableFuture<Boolean> mapsFuture = restaurantDataFuture.thenApplyAsync(restaurantData -> {
                org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(requestAttributes);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
                try {
                    Double rLat = (Double) restaurantData.get("lat");
                    Double rLng = (Double) restaurantData.get("lng");
                    try {
                        java.util.Map<String, Object> mapsResponse = mapsClient.checkFleetAvailability("BLR", rLat, rLng, com.fooddelivery.common.constants.AppConstants.MAX_DELIVERY_RADIUS_KM);
                        if (mapsResponse != null) {
                            return Boolean.TRUE.equals(mapsResponse.get("available"));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to reach MapsIntegration for fleet check: {}", e.getMessage());
                    }
                    return false;
                } finally {
                    org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            }, executorService).orTimeout(3, java.util.concurrent.TimeUnit.SECONDS);
            // Wait for all async calls to complete non-blockingly
            return java.util.concurrent.CompletableFuture.allOf(menuFuture, restaurantDataFuture, mapsFuture).orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).thenApplyAsync(v -> {
                try {
                    java.util.Map<String, Object> restaurantData = restaurantDataFuture.join();
                    Double rLat = (Double) restaurantData.get("lat");
                    Double rLng = (Double) restaurantData.get("lng");
                    boolean hasDrivers = mapsFuture.join();
                    if (!hasDrivers) {
                        throw new com.fooddelivery.customer.exception.DeliveryPartnerUnavailableException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_NO_DELIVERY_PARTNER_NEARBY, com.fooddelivery.common.constants.AppConstants.ERROR_NO_DELIVERY_PARTNER_NEARBY);
                    }
                    double distance = resolveDistanceKm(address, request.getRestaurantId(), rLat, rLng);
                    if (distance > 7.0) {
                        throw new IllegalArgumentException("The restaurant is too far away (over 7km). Please select a closer restaurant.");
                    }
                    
                    BigDecimal totalAmount = BigDecimal.ZERO;
                    Set<OrderItem> orderItems = new HashSet<>();
                    List<MenuItemDTO> fetchedItems = menuFuture.get();
                    if (fetchedItems == null) {
                        throw new IllegalArgumentException("Failed to fetch menu items");
                    }
                    Map<UUID, MenuItemDTO> menuItemMap = fetchedItems.stream().collect(Collectors.toMap(MenuItemDTO::id, item -> item));
                    List<UUID> unavailableItemIds = new ArrayList<>();
                    for (OrderItemRequest req : requestedItems) {
                        MenuItemDTO menuItem = menuItemMap.get(req.getMenuItemId());
                        if (menuItem == null || !menuItem.restaurantId().equals(restaurantId) || !menuItem.isAvailable()) {
                            unavailableItemIds.add(req.getMenuItemId());
                        }
                    }
                    if (!unavailableItemIds.isEmpty()) {
                        throw new com.fooddelivery.customer.exception.MenuItemsUnavailableException("Some menu items are currently unavailable or not found.", unavailableItemIds);
                    }
                    int maxPrepTime = 15; // default minimum
                    for (OrderItemRequest req : requestedItems) {
                        MenuItemDTO menuItem = menuItemMap.get(req.getMenuItemId());
                        if (menuItem.prepTimeMinutes() != null && menuItem.prepTimeMinutes() > maxPrepTime) {
                            maxPrepTime = menuItem.prepTimeMinutes();
                        }
                        BigDecimal itemTotal = menuItem.price().multiply(BigDecimal.valueOf(req.getQuantity()));
                        totalAmount = totalAmount.add(itemTotal);
                        OrderItem orderItem = OrderItem.builder().id(UUID.randomUUID()).menuItemId(menuItem.id()).name(menuItem.name()).quantity(req.getQuantity()).price(menuItem.price()).build();
                        orderItems.add(orderItem);
                    }
                    Order order = Order.builder().id(UUID.randomUUID()).customerId(customerId).customerName(request.getCustomerName()).restaurantId(restaurantId).restaurantName((String) restaurantData.get("name")).deliveryAddressId(deliveryAddressId).deliveryAddress(formatAddress(address)).deliveryLat(address.getLatitude()).deliveryLng(address.getLongitude()).otp(String.format("%06d", new java.security.SecureRandom().nextInt(1000000))).status(OrderStatus.CREATED).orderItems(new HashSet<>()).estimatedPrepTimeMinutes(maxPrepTime).build();
                    for (OrderItem item : orderItems) {
                        item.setOrder(order);
                    }
                    order.setOrderItems(orderItems);
                    // We no longer add the restaurant's fixed delivery fee. We use dynamic pricing below.
                    // Call the dynamic pricing service
                    com.fooddelivery.customer.model.PricingBreakdown pricing = dynamicPricingService.calculatePricing(totalAmount,  // Treat the whole subtotal + existing delivery fee as the base cost
                    new BigDecimal(String.valueOf(distance)));
                    // totalAmount sent to payment gateway now explicitly includes the calculated customerDeliveryFee, SGST, and CGST instead of the old fixed fee
                    // We should overwrite totalAmount here so the customer pays exactly Food Cost + Customer Delivery Fee + Taxes
                    totalAmount = totalAmount.add(pricing.getTotalCustomerDeliveryFee()).add(pricing.getSgst()).add(pricing.getCgst());
                    order.setTotalAmount(totalAmount);
                    order.setItemTotal(pricing.getItemTotal());
                    order.setCustomerPlatformFee(pricing.getCustomerPlatformFee());
                    order.setRestaurantPlatformFee(pricing.getRestaurantPlatformFee());
                    order.setPlatformBonus(pricing.getPlatformBonus());
                    order.setRestaurantDeliveryContribution(pricing.getRestaurantDeliveryContribution());
                    order.setRestaurantPayout(pricing.getRestaurantPayout());
                    order.setDeliveryFee(pricing.getDeliveryFee());
                    order.setDriverGrossPayout(pricing.getDriverGrossPayout());
                    order.setDriverTaxes(pricing.getDriverTaxes());
                    order.setDriverNetPayout(pricing.getDriverNetPayout());
                    order.setSgst(pricing.getSgst());
                    order.setCgst(pricing.getCgst());
                    
                    order.setDistanceKm(new BigDecimal(String.valueOf(distance)));
                    for (com.fooddelivery.order.entity.OrderCharge charge : pricing.getCharges()) {
                        charge.setOrder(order);
                    }
                    order.setCharges(pricing.getCharges());
                    orderSagaOrchestrator.startOrderSaga(order);
                    log.info("Created order {} for customer {} with total amount {}", order.getId(), customerId, totalAmount);
                    return order;
                } catch (java.util.concurrent.CompletionException ce) {
                    if (ce.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) ce.getCause();
                    }
                    throw ce;
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, executorService);
        } catch (Exception e) {
            log.error("Failed to create order", e);
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
    }

    public void handleDelayApproval(UUID customerId, UUID orderId, boolean approved) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getCustomerId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to perform this action on this order");
        }
        if (order.getStatus() != OrderStatus.AWAITING_DELAY_APPROVAL) {
            throw new IllegalStateException("Order is not awaiting delay approval. Current status: " + order.getStatus());
        }
        if (approved) {
            // we don't change the status, just let it stay AWAITING_DELAY_APPROVAL until restaurant sends ORDER_ACCEPTED
            transactionTemplate.executeWithoutResult(status -> {
                orderSagaOrchestrator.publishDelayApprovalEvent(order, true, null);
            });
        } else {
            transactionTemplate.executeWithoutResult(status -> {
                orderSagaOrchestrator.publishDelayApprovalEvent(order, false, "Customer manually rejected additional prep time request");
            });
        }
    }

    public void cancelOrder(UUID customerId, UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getCustomerId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to perform this action on this order");
        }
        transactionTemplate.executeWithoutResult(status -> {
            orderSagaOrchestrator.cancelOrderLocally(order, "Cancelled by customer via UI");
        });
    }


    public java.util.concurrent.CompletableFuture<com.fooddelivery.customer.dto.QuoteResponse> calculateOrderQuote(UUID customerId, com.fooddelivery.customer.dto.QuoteRequest request) {
        UUID restaurantId = request.getRestaurantId();
        UUID deliveryAddressId = request.getDeliveryAddressId();
        final List<OrderItemRequest> requestedItems = request.getItems() == null ? java.util.Collections.emptyList() : request.getItems();
        if (deliveryAddressId == null) {
            throw new IllegalArgumentException("Delivery address is required.");
        }
        try {
            com.fooddelivery.customer.entity.CustomerAddress address = addressRepository.findById(deliveryAddressId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery address not found"));

            if (!address.getCustomerId().equals(customerId)) {
                throw new org.springframework.security.access.AccessDeniedException("Delivery address does not belong to the current customer");
            }

            String menuIds = requestedItems.stream().map(req -> req.getMenuItemId().toString()).collect(Collectors.joining(","));
            org.springframework.web.context.request.RequestAttributes requestAttributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            org.springframework.security.core.context.SecurityContext securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
            
            java.util.concurrent.CompletableFuture<List<MenuItemDTO>> menuFuture;
            if (requestedItems.isEmpty()) {
                menuFuture = java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyList());
            } else {
                menuFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(requestAttributes);
                    org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
                    try {
                        return restaurantClient.getMenuItemsBatch(restaurantId, menuIds);
                    } finally {
                        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    }
                }, executorService).orTimeout(3, java.util.concurrent.TimeUnit.SECONDS);
            }

            java.util.concurrent.CompletableFuture<java.util.Map<String, Object>> restaurantDataFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(requestAttributes);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
                try {
                    java.util.Map<String, Object> responseBody = restaurantClient.getRestaurantById(restaurantId);
                    if (responseBody == null) throw new IllegalArgumentException("Restaurant not found");
                    java.util.Map<String, Object> restaurantData = (java.util.Map<String, Object>) responseBody.get("data");
                    if (restaurantData == null) throw new IllegalArgumentException("Restaurant not found");
                    Double rLat = (Double) restaurantData.get("lat");
                    Double rLng = (Double) restaurantData.get("lng");
                    if (rLat == null || rLng == null) throw new IllegalStateException("Unknown restaurant location");
                    return restaurantData;
                } finally {
                    org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            }, executorService).orTimeout(3, java.util.concurrent.TimeUnit.SECONDS);

            return java.util.concurrent.CompletableFuture.allOf(menuFuture, restaurantDataFuture)
                .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).thenApplyAsync(v -> {
                try {
                    java.util.Map<String, Object> restaurantData = restaurantDataFuture.join();
                    Double rLat = (Double) restaurantData.get("lat");
                    Double rLng = (Double) restaurantData.get("lng");
                    Boolean isActive = (Boolean) restaurantData.get("isActive");
                    Boolean isOpen = (Boolean) restaurantData.get("isOpen");

                    if (Boolean.FALSE.equals(isActive) || Boolean.FALSE.equals(isOpen)) {
                        throw new IllegalArgumentException("Restaurant is currently offline or closed.");
                    }

                    double distance = resolveDistanceKm(address, request.getRestaurantId(), rLat, rLng);
                    if (distance > 7.0) {
                        throw new IllegalArgumentException("The restaurant is too far away (over 7km). Please select a closer restaurant.");
                    }

                    BigDecimal totalAmount = BigDecimal.ZERO;
                    if (!requestedItems.isEmpty()) {
                        List<MenuItemDTO> fetchedItems = menuFuture.get();
                        if (fetchedItems == null) throw new IllegalArgumentException("Failed to fetch menu items");
                        Map<UUID, MenuItemDTO> menuItemMap = fetchedItems.stream().collect(Collectors.toMap(MenuItemDTO::id, item -> item));
                        
                        for (OrderItemRequest req : requestedItems) {
                            MenuItemDTO menuItem = menuItemMap.get(req.getMenuItemId());
                            if (menuItem == null || !menuItem.restaurantId().equals(restaurantId) || !menuItem.isAvailable()) {
                                throw new com.fooddelivery.customer.exception.MenuItemsUnavailableException("Menu item unavailable", List.of(req.getMenuItemId()));
                            }
                            totalAmount = totalAmount.add(menuItem.price().multiply(BigDecimal.valueOf(req.getQuantity())));
                        }
                    }

                    com.fooddelivery.customer.model.PricingBreakdown pricing = dynamicPricingService.calculatePricing(totalAmount, new BigDecimal(String.valueOf(distance)));
                    
                    BigDecimal finalTotal = totalAmount.add(pricing.getTotalCustomerDeliveryFee()).add(pricing.getSgst()).add(pricing.getCgst());

                    return com.fooddelivery.customer.dto.QuoteResponse.builder()
                        .subtotal(pricing.getItemTotal())
                        .deliveryFee(pricing.getDeliveryFee())
                        .platformFee(pricing.getCustomerPlatformFee())
                        .sgst(pricing.getSgst())
                        .cgst(pricing.getCgst())
                        .total(finalTotal)
                        .minAmountForFreeDelivery(dynamicPricingService.getMinAmountForFreeDelivery(new BigDecimal(String.valueOf(distance))).orElse(null))
                        .distanceKm(new BigDecimal(String.valueOf(distance)))
                        .driverPayout(pricing.getDriverGrossPayout())
                        .restaurantDeliveryContribution(pricing.getRestaurantDeliveryContribution())
                        .build();

                } catch (java.util.concurrent.CompletionException ce) {
                    if (ce.getCause() instanceof RuntimeException) throw (RuntimeException) ce.getCause();
                    throw ce;
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, executorService);
        } catch (Exception e) {
            log.error("Failed to generate quote", e);
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
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

    public CustomerOrderService(final IOrderRepository orderRepository, final OrderSagaOrchestrator orderSagaOrchestrator, final StringRedisTemplate redisTemplate, final PaymentGatewayOrchestrator paymentGatewayOrchestrator, final com.fooddelivery.customer.repository.CustomerAddressRepository addressRepository, final com.fooddelivery.customer.client.RestaurantClient restaurantClient, final com.fooddelivery.common.client.MapsServiceClient mapsClient, final org.springframework.transaction.support.TransactionTemplate transactionTemplate, final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository, final DynamicPricingService dynamicPricingService, final com.fooddelivery.common.client.WalletServiceClient walletServiceClient, final com.fooddelivery.common.lock.RedisLock redisLock) {
        this.orderRepository = orderRepository;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.redisTemplate = redisTemplate;
        this.paymentGatewayOrchestrator = paymentGatewayOrchestrator;
        this.addressRepository = addressRepository;
        this.restaurantClient = restaurantClient;
        this.mapsClient = mapsClient;
        this.transactionTemplate = transactionTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.dynamicPricingService = dynamicPricingService;
        this.walletServiceClient = walletServiceClient;
        this.redisLock = redisLock;
    }

    private double resolveDistanceKm(
            com.fooddelivery.customer.entity.CustomerAddress address,
            UUID restaurantId,
            Double rLat,
            Double rLng) {
        String distanceCacheKey = "distance_cache:" + address.getId() + ":" + restaurantId;
        String cachedDistance = redisTemplate.opsForValue().get(distanceCacheKey);
        double distance = -1.0;
        boolean isFallback = false;
        boolean cacheHit = false;
        
        if (cachedDistance != null) {
            try {
                distance = Double.parseDouble(cachedDistance);
                cacheHit = true;
            } catch (NumberFormatException e) {
                log.warn("Corrupted distance cache value for key {}: '{}'. Deleting and re-fetching.", distanceCacheKey, cachedDistance);
                redisTemplate.delete(distanceCacheKey);
            }
        }
        
        if (!cacheHit) {
            String lockKey = "lock:distance_cache:" + address.getId() + ":" + restaurantId;
            String lockToken = UUID.randomUUID().toString();
            boolean acquired = redisLock.tryAcquire(lockKey, lockToken, java.time.Duration.ofSeconds(10));
            try {
                if (acquired) {
                    cachedDistance = redisTemplate.opsForValue().get(distanceCacheKey);
                    if (cachedDistance != null) {
                        try {
                            distance = Double.parseDouble(cachedDistance);
                            cacheHit = true;
                        } catch (NumberFormatException ignored) {
                            log.warn("Corrupted distance cache value on double check: '{}'", cachedDistance);
                        }
                    }
                    if (!cacheHit) {
                        String originStr = address.getLatitude() + "," + address.getLongitude();
                        String destinationStr = rLat + "," + rLng;
                        java.util.Map<String, Object> distanceMap = mapsClient.getDistance(originStr, destinationStr);
                        if (distanceMap != null) {
                            if (distanceMap.containsKey("distance")) {
                                distance = ((Number) distanceMap.get("distance")).doubleValue();
                            } else {
                                isFallback = true;
                            }
                            if (Boolean.TRUE.equals(distanceMap.get("fallback"))) {
                                isFallback = true;
                            }
                        } else {
                            isFallback = true;
                        }
                        if (!isFallback) {
                            redisTemplate.opsForValue().set(distanceCacheKey, String.valueOf(distance), 1, java.util.concurrent.TimeUnit.HOURS);
                        }
                    }
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    cachedDistance = redisTemplate.opsForValue().get(distanceCacheKey);
                    if (cachedDistance != null) {
                        try {
                            distance = Double.parseDouble(cachedDistance);
                        } catch (NumberFormatException e) {
                            log.warn("Corrupted distance cache value after lock wait for key {}: '{}'", distanceCacheKey, cachedDistance);
                            throw new RuntimeException("Corrupted distance cache value after waiting for lock");
                        }
                    } else {
                        throw new RuntimeException("Timeout waiting for distance calculation");
                    }
                }
            } finally {
                if (acquired) {
                    redisLock.release(lockKey, lockToken);
                }
            }
            if (isFallback) {
                throw new IllegalArgumentException("Unable to calculate accurate delivery distance as the mapping service is currently unavailable. Please try again later.");
            }
        }
        
        return distance;
    }
}
