package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
    List<PaymentIntent> findByStatusAndCreatedAtBefore(@Param("status") PaymentIntentStatus status, @Param("cutoffTime") OffsetDateTime cutoffTime);

    @Query("SELECT p FROM PaymentIntent p WHERE p.status = :status AND p.createdAt < :cutoffTime")
    org.springframework.data.domain.Page<PaymentIntent> findByStatusAndCreatedAtBefore(@Param("status") PaymentIntentStatus status, @Param("cutoffTime") OffsetDateTime cutoffTime, org.springframework.data.domain.Pageable pageable);

    /**
     * Intents stuck in a transient state. OrderRefundService sets REFUND_PENDING when it enqueues the
     * refund and only sets REFUND_FAILED if that enqueue throws, which it does not -- so a refund that
     * is never completed downstream stays REFUND_PENDING forever and no status-based sweep sees it.
     *
     * <p>Derived from the method name deliberately -- no {@code @Query}. An annotation placed above
     * this method would bind to it rather than to whatever it was written for, which is exactly how
     * the ':minTime' binding failure below was introduced.
     */
    org.springframework.data.domain.Page<PaymentIntent> findByStatusAndUpdatedAtBefore(PaymentIntentStatus status, OffsetDateTime cutoffTime, org.springframework.data.domain.Pageable pageable);

    // Half-open on purpose: BETWEEN would include cutoffTime, so the derived query cannot express this.
    @Query("SELECT p FROM PaymentIntent p WHERE p.status = :status AND p.createdAt >= :minTime AND p.createdAt < :cutoffTime")
    org.springframework.data.domain.Page<PaymentIntent> findByStatusAndCreatedAtBetween(@Param("status") PaymentIntentStatus status, @Param("minTime") OffsetDateTime minTime, @Param("cutoffTime") OffsetDateTime cutoffTime, org.springframework.data.domain.Pageable pageable);
}
