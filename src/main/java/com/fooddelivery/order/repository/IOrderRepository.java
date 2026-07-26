package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;

public interface IOrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByStatusAndUpdatedAtBefore(com.fooddelivery.common.enums.OrderStatus status, LocalDateTime time);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByCustomerId(UUID customerId);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByDeliveryExecutiveId(UUID deliveryExecutiveId);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByDeliveryExecutiveIdAndStatusNotIn(UUID deliveryExecutiveId, List<com.fooddelivery.common.enums.OrderStatus> statuses);

    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByDeliveryExecutiveIdAndStatusAndCreatedAtBetween(UUID deliveryExecutiveId, com.fooddelivery.common.enums.OrderStatus status, LocalDateTime start, LocalDateTime end);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByStatusInAndDeliveryExecutiveIdIsNull(List<com.fooddelivery.common.enums.OrderStatus> statuses);
    
    List<Order> findByStatusIn(List<com.fooddelivery.common.enums.OrderStatus> statuses);
    
    @EntityGraph(attributePaths = {"orderItems"})
    java.util.Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByCustomerIdAndStatusNotInOrderByCreatedAtDesc(UUID customerId, List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);
    
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByCustomerIdAndStatusInOrderByCreatedAtDesc(UUID customerId, List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);
}
