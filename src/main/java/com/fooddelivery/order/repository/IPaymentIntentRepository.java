package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fooddelivery.common.constants.PaymentIntentStatus;

@Repository
public interface IPaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByInternalOrderId(UUID internalOrderId);
    
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentIntent p WHERE p.internalOrderId = :internalOrderId")
    Optional<PaymentIntent> findByInternalOrderIdForUpdate(@Param("internalOrderId") UUID internalOrderId);

    Optional<PaymentIntent> findByGatewayOrderId(String gatewayOrderId);
    
    org.springframework.data.domain.Page<PaymentIntent> findByStatus(PaymentIntentStatus status, org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT p FROM PaymentIntent p WHERE p.status = :status AND p.createdAt < :cutoffTime")
    List<PaymentIntent> findByStatusAndCreatedAtBefore(@Param("status") PaymentIntentStatus status, @Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT p FROM PaymentIntent p WHERE p.status = :status AND p.createdAt < :cutoffTime")
    org.springframework.data.domain.Page<PaymentIntent> findByStatusAndCreatedAtBefore(@Param("status") PaymentIntentStatus status, @Param("cutoffTime") LocalDateTime cutoffTime, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p FROM PaymentIntent p WHERE p.status = :status AND p.createdAt >= :minTime AND p.createdAt < :cutoffTime")
    org.springframework.data.domain.Page<PaymentIntent> findByStatusAndCreatedAtBetween(@Param("status") PaymentIntentStatus status, @Param("minTime") LocalDateTime minTime, @Param("cutoffTime") LocalDateTime cutoffTime, org.springframework.data.domain.Pageable pageable);
}
