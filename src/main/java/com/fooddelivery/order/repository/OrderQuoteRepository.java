package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.OrderQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
     * <p>{@code @Transactional} is required, not decorative: a {@code @Modifying} @Query gets no
     * transaction from SimpleJpaRepository, and this runs on an executor thread inside
     * {@code CompletableFuture.supplyAsync} where no caller transaction propagates. Without it
     * every checkout fails with "No EntityManager with actual transaction available".
     *
     * @return 1 if this caller claimed the quote, 0 if it was already consumed or has expired.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderQuote q SET q.consumedAt = :now, q.consumedOrderId = :orderId "
            + "WHERE q.id = :quoteId AND q.consumedAt IS NULL AND q.expiresAt > :now")
    int claim(@Param("quoteId") UUID quoteId,
              @Param("orderId") UUID orderId,
              @Param("now") Instant now);

    /** Housekeeping for quotes that expired without being redeemed. */
    @Transactional
    @Modifying
    @Query("DELETE FROM OrderQuote q WHERE q.expiresAt < :cutoff AND q.consumedAt IS NULL")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
