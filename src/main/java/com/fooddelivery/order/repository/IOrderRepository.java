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
    Page<Order> findByStatusAndUpdatedAtBefore(com.fooddelivery.common.enums.OrderStatus status, LocalDateTime time, Pageable pageable);
    
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
    @org.springframework.data.jpa.repository.Query(
        "SELECT o FROM Order o " +
        "WHERE o.customerId = :customerId " +
        "AND o.status NOT IN :cancelledStatuses " +
        "AND (o.deliveryStatus IS NULL OR o.deliveryStatus NOT IN :terminalDeliveryStatuses) " +
        "ORDER BY o.createdAt DESC"
    )
    Page<Order> findActiveOrdersForCustomer(
        @org.springframework.data.repository.query.Param("customerId") UUID customerId,
        @org.springframework.data.repository.query.Param("cancelledStatuses") List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses,
        @org.springframework.data.repository.query.Param("terminalDeliveryStatuses") List<com.fooddelivery.common.enums.DeliveryStatus> terminalDeliveryStatuses,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"orderItems"})
    @org.springframework.data.jpa.repository.Query(
        "SELECT o FROM Order o " +
        "WHERE o.customerId = :customerId " +
        "AND (o.status IN :cancelledStatuses OR o.deliveryStatus IN :terminalDeliveryStatuses) " +
        "ORDER BY o.createdAt DESC"
    )
    Page<Order> findHistoryOrdersForCustomer(
        @org.springframework.data.repository.query.Param("customerId") UUID customerId,
        @org.springframework.data.repository.query.Param("cancelledStatuses") List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses,
        @org.springframework.data.repository.query.Param("terminalDeliveryStatuses") List<com.fooddelivery.common.enums.DeliveryStatus> terminalDeliveryStatuses,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"orderItems"})
    @org.springframework.data.jpa.repository.Query(
        "SELECT o FROM Order o " +
        "WHERE o.deliveryExecutiveId = :driverId " +
        "AND o.status NOT IN :cancelledStatuses " +
        "AND (o.deliveryStatus IS NULL OR o.deliveryStatus NOT IN :terminalDeliveryStatuses)"
    )
    List<Order> findActiveOrdersForDriver(
        @org.springframework.data.repository.query.Param("driverId") UUID driverId,
        @org.springframework.data.repository.query.Param("cancelledStatuses") List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses,
        @org.springframework.data.repository.query.Param("terminalDeliveryStatuses") List<com.fooddelivery.common.enums.DeliveryStatus> terminalDeliveryStatuses
    );

    @EntityGraph(attributePaths = {"orderItems"})
    @org.springframework.data.jpa.repository.Query(
        "SELECT o FROM Order o " +
        "WHERE o.deliveryExecutiveId = :driverId " +
        "AND (o.status IN :cancelledStatuses OR o.deliveryStatus IN :terminalDeliveryStatuses) " +
        "AND o.createdAt >= :start AND o.createdAt <= :end"
    )
    List<Order> findHistoryOrdersForDriver(
        @org.springframework.data.repository.query.Param("driverId") UUID driverId,
        @org.springframework.data.repository.query.Param("cancelledStatuses") List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses,
        @org.springframework.data.repository.query.Param("terminalDeliveryStatuses") List<com.fooddelivery.common.enums.DeliveryStatus> terminalDeliveryStatuses,
        @org.springframework.data.repository.query.Param("start") LocalDateTime start,
        @org.springframework.data.repository.query.Param("end") LocalDateTime end
    );

    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByCustomerIdAndStatusInOrderByCreatedAtDesc(UUID customerId, List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusOrderByCreatedAtDesc(com.fooddelivery.common.enums.OrderStatus status, Pageable pageable);
    
    Page<Order> findByDeliveryStatusOrderByCreatedAtDesc(com.fooddelivery.common.enums.DeliveryStatus deliveryStatus, Pageable pageable);
}
