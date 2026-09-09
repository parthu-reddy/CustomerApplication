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
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByStatusAndUpdatedAtBefore(com.fooddelivery.common.enums.OrderStatus status, LocalDateTime time);
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusAndUpdatedAtBefore(com.fooddelivery.common.enums.OrderStatus status, LocalDateTime time, Pageable pageable);
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusInAndUpdatedAtBefore(List<com.fooddelivery.common.enums.OrderStatus> statuses, LocalDateTime time, Pageable pageable);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByStatusAndCreatedAtBefore(com.fooddelivery.common.enums.OrderStatus status, LocalDateTime time);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByCustomerId(UUID customerId);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByRestaurantId(UUID restaurantId);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByDeliveryExecutiveId(UUID deliveryExecutiveId);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByDeliveryExecutiveIdAndStatusNotIn(UUID deliveryExecutiveId, List<com.fooddelivery.common.enums.OrderStatus> statuses);

    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByDeliveryExecutiveIdAndStatusAndCreatedAtBetween(UUID deliveryExecutiveId, com.fooddelivery.common.enums.OrderStatus status, LocalDateTime start, LocalDateTime end);
    
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusInAndDeliveryExecutiveIdIsNull(List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);
    
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusInAndDeliveryExecutiveIdIsNullOrderByCreatedAtDesc(List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByStatusIn(List<com.fooddelivery.common.enums.OrderStatus> statuses);
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusIn(List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);
    
    @EntityGraph(attributePaths = {"orderItems"})
    java.util.Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByIdInAndCustomerId(List<UUID> ids, UUID customerId);

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
    Page<Order> findActiveOrdersForDriver(
        @org.springframework.data.repository.query.Param("driverId") UUID driverId,
        @org.springframework.data.repository.query.Param("cancelledStatuses") List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses,
        @org.springframework.data.repository.query.Param("terminalDeliveryStatuses") List<com.fooddelivery.common.enums.DeliveryStatus> terminalDeliveryStatuses,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"orderItems"})
    @org.springframework.data.jpa.repository.Query(
        "SELECT o FROM Order o " +
        "WHERE o.deliveryExecutiveId = :driverId " +
        "AND (o.status IN :cancelledStatuses OR o.deliveryStatus IN :terminalDeliveryStatuses) " +
        "AND o.createdAt >= :start AND o.createdAt <= :end"
    )
    Page<Order> findHistoryOrdersForDriver(
        @org.springframework.data.repository.query.Param("driverId") UUID driverId,
        @org.springframework.data.repository.query.Param("cancelledStatuses") List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses,
        @org.springframework.data.repository.query.Param("terminalDeliveryStatuses") List<com.fooddelivery.common.enums.DeliveryStatus> terminalDeliveryStatuses,
        @org.springframework.data.repository.query.Param("start") LocalDateTime start,
        @org.springframework.data.repository.query.Param("end") LocalDateTime end,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByCustomerIdAndStatusInOrderByCreatedAtDesc(UUID customerId, List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusOrderByCreatedAtDesc(com.fooddelivery.common.enums.OrderStatus status, Pageable pageable);
    
    Page<Order> findByDeliveryStatusOrderByCreatedAtDesc(com.fooddelivery.common.enums.DeliveryStatus deliveryStatus, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status NOT IN :excludedStatuses AND CAST(o.createdAt AS date) = :date")
    java.math.BigDecimal sumOrderTotalsByDate(@org.springframework.data.repository.query.Param("date") java.time.LocalDate date, @org.springframework.data.repository.query.Param("excludedStatuses") List<com.fooddelivery.common.enums.OrderStatus> excludedStatuses);

    /**
     * What the orders say was owed to restaurants for deliveries on this date.
     *
     * <p>Dated on {@code deliveredAt}, because that is when {@code LedgerBookkeeper.bookDelivered}
     * raises the payable. Dating on {@code createdAt} would compare an order placed near midnight
     * against a payable booked the next day and report a break every night.
     */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(o.restaurantPayout), 0) FROM Order o "
            + "WHERE o.deliveredAt IS NOT NULL AND CAST(o.deliveredAt AS date) = :date")
    java.math.BigDecimal sumRestaurantPayableByDeliveryDate(@org.springframework.data.repository.query.Param("date") java.time.LocalDate date);

    /** The same, for riders. */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(o.driverNetPayout), 0) FROM Order o "
            + "WHERE o.deliveredAt IS NOT NULL AND CAST(o.deliveredAt AS date) = :date")
    java.math.BigDecimal sumDriverPayableByDeliveryDate(@org.springframework.data.repository.query.Param("date") java.time.LocalDate date);
}
