package com.fooddelivery.delivery.service;

import com.fooddelivery.delivery.entity.DeliveryExecutive;
import com.fooddelivery.delivery.enums.DeliveryExecutiveStatus;
import com.fooddelivery.delivery.repository.IDeliveryExecutiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final IDeliveryExecutiveRepository repository;

    @Transactional
    public DeliveryExecutive onboard(String name, String phoneNumber, String vehicleNumber) {
        DeliveryExecutive executive = new DeliveryExecutive();
        executive.setId(UUID.randomUUID());
        executive.setName(name);
        executive.setPhoneNumber(phoneNumber);
        executive.setVehicleNumber(vehicleNumber);
        executive.setStatus(DeliveryExecutiveStatus.OFFLINE);
        executive.setCreatedAt(LocalDateTime.now());
        executive.setUpdatedAt(LocalDateTime.now());
        log.info("Onboarded Delivery Executive: {}", executive.getId());
        return repository.save(executive);
    }

    @Transactional
    public DeliveryExecutive toggleStatus(UUID driverId, boolean isOnline) {
        DeliveryExecutive executive = repository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        
        executive.setStatus(isOnline ? DeliveryExecutiveStatus.ONLINE : DeliveryExecutiveStatus.OFFLINE);
        log.info("Driver {} is now {}", driverId, executive.getStatus());
        
        return repository.save(executive);
    }

    private final com.fooddelivery.order.repository.IOrderRepository orderRepository;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    private final com.fooddelivery.delivery.service.LogisticsDispatchService logisticsDispatchService;

    @Transactional
    public com.fooddelivery.order.entity.Order acceptOrderPing(UUID driverId, UUID orderId) {
        log.info("Driver {} attempting to accept order {}", driverId, orderId);
        
        com.fooddelivery.order.entity.Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
        if (order.getStatus() != com.fooddelivery.order.enums.OrderStatus.READY_FOR_PICKUP) {
            throw new IllegalStateException("Order is not ready for pickup");
        }
        
        if (order.getDeliveryExecutiveId() == null || !order.getDeliveryExecutiveId().equals(driverId)) {
            throw new IllegalArgumentException("Order is not assigned to this driver");
        }
        
        // Use Redis SETNX in a real scenario to lock the order for this driver.
        // For now, we update the DB.
        order.setStatus(com.fooddelivery.order.enums.OrderStatus.OUT_FOR_DELIVERY);
        
        com.fooddelivery.common.event.OutboxEvent event = com.fooddelivery.common.event.OutboxEvent.builder()
                .aggregateId(order.getId().toString())
                .aggregateType("Order")
                .type("DRIVER_ASSIGNED")
                .payload("{\"orderId\":\"" + orderId + "\", \"driverId\":\"" + driverId + "\"}")
                .build();
                
        orderSagaOrchestrator.saveStateAndEvent(order, event);
        
        DeliveryExecutive executive = repository.findById(driverId).orElseThrow();
        executive.setStatus(DeliveryExecutiveStatus.ON_DELIVERY);
        repository.save(executive);
        
        return order;
    }

    @Transactional
    public void rejectOrderPing(UUID driverId, UUID orderId) {
        log.info("Driver {} rejected order ping {}", driverId, orderId);
        
        com.fooddelivery.order.entity.Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
        if (order.getDeliveryExecutiveId() == null || !order.getDeliveryExecutiveId().equals(driverId)) {
            throw new IllegalArgumentException("Order is not assigned to this driver");
        }
        
        // In a real system, this updates the dispatch cache to offer it to the next driver.
        // We won't mutate order status here since another driver will take it.
        logisticsDispatchService.releaseDriverLock(driverId.toString());
        
        // Notify Saga to redispatch
        com.fooddelivery.common.event.OutboxEvent event = com.fooddelivery.common.event.OutboxEvent.builder()
                .aggregateId(order.getId().toString())
                .aggregateType("Order")
                .type("ORDER_DRIVER_REJECTED")
                .payload("{\"eventType\":\"ORDER_DRIVER_REJECTED\", \"orderId\":\"" + orderId + "\", \"driverId\":\"" + driverId + "\"}")
                .build();
                
        orderSagaOrchestrator.saveStateAndEvent(order, event);
    }

    @Transactional
    public com.fooddelivery.order.entity.Order updateOrderStatus(UUID driverId, UUID orderId, com.fooddelivery.order.enums.OrderStatus status) {
        log.info("Driver {} updating order {} to {}", driverId, orderId, status);
        
        com.fooddelivery.order.entity.Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        
        if (order.getDeliveryExecutiveId() == null || !order.getDeliveryExecutiveId().equals(driverId)) {
            throw new IllegalArgumentException("Order is not assigned to this driver");
        }
        
        if (status == com.fooddelivery.order.enums.OrderStatus.DELIVERED && order.getStatus() != com.fooddelivery.order.enums.OrderStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException("Can only mark OUT_FOR_DELIVERY orders as DELIVERED, current: " + order.getStatus());
        }
        if (status == com.fooddelivery.order.enums.OrderStatus.DELIVERY_FAILED && order.getStatus() != com.fooddelivery.order.enums.OrderStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException("Can only mark OUT_FOR_DELIVERY orders as DELIVERY_FAILED, current: " + order.getStatus());
        }
                
        order.setStatus(status);
        
        com.fooddelivery.common.event.OutboxEvent event = com.fooddelivery.common.event.OutboxEvent.builder()
                .aggregateId(order.getId().toString())
                .aggregateType("Order")
                .type(status == com.fooddelivery.order.enums.OrderStatus.DELIVERED ? "ORDER_DELIVERED" : "ORDER_STATUS_UPDATED")
                .payload("{\"eventType\":\"" + (status == com.fooddelivery.order.enums.OrderStatus.DELIVERED ? "ORDER_DELIVERED" : "ORDER_STATUS_UPDATED") + "\", \"orderId\":\"" + orderId + "\", \"status\":\"" + status.name() + "\"}")
                .build();
                
        orderSagaOrchestrator.saveStateAndEvent(order, event);
        
        if (status == com.fooddelivery.order.enums.OrderStatus.DELIVERED || status == com.fooddelivery.order.enums.OrderStatus.DELIVERY_FAILED) {
            DeliveryExecutive executive = repository.findById(driverId).orElseThrow();
            executive.setStatus(DeliveryExecutiveStatus.ONLINE);
            executive.setUpdatedAt(java.time.LocalDateTime.now());
            repository.save(executive);
            
            // Release the Redis driver lock so they can receive new pings
            logisticsDispatchService.releaseDriverLock(driverId.toString());
        }
        
        return order;
    }

    @Transactional
    public void timeoutDriverPing(UUID driverId, UUID orderId) {
        log.info("Driver {} ping timed out for order {}", driverId, orderId);
        
        com.fooddelivery.order.entity.Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
        if (order.getDeliveryExecutiveId() == null || !order.getDeliveryExecutiveId().equals(driverId)) {
            throw new IllegalArgumentException("Order is not assigned to this driver");
        }
        
        // Similar to reject, we will let LogisticsDispatchService release the lock.
        logisticsDispatchService.releaseDriverLock(driverId.toString());
        
        // Notify Saga to redispatch
        com.fooddelivery.common.event.OutboxEvent event = com.fooddelivery.common.event.OutboxEvent.builder()
                .aggregateId(order.getId().toString())
                .aggregateType("Order")
                .type("ORDER_DRIVER_REJECTED")
                .payload("{\"eventType\":\"ORDER_DRIVER_REJECTED\", \"orderId\":\"" + orderId + "\", \"driverId\":\"" + driverId + "\"}")
                .build();
                
        orderSagaOrchestrator.saveStateAndEvent(order, event);
    }
}
