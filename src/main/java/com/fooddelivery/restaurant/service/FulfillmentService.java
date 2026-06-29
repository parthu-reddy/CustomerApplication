package com.fooddelivery.restaurant.service;

import com.fooddelivery.common.event.OutboxEvent;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.enums.OrderStatus;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FulfillmentService {

    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    @Transactional
    public Order acceptOrder(UUID restaurantId, UUID orderId) {
        log.info("Restaurant {} accepting order {}", restaurantId, orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
        if (!order.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Order does not belong to this restaurant");
        }
        
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Can only accept PAID orders");
        }
        
        order.setStatus(OrderStatus.ACCEPTED);
        
        // This simulates saving the order and triggering the Outbox pattern for Logistics dispatch
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId(order.getId().toString())
                .aggregateType("Order")
                .type("ORDER_ACCEPTED")
                .payload("{\"eventType\":\"ORDER_ACCEPTED\", \"orderId\":\"" + orderId + "\", \"restaurantId\":\"" + restaurantId + "\"}")
                .build();
                
        orderSagaOrchestrator.saveStateAndEvent(order, event);
        return order;
    }

    @Transactional
    public Order rejectOrder(UUID restaurantId, UUID orderId) {
        log.info("Restaurant {} rejecting order {}", restaurantId, orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
        if (!order.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Order does not belong to this restaurant");
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId(order.getId().toString())
                .aggregateType("Order")
                .type("ORDER_REJECTED")
                .payload("{\"eventType\":\"ORDER_REJECTED\", \"orderId\":\"" + orderId + "\", \"restaurantId\":\"" + restaurantId + "\"}")
                .build();
                
        orderSagaOrchestrator.saveStateAndEvent(order, event);
        return order;
    }

    @Transactional
    public Order readyOrder(UUID restaurantId, UUID orderId) {
        log.info("Restaurant {} marked order {} as ready", restaurantId, orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
        if (!order.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Order does not belong to this restaurant");
        }
        
        if (order.getStatus() != OrderStatus.ACCEPTED && order.getStatus() != OrderStatus.DISPATCHED) {
            throw new IllegalStateException("Can only mark ACCEPTED or DISPATCHED orders as ready, current status: " + order.getStatus());
        }
        
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId(order.getId().toString())
                .aggregateType("Order")
                .type("ORDER_READY")
                .payload("{\"eventType\":\"ORDER_READY\", \"orderId\":\"" + orderId + "\", \"restaurantId\":\"" + restaurantId + "\"}")
                .build();
                
        orderSagaOrchestrator.saveStateAndEvent(order, event);
        return order;
    }

    @Transactional
    public Order cancelOrderAfterAccept(UUID restaurantId, UUID orderId) {
        log.info("Restaurant {} cancelling order {} after acceptance", restaurantId, orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
        if (!order.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Order does not belong to this restaurant");
        }
        
        if (order.getStatus() != OrderStatus.ACCEPTED && order.getStatus() != OrderStatus.DISPATCHED && order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new IllegalStateException("Can only cancel ACCEPTED, DISPATCHED, or READY_FOR_PICKUP orders, current status: " + order.getStatus());
        }
        
        order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
        
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId(order.getId().toString())
                .aggregateType("Order")
                .type("ORDER_CANCELLED_BY_RESTAURANT")
                .payload("{\"eventType\":\"ORDER_CANCELLED_BY_RESTAURANT\", \"orderId\":\"" + orderId + "\", \"restaurantId\":\"" + restaurantId + "\"}")
                .build();
                
        orderSagaOrchestrator.saveStateAndEvent(order, event);
        return order;
    }
}
