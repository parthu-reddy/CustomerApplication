package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RefundItemRepository extends JpaRepository<RefundItem, UUID> {

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM RefundItem ri JOIN ri.refund r WHERE ri.orderItemId = :orderItemId AND r.status = 'COMPLETED'")
    Integer sumCompletedQuantity(@Param("orderItemId") UUID orderItemId);
}
