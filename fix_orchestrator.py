import re

with open("src/main/java/com/fooddelivery/order/service/OrderSagaOrchestrator.java", "r") as f:
    content = f.read()

# 1. checkDelayApprovalTimeouts
# It's currently annotated with @Transactional. We need to remove it and wrap the DB calls in TransactionTemplate.
# Wait, checkDelayApprovalTimeouts doesn't have a transactionTemplate right now, we can use the injected one.
check_delay_old = """    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "60000") // Run every 1 minute
    @Transactional
    public void checkDelayApprovalTimeouts() {
        java.time.LocalDateTime cutoffTime = java.time.LocalDateTime.now().minusMinutes(10);
        java.util.List<Order> delayedOrders = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.AWAITING_DELAY_APPROVAL, cutoffTime);
        
        for (Order order : delayedOrders) {
            log.info("Order {} exceeded 10-minute delay approval timeout. Cancelling order.", order.getId());
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancellationReason("Auto-cancelled: Customer did not respond to delay approval in 10 minutes");
            orderRepository.save(order);
            
            // Refund the customer
            processRefund(order);
            
            // Publish rejection event to restaurant to let them know it was auto-cancelled
            publishDelayApprovalEvent(order, false);
            
            // Send notification to customer
            sendNotification(order.getId().toString(), order.getCustomerId(), "ORDER_CANCELLED_DELAY_TIMEOUT");
        }
    }"""

check_delay_new = """    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "60000") // Run every 1 minute
    public void checkDelayApprovalTimeouts() {
        java.time.LocalDateTime cutoffTime = java.time.LocalDateTime.now().minusMinutes(10);
        
        java.util.List<Order> delayedOrders = transactionTemplate.execute(status -> {
            return orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.AWAITING_DELAY_APPROVAL, cutoffTime);
        });
        
        if (delayedOrders == null || delayedOrders.isEmpty()) return;
        
        for (Order order : delayedOrders) {
            log.info("Order {} exceeded 10-minute delay approval timeout. Cancelling order.", order.getId());
            
            boolean updated = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                Order dbOrder = orderRepository.findById(order.getId()).orElse(null);
                if (dbOrder != null && dbOrder.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL) {
                    dbOrder.setStatus(OrderStatus.CANCELLED);
                    dbOrder.setCancellationReason("Auto-cancelled: Customer did not respond to delay approval in 10 minutes");
                    orderRepository.save(dbOrder);
                    return true;
                }
                return false;
            }));
            
            if (updated) {
                // Refund the customer (Outside transaction!)
                processRefund(order);
                
                // Publish rejection event to restaurant to let them know it was auto-cancelled
                publishDelayApprovalEvent(order, false);
                
                // Send notification to customer
                sendNotification(order.getId().toString(), order.getCustomerId(), "ORDER_CANCELLED_DELAY_TIMEOUT");
            }
        }
    }"""

content = content.replace(check_delay_old, check_delay_new)

# 2. handleOrderEvents
# We need to extract processRefund out of the transactionTemplate.
# We can introduce a thread-local or just an array to hold the order that needs refund.
# Actually, since handleOrderEvents only processes ONE event, we can just return the Order that needs refund from the transactionTemplate!
# wait, transactionTemplate.executeWithoutResult doesn't return anything.
# Let's change it to transactionTemplate.execute(status -> ...) returning an Order.

handle_events_old_start = """        while (!success && retries < 5) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);"""

handle_events_new_start = """        while (!success && retries < 5) {
            try {
                Order orderToRefund = transactionTemplate.execute(status -> {
                    try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);"""

content = content.replace(handle_events_old_start, handle_events_new_start)

# Now we need to replace all `return;` inside handleOrderEvents with `return null;`
# and `processRefund(order); return;` with `return order;`
# But only for the places inside handleOrderEvents.

content = content.replace("""                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "ORDER_DELIVERED");
                } else if (order != null) {
                    if (OrderStatus.DELIVERED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_DELIVERED for Order {}. Current status {} is further along.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_DELIVERED for Order {}. Current status is {}. Allowed previous states are DISPATCHED, READY_FOR_PICKUP, OUT_FOR_DELIVERY.", orderId, order.getStatus());
                    }
                }
                return;""", """                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "ORDER_DELIVERED");
                } else if (order != null) {
                    if (OrderStatus.DELIVERED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_DELIVERED for Order {}. Current status {} is further along.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_DELIVERED for Order {}. Current status is {}. Allowed previous states are DISPATCHED, READY_FOR_PICKUP, OUT_FOR_DELIVERY.", orderId, order.getStatus());
                    }
                }
                return null;""")

