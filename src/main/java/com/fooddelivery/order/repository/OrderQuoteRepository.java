package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.OrderQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface OrderQuoteRepository extends JpaRepository<OrderQuote, UUID> {

    /**
     * Claims a quote for exactly one order.
     *
     * <p>The unconsumed-and-unexpired test is part of the UPDATE rather than a preceding read, so
     * two concurrent checkouts on one quote cannot both pass validation and then both charge. The
     * loser gets 0 rows back.
     *
     * @return 1 if this caller claimed the quote, 0 if it was already consumed or has expired.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderQuote q SET q.consumedAt = :now, q.consumedOrderId = :orderId "
            + "WHERE q.id = :quoteId AND q.consumedAt IS NULL AND q.expiresAt > :now")
    int claim(@Param("quoteId") UUID quoteId,
              @Param("orderId") UUID orderId,
              @Param("now") LocalDateTime now);

    /** Housekeeping for quotes that expired without being redeemed. */
    @Modifying
    @Query("DELETE FROM OrderQuote q WHERE q.expiresAt < :cutoff AND q.consumedAt IS NULL")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
