package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface IOrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByStatusAndUpdatedAtBefore(com.fooddelivery.common.enums.OrderStatus status, LocalDateTime time);
    List<Order> findByCustomerId(UUID customerId);
    List<Order> findByDeliveryExecutiveId(UUID deliveryExecutiveId);
    List<Order> findByStatusInAndDeliveryExecutiveIdIsNull(List<com.fooddelivery.common.enums.OrderStatus> statuses);
    List<Order> findByStatusIn(List<com.fooddelivery.common.enums.OrderStatus> statuses);
    java.util.Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
    Page<Order> findByCustomerIdAndStatusNotInOrderByCreatedAtDesc(UUID customerId, List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);
    Page<Order> findByCustomerIdAndStatusInOrderByCreatedAtDesc(UUID customerId, List<com.fooddelivery.common.enums.OrderStatus> statuses, Pageable pageable);
}
