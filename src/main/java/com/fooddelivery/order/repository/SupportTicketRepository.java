package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus status, Pageable pageable);

    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SupportTicket> findByOrderId(UUID orderId);

    boolean existsByOrderIdAndCustomerIdAndStatus(UUID orderId, UUID customerId, SupportTicket.TicketStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT st FROM SupportTicket st JOIN Order o ON st.orderId = o.id WHERE o.restaurantId = :restaurantId AND st.status = :status ORDER BY st.createdAt DESC")
    List<SupportTicket> findByRestaurantIdAndStatus(@org.springframework.data.repository.query.Param("restaurantId") UUID restaurantId, @org.springframework.data.repository.query.Param("status") SupportTicket.TicketStatus status);
}
