package com.fooddelivery.order.scheduler;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.common.client.WalletServiceClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import feign.FeignException;

@Component
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
/**
 * <strong>@replication-safe: distributed-lock</strong> -- guarded by LOCK_SWEEP_STALE_CREATED_ORDERS.
 *
 * <p>Classification recorded 2026-08-27 (Phase 7). Every @Scheduled class in this workspace
 * carries one of these markers; the BOOT-SCHEDULE-CLASSIFIED check fails on a new one that
 * does not. Change the marker only after re-reading what the job actually does.
 */
public class UnpaidOrderCanceller {

    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final TransactionTemplate transactionTemplate;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final WalletServiceClient walletServiceClient;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    private final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;

    @Scheduled(fixedDelay = 60000)
    public void sweepStaleCreatedOrders() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_STALE_CREATED_ORDERS, "1", java.time.Duration.ofSeconds(50));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        
        // 1. Cancel stale CREATED orders
        org.springframework.data.domain.Page<Order> staleOrdersPage = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.CREATED, threshold, org.springframework.data.domain.PageRequest.of(0, 500));
        List<Order> staleOrders = staleOrdersPage.getContent();
        if (!staleOrders.isEmpty()) {
            log.info("Found {} stale CREATED orders.", staleOrders.size());
            for (Order order : staleOrders) {
                processStaleCreatedOrder(order);
            }
        }

        // 2. Cancel stale PENDING_ACCEPTANCE orders
        // PENDING_ACCEPTANCE is RestaurantTimeoutSweeper's, at 10 minutes, as CANCELLED_BY_RESTAURANT
        // with RESTAURANT_FAULT. This class used to sweep the same state at 15 minutes through
        // cancelOrderLocally -- producing CANCELLED with FaultType.UNKNOWN and telling the
        // restaurant the *customer* had cancelled. Whichever fired first decided who was at fault.
        // One condition, one sweeper.
    }

    private void processStaleCreatedOrder(Order order) {
        if (order.getPaymentMethod() == PaymentMethod.WALLET) {
            java.util.Optional<PaymentIntent> intentOpt = paymentIntentRepository.findByInternalOrderId(order.getId());
            if (intentOpt.isPresent()) {
                PaymentIntent intent = intentOpt.get();
                if (intent.getGatewayOrderId() != null && intent.getGatewayOrderId().startsWith("INTERNAL_")) {
                    UUID refId = UUID.fromString(intent.getGatewayOrderId().replace("INTERNAL_", ""));
                    try {
                        walletServiceClient.getTransactionByReference(refId);
                        // If we got here, the transaction EXISTS! The wallet was debited.
                        log.warn("Sweeper: Wallet WAS debited for order {}, emitting PAYMENT_COMPLETED outbox event to resume flow", order.getId());
                        emitPaymentCompleted(order, intent.getGatewayOrderId());
                        return; // Do not cancel the order!
                    } catch (FeignException.NotFound e) {
                        log.info("Sweeper: Wallet transaction not found for order {}, safe to cancel.", order.getId());
                    } catch (Exception e) {
                        log.error("Sweeper: Failed to verify wallet transaction for order {}, skipping cancellation to be safe.", order.getId(), e);
                        return; // Skip cancellation, might be a network error
                    }
                }
            }
        }

        // Cancel the order
        try {
            orderSagaOrchestrator.cancelOrderLocally(order, "Payment timeout after 15 minutes");
        } catch (Exception e) {
            log.error("Failed to cancel stale order {}", order.getId(), e);
        }
    }

    private void emitPaymentCompleted(Order order, String intentGatewayOrderId) {
        transactionTemplate.executeWithoutResult(status -> {
            String payload = String.format("{\"eventType\":\"PAYMENT_COMPLETED\", \"orderId\":\"%s\", \"gatewayOrderId\":\"%s\"}", order.getId(), intentGatewayOrderId);
            com.fooddelivery.common.outbox.entity.OutboxEventEntity evt = com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.PAYMENT).aggregateId(intentGatewayOrderId).eventType(com.fooddelivery.common.constants.EventType.PAYMENT_COMPLETED).payload(payload).createdAt(java.time.LocalDateTime.now()).build();
            outboxEventRepository.save(evt);
        });
    }
}
