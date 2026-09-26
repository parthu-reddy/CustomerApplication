package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;

public interface IOrderRepository extends JpaRepository<Order, UUID> {
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByStatusAndUpdatedAtBefore(com.fooddelivery.common.enums.OrderStatus status, Instant time);
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusAndUpdatedAtBefore(com.fooddelivery.common.enums.OrderStatus status, Instant time, Pageable pageable);
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusInAndUpdatedAtBefore(List<com.fooddelivery.common.enums.OrderStatus> statuses, Instant time, Pageable pageable);
    
    @EntityGraph(attributePaths = {"orderItems"})
    /**
     * Orders the restaurant has stalled on: still in a restaurant-owned state, untouched since
     * {@code time}, and with no delivery outcome already recorded.
     *
     * <p>The delivery-status exclusion is the point. Without it the sweeper collected orders whose
     * dispatch or delivery had already failed and cancelled them a second time as
     * CANCELLED_BY_RESTAURANT, billing the restaurant for a failure that was not theirs.
     */
    @Query("SELECT o FROM Order o WHERE o.status IN :statuses AND o.updatedAt < :time "
            + "AND (o.deliveryStatus IS NULL OR o.deliveryStatus NOT IN :endedDelivery)")
    Page<Order> findStuckInRestaurantStates(
            @Param("statuses") List<com.fooddelivery.common.enums.OrderStatus> statuses,
            @Param("endedDelivery") List<com.fooddelivery.common.enums.DeliveryStatus> endedDelivery,
            @Param("time") Instant time,
            Pageable pageable);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByCustomerId(UUID customerId);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByRestaurantId(UUID restaurantId);

    /**
     * An outlet's orders delivered in {@code [from, to)}: what it earned in that window. Dated on
     * {@code deliveredAt}, because that is when {@code LedgerBookkeeper.bookDelivered} raises the
     * payable; an order that never reached the customer carries a quoted payout that is never paid.
     * The items come in the same query: the earnings summary sums them outside any transaction, and
     * open-in-view is off in the deployed config.
     */
    @EntityGraph(attributePaths = {"orderItems"})
    @org.springframework.data.jpa.repository.Query(
        "SELECT o FROM Order o WHERE o.restaurantId = :restaurantId AND o.deliveredAt >= :from AND o.deliveredAt < :to")
    List<Order> findByRestaurantIdDeliveredInWindow(
        @org.springframework.data.repository.query.Param("restaurantId") UUID restaurantId,
        @org.springframework.data.repository.query.Param("from") Instant from,
        @org.springframework.data.repository.query.Param("to") Instant to);

    /** A rider's orders delivered in {@code [from, to)}: what they earned in that window. Dated as above. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT o FROM Order o WHERE o.deliveryExecutiveId = :driverId AND o.deliveredAt >= :from AND o.deliveredAt < :to")
    List<Order> findByDeliveryExecutiveIdDeliveredInWindow(
        @org.springframework.data.repository.query.Param("driverId") UUID driverId,
        @org.springframework.data.repository.query.Param("from") Instant from,
        @org.springframework.data.repository.query.Param("to") Instant to);

    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByDeliveryExecutiveId(UUID deliveryExecutiveId);
    
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByDeliveryExecutiveIdAndStatusNotIn(UUID deliveryExecutiveId, List<com.fooddelivery.common.enums.OrderStatus> statuses);

    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByDeliveryExecutiveIdAndStatusAndCreatedAtBetween(UUID deliveryExecutiveId, com.fooddelivery.common.enums.OrderStatus status, Instant start, Instant end);
    
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
        "AND o.createdAt >= :start AND o.createdAt < :end"
    )
    Page<Order> findHistoryOrdersForDriver(
        @org.springframework.data.repository.query.Param("driverId") UUID driverId,
        @org.springframework.data.repository.query.Param("cancelledStatuses") List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses,
        @org.springframework.data.repository.query.Param("terminalDeliveryStatuses") List<com.fooddelivery.common.enums.DeliveryStatus> terminalDeliveryStatuses,
        @org.springframework.data.repository.query.Param("start") Instant start,
        @org.springframework.data.repository.query.Param("end") Instant end,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByCustomerIdAndStatusInOrderByCreatedAtDesc(UUID customerId, List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByStatusOrderByCreatedAtDesc(com.fooddelivery.common.enums.OrderStatus status, Pageable pageable);
    
    Page<Order> findByDeliveryStatusOrderByCreatedAtDesc(com.fooddelivery.common.enums.DeliveryStatus deliveryStatus, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status NOT IN :excludedStatuses AND o.createdAt >= :from AND o.createdAt < :to")
    java.math.BigDecimal sumOrderTotalsInWindow(@org.springframework.data.repository.query.Param("from") java.time.Instant from, @org.springframework.data.repository.query.Param("to") java.time.Instant to, @org.springframework.data.repository.query.Param("excludedStatuses") List<com.fooddelivery.common.enums.OrderStatus> excludedStatuses);

    /**
     * What the orders say was owed to restaurants for deliveries on this date.
     *
     * <p>Dated on {@code deliveredAt}, because that is when {@code LedgerBookkeeper.bookDelivered}
     * raises the payable. Dating on {@code createdAt} would compare an order placed near midnight
     * against a payable booked the next day and report a break every night.
     */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(o.restaurantPayout), 0) FROM Order o "
            + "WHERE o.deliveredAt IS NOT NULL AND o.deliveredAt >= :from AND o.deliveredAt < :to")
    java.math.BigDecimal sumRestaurantPayableDeliveredInWindow(@org.springframework.data.repository.query.Param("from") java.time.Instant from, @org.springframework.data.repository.query.Param("to") java.time.Instant to);

    /** The same, for riders. */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(o.driverNetPayout), 0) FROM Order o "
            + "WHERE o.deliveredAt IS NOT NULL AND o.deliveredAt >= :from AND o.deliveredAt < :to")
    java.math.BigDecimal sumDriverPayableDeliveredInWindow(@org.springframework.data.repository.query.Param("from") java.time.Instant from, @org.springframework.data.repository.query.Param("to") java.time.Instant to);
}