content = content.replace("""                    order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
                    order.setCancellationReason(rootNode.path("reason").asText("Restaurant could not fulfill the order"));
                    orderRepository.save(order);
                    processRefund(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "ORDER_CANCELLED_BY_RESTAURANT");
                } else if (order != null) {
                    if (OrderStatus.CANCELLED_BY_RESTAURANT.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process {} for Order {}. Current status is {}.", eventType, orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process {} for Order {}. Current status is {}. Allowed previous states are PAID, AWAITING_DELAY_APPROVAL, ACCEPTED, DISPATCHED, READY_FOR_PICKUP.", eventType, orderId, order.getStatus());
                    }
                }
                return;""", """                    order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
                    order.setCancellationReason(rootNode.path("reason").asText("Restaurant could not fulfill the order"));
                    orderRepository.save(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "ORDER_CANCELLED_BY_RESTAURANT");
                    return order; // Need refund
                } else if (order != null) {
                    if (OrderStatus.CANCELLED_BY_RESTAURANT.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process {} for Order {}. Current status is {}.", eventType, orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process {} for Order {}. Current status is {}. Allowed previous states are PAID, AWAITING_DELAY_APPROVAL, ACCEPTED, DISPATCHED, READY_FOR_PICKUP.", eventType, orderId, order.getStatus());
                    }
                }
                return null;""")

content = content.replace("""                    if (order != null && (order.getStatus() == OrderStatus.DISPATCHED || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY || order.getStatus() == OrderStatus.READY_FOR_PICKUP)) {
                        order.setStatus(OrderStatus.DELIVERY_FAILED);
                        orderRepository.save(order);
                        processRefund(order);
                        
                        // Send notification to customer
                        sendNotification(orderId.toString(), order.getCustomerId(), "DELIVERY_FAILED");
                    } else if (order != null) {
                        if (OrderStatus.DELIVERY_FAILED.ordinal() < order.getStatus().ordinal()) {
                            log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process DELIVERY_FAILED status update for Order {}. Current status is {}.", orderId, order.getStatus());
                        } else {
                            log.error("ILLEGAL_STATE_TRANSITION: Cannot process DELIVERY_FAILED status update for Order {}. Current status is {}. Allowed previous states are DISPATCHED, OUT_FOR_DELIVERY, READY_FOR_PICKUP.", orderId, order.getStatus());
                        }
                    }
                } else if (updateStatus != null) {""", """                    if (order != null && (order.getStatus() == OrderStatus.DISPATCHED || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY || order.getStatus() == OrderStatus.READY_FOR_PICKUP)) {
                        order.setStatus(OrderStatus.DELIVERY_FAILED);
                        orderRepository.save(order);
                        
                        // Send notification to customer
                        sendNotification(orderId.toString(), order.getCustomerId(), "DELIVERY_FAILED");
                        return order; // Need refund
                    } else if (order != null) {
                        if (OrderStatus.DELIVERY_FAILED.ordinal() < order.getStatus().ordinal()) {
                            log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process DELIVERY_FAILED status update for Order {}. Current status is {}.", orderId, order.getStatus());
                        } else {
                            log.error("ILLEGAL_STATE_TRANSITION: Cannot process DELIVERY_FAILED status update for Order {}. Current status is {}. Allowed previous states are DISPATCHED, OUT_FOR_DELIVERY, READY_FOR_PICKUP.", orderId, order.getStatus());
                        }
                    }
                } else if (updateStatus != null) {""")

content = content.replace("""                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process status update to {} for Order {}. Current status {} is already terminal.", updateStatus, orderId, order.getStatus());
                    }
                }
                return;
            }""", """                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process status update to {} for Order {}. Current status {} is already terminal.", updateStatus, orderId, order.getStatus());
                    }
                }
                return null;
            }""")

content = content.replace("""            if ("ORDER_DRIVER_REJECTED".equals(eventType)) {
                log.info("Driver rejected/timed out ping for Order {}. Redispatching will be handled by DeliveryExecutiveApplication.", orderId);
                // DeliveryExecutiveApplication is listening to this event and will handle redispatch
                return;
            }""", """            if ("ORDER_DRIVER_REJECTED".equals(eventType)) {
                log.info("Driver rejected/timed out ping for Order {}. Redispatching will be handled by DeliveryExecutiveApplication.", orderId);
                // DeliveryExecutiveApplication is listening to this event and will handle redispatch
                return null;
            }""")

content = content.replace("""                } else if (order != null) {
                    log.error("ILLEGAL_STATE_TRANSITION: Cannot process DRIVER_ASSIGNED for Order {}. Current status is {}. Allowed previous states are ACCEPTED, READY_FOR_PICKUP, DISPATCHED.", orderId, order.getStatus());
                }
                return;
            }""", """                } else if (order != null) {
                    log.error("ILLEGAL_STATE_TRANSITION: Cannot process DRIVER_ASSIGNED for Order {}. Current status is {}. Allowed previous states are ACCEPTED, READY_FOR_PICKUP, DISPATCHED.", orderId, order.getStatus());
                }
                return null;
            }""")

