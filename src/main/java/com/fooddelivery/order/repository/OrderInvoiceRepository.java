package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.OrderInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderInvoiceRepository extends JpaRepository<OrderInvoice, UUID> {
}
