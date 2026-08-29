package com.fooddelivery.order.scheduler;

import com.fooddelivery.order.repository.OrderQuoteRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;

@Component
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
/**
 * <strong>@replication-safe: idempotent</strong> -- deletes unredeemed quotes past a cutoff and
 * nothing else. Two replicas running it concurrently delete the same rows; the second finds none.
 * No lock is needed and adding one would only serialise a cheap delete.
 *
 * <p>Classification recorded 2026-08-29. Every @Scheduled class in this workspace carries one of
 * these markers; the BOOT-SCHEDULE-CLASSIFIED check fails on a new one that does not. Change the
 * marker only after re-reading what the job actually does.
 *
 * <p>Redeemed quotes are deliberately left in place: a quote is the evidence of what a customer was
 * shown, and its id is recorded on the order it priced.
 */
public class ExpiredQuoteSweeper {

    /** Kept well past expiry so a support query about a quote the customer abandoned still resolves. */
    private static final int RETAIN_EXPIRED_HOURS = 24;

    private final OrderQuoteRepository orderQuoteRepository;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${quotes.sweeper.fixed-delay-ms:3600000}")
    public void purgeExpiredQuotes() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(RETAIN_EXPIRED_HOURS);
        Integer deleted = transactionTemplate.execute(status -> orderQuoteRepository.deleteExpiredBefore(cutoff));
        if (deleted != null && deleted > 0) {
            log.info("Purged {} unredeemed order quotes that expired before {}", deleted, cutoff);
        }
    }
}
