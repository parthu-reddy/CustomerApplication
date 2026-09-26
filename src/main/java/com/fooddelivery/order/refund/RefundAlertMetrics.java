package com.fooddelivery.order.refund;

import com.fooddelivery.order.repository.RefundRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Feeds {@code refund_processing_time_hours}, which the RefundStuck alert reads.
 *
 * <p>The metric previously existed only as a constant zero registered in LedgerService so that a
 * static check comparing alert expressions against the source would pass. Nothing ever set it, so
 * the alert was permanently silent while looking configured.
 *
 * <p><strong>@replication-safe: idempotent</strong> -- reads one aggregate and publishes it as a
 * gauge. Every replica exporting its own view of the same query is correct, and Prometheus
 * scrapes them all; a lock would leave every replica but one reporting a stale zero.
 */
@Component
@Slf4j
public class RefundAlertMetrics {

    private final RefundRepository refundRepository;
    private final AtomicLong oldestInFlightHours = new AtomicLong(0);

    public RefundAlertMetrics(RefundRepository refundRepository, MeterRegistry meterRegistry) {
        this.refundRepository = refundRepository;
        Gauge.builder("refund_processing_time_hours", oldestInFlightHours, AtomicLong::doubleValue)
             .description("Age in hours of the oldest refund still REQUESTED or PROCESSING")
             .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${money.alert-metrics.refresh-ms:60000}")
    public void refresh() {
        try {
            oldestInFlightHours.set(oldestInFlightAgeInHours());
        } catch (Exception e) {
            log.warn("Could not refresh refund alert metrics: {}", e.getMessage());
        }
    }

    long oldestInFlightAgeInHours() {
        Instant oldest = refundRepository.findOldestInFlightCreatedAt();
        if (oldest == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(oldest, Instant.now()).toHours());
    }
}
