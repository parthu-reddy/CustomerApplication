package com.fooddelivery.order.scheduler;

import com.fooddelivery.order.refund.RefundService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
/**
 * <strong>@replication-safe: distributed-lock</strong> -- guarded by a Redis lock before any refund is re-enqueued.
 *
 * <p>Classification recorded 2026-08-27 (Phase 7). Every @Scheduled class in this workspace
 * carries one of these markers; the BOOT-SCHEDULE-CLASSIFIED check fails on a new one that
 * does not. Change the marker only after re-reading what the job actually does.
 */
public class RefundRetrySweeper {
    
    private final RefundService refundService;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 300000)
    public void retryFailedRefunds() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_REFUND_RETRIES, "1", java.time.Duration.ofSeconds(200));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        
        try {
            log.info("Running RefundRetrySweeper");
            refundService.retryStuck();
            log.info("RefundRetrySweeper complete");
        } catch (Exception e) {
            log.error("Error in RefundRetrySweeper", e);
        }
    }
}
