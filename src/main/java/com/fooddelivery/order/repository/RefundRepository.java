package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.orderId = :orderId AND r.status IN ('COMPLETED', 'PROCESSING')")
    BigDecimal sumCompletedByOrder(@Param("orderId") UUID orderId);

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT r FROM Refund r WHERE r.status = 'PROCESSING' AND r.updatedAt < :threshold")
    List<Refund> findStuckProcessing(@Param("threshold") LocalDateTime threshold);

    List<Refund> findByOrderId(UUID orderId);
    
    @Query("SELECT r FROM Refund r, Order o WHERE r.orderId = o.id AND o.restaurantId = :restaurantId AND r.status = :status AND r.faultType = 'RESTAURANT_FAULT'")
    List<Refund> findRestaurantFaultRefunds(@Param("restaurantId") UUID restaurantId, @Param("status") com.fooddelivery.common.enums.RefundStatus status);

    org.springframework.data.domain.Page<Refund> findByStatus(com.fooddelivery.common.enums.RefundStatus status, org.springframework.data.domain.Pageable pageable);
}
