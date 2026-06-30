package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

import java.time.LocalDateTime;
import java.util.List;

public interface IOrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByStatusAndUpdatedAtBefore(com.fooddelivery.common.enums.OrderStatus status, LocalDateTime time);
}