content = content.replace("""                if (order != null && (order.getStatus() == OrderStatus.ACCEPTED || order.getStatus() == OrderStatus.READY_FOR_PICKUP || order.getStatus() == OrderStatus.DISPATCHED)) {
                    order.setStatus(OrderStatus.DELIVERY_FAILED);
                    orderRepository.save(order);
                    processRefund(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "DISPATCH_FAILED");
                } else if (order != null) {""", """                if (order != null && (order.getStatus() == OrderStatus.ACCEPTED || order.getStatus() == OrderStatus.READY_FOR_PICKUP || order.getStatus() == OrderStatus.DISPATCHED)) {
                    order.setStatus(OrderStatus.DELIVERY_FAILED);
                    orderRepository.save(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "DISPATCH_FAILED");
                    return order; // Need refund
                } else if (order != null) {""")

content = content.replace("""                    if (OrderStatus.DELIVERY_FAILED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process DISPATCH_FAILED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process DISPATCH_FAILED for Order {}. Current status is {}. Allowed previous states are ACCEPTED, READY_FOR_PICKUP, DISPATCHED.", orderId, order.getStatus());
                    }
                }
                return;
            }""", """                    if (OrderStatus.DELIVERY_FAILED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process DISPATCH_FAILED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process DISPATCH_FAILED for Order {}. Current status is {}. Allowed previous states are ACCEPTED, READY_FOR_PICKUP, DISPATCHED.", orderId, order.getStatus());
                    }
                }
                return null;
            }""")

content = content.replace("""                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "DELAY_APPROVAL_REQUESTED");
                } else if (order != null) {
                    if (OrderStatus.AWAITING_DELAY_APPROVAL.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_DELAY_APPROVAL_REQUESTED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_DELAY_APPROVAL_REQUESTED for Order {}. Current status is {}. Expected PAID.", orderId, order.getStatus());
                    }
                }
                return;
            }""", """                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "DELAY_APPROVAL_REQUESTED");
                } else if (order != null) {
                    if (OrderStatus.AWAITING_DELAY_APPROVAL.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_DELAY_APPROVAL_REQUESTED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_DELAY_APPROVAL_REQUESTED for Order {}. Current status is {}. Expected PAID.", orderId, order.getStatus());
                    }
                }
                return null;
            }""")

content = content.replace("""                if (order != null && order.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL) {
                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);
                    processRefund(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "ORDER_DELAY_REJECTED");
                } else if (order != null) {""", """                if (order != null && order.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL) {
                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "ORDER_DELAY_REJECTED");
                    return order; // Need refund
                } else if (order != null) {""")

content = content.replace("""                    if (OrderStatus.CANCELLED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_DELAY_REJECTED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_DELAY_REJECTED for Order {}. Current status is {}. Expected AWAITING_DELAY_APPROVAL.", orderId, order.getStatus());
                    }
                }
                return;
            }""", """                    if (OrderStatus.CANCELLED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_DELAY_REJECTED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_DELAY_REJECTED for Order {}. Current status is {}. Expected AWAITING_DELAY_APPROVAL.", orderId, order.getStatus());
                    }
                }
                return null;
            }""")

content = content.replace("""                } else if (order != null) {
                    if (OrderStatus.ACCEPTED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_ACCEPTED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_ACCEPTED for Order {}. Current status is {}. Expected PAID or AWAITING_DELAY_APPROVAL.", orderId, order.getStatus());
                    }
                }
                return;
            }""", """                } else if (order != null) {
                    if (OrderStatus.ACCEPTED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_ACCEPTED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_ACCEPTED for Order {}. Current status is {}. Expected PAID or AWAITING_DELAY_APPROVAL.", orderId, order.getStatus());
                    }
                }
                return null;
            }""")

content = content.replace("""                } else if (order != null) {
                    if (OrderStatus.READY_FOR_PICKUP.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_READY for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_READY for Order {}. Current status is {}. Expected ACCEPTED or DISPATCHED.", orderId, order.getStatus());
                    }
                }
                return;
            }""", """                } else if (order != null) {
                    if (OrderStatus.READY_FOR_PICKUP.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_READY for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_READY for Order {}. Current status is {}. Expected ACCEPTED or DISPATCHED.", orderId, order.getStatus());
                    }
                }
                return null;
            }""")

# Missing return for the block where orderIdStr == null
content = content.replace("""            if (orderIdStr == null || eventType == null) {
                log.warn("Missing orderId or eventType. Ignored.");
                return;
            }""", """            if (orderIdStr == null || eventType == null) {
                log.warn("Missing orderId or eventType. Ignored.");
                return null;
            }""")

# Default return
content = content.replace("""                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process order event inner", e);
                    }
                });
                success = true;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {""", """                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process order event inner", e);
                    }
                });
                success = true;
                if (orderToRefund != null) {
                    processRefund(orderToRefund);
                }
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {""")

with open("src/main/java/com/fooddelivery/order/service/OrderSagaOrchestrator.java", "w") as f:
    f.write(content)

