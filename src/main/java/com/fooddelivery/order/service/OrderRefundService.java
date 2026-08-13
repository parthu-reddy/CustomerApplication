package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderRefundService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderRefundService.class);
    private static final String REFUND_TX_PREFIX = "REFUND_";
    private static final UUID PLATFORM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderActionService orderActionService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public OrderRefundService(IPaymentIntentRepository paymentIntentRepository,
                              IOrderRepository orderRepository,
                              OutboxEventRepository outboxEventRepository,
                              OrderActionService orderActionService,
                              TransactionTemplate transactionTemplate,
                              ObjectMapper objectMapper) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.orderActionService = orderActionService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @io.micrometer.core.annotation.Timed(value = "order.saga.refund.process", description = "Time taken to process full refund")
    public void processRefund(Order order) {
        processRefund(order, com.fooddelivery.common.enums.RefundDestination.GATEWAY);
    }

    public void processRefund(Order order, com.fooddelivery.common.enums.RefundDestination refundDestination) {
        processRefund(order, refundDestination, com.fooddelivery.common.enums.FaultType.UNKNOWN);
    }

    public void processRefund(Order order, com.fooddelivery.common.enums.RefundDestination refundDestination, com.fooddelivery.common.enums.FaultType faultType) {
        log.info("Processing refund for Order {} to {}", order.getId(), refundDestination);
        
        try {
            transactionTemplate.executeWithoutResult(status -> {
                com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByInternalOrderIdForUpdate(order.getId())
                    .orElseThrow(() -> new IllegalStateException("PaymentIntent not found for internalOrderId " + order.getId() + ". Cannot process refund."));
                    
                java.math.BigDecimal currentRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal remainingRefundable = order.getTotalAmount().subtract(currentRefunded);
                
                if (remainingRefundable.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    log.info("Order {} is already fully refunded. Skipping.", order.getId());
                    return;
                }
                
                if (intent.getStatus() == PaymentIntentStatus.SUCCESS || intent.getStatus() == PaymentIntentStatus.CAPTURED || intent.getStatus() == PaymentIntentStatus.PARTIALLY_REFUNDED || intent.getStatus() == PaymentIntentStatus.REFUND_FAILED) {
                    try {
                        Map<String, Object> payloadMap = new HashMap<>();
                        payloadMap.put("intentId", intent.getId().toString());
                        payloadMap.put("gatewayOrderId", intent.getGatewayOrderId());
                        payloadMap.put("amountInInr", remainingRefundable);
                        payloadMap.put("gatewayName", intent.getGatewayName());
                        payloadMap.put("orderId", order.getId().toString());
                        payloadMap.put("refundDestination", refundDestination != null ? refundDestination.name() : "GATEWAY");
                        String payloadStr = objectMapper.writeValueAsString(payloadMap);
                        OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.PAYMENT).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.PAYMENT_REFUND_REQUESTED).payload(payloadStr).createdAt(LocalDateTime.now()).build();
                        outboxEventRepository.save(outboxEvent);
                        
                        // Handle Post-Delivery Reversals
                        Order latestOrder = orderRepository.findById(order.getId()).orElse(order);
                        if (latestOrder.getDeliveryStatus() == com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
                            UUID reversalId = UUID.randomUUID();
                            Map<String, Object> reversalPayload = new HashMap<>();
                            reversalPayload.put("reversalId", reversalId.toString());
                            reversalPayload.put("orderId", order.getId().toString());
                            reversalPayload.put("amount", remainingRefundable);
                            reversalPayload.put("faultType", faultType != null ? faultType.name() : "UNKNOWN");
                            String reversalStr = objectMapper.writeValueAsString(reversalPayload);
                            OutboxEventEntity reversalEvent = OutboxEventEntity.builder()
                                .id(reversalId)
                                .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                                .aggregateId(order.getId().toString())
                                .eventType(com.fooddelivery.common.constants.EventType.valueOf("LEDGER_REVERSAL_REQUEST")) 
                                .payload(reversalStr)
                                .createdAt(LocalDateTime.now())
                                .build();
                            outboxEventRepository.save(reversalEvent);
                            log.info("Emitted LEDGER_REVERSAL_REQUEST for delivered order {}", order.getId());
                        }

                        latestOrder.setPaymentStatus(PaymentIntentStatus.REFUND_PENDING);
                        orderRepository.save(latestOrder);
                        
                        intent.setStatus(PaymentIntentStatus.REFUND_PENDING);
                        paymentIntentRepository.save(intent);
                    } catch (Exception e) {
                        log.error("Failed to enqueue payment refund event for order {}", order.getId(), e);
                        markRefundFailedWithRetry(intent);
                        throw new RuntimeException(e); // Rollback tx
                    }
                } else {
                    log.warn("Cannot refund PaymentIntent {} in status {}", intent.getId(), intent.getStatus());
                }
            });
            io.micrometer.core.instrument.Metrics.counter("order.saga.refund.process.success").increment();
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process refund for order {}", order.getId(), e);
            io.micrometer.core.instrument.Metrics.counter("order.saga.refund.process.failure").increment();
            throw e; 
        }
    }

    @io.micrometer.core.annotation.Timed(value = "order.saga.refund.process.partial", description = "Time taken to process partial refund")
    public void processPartialRefund(Order order, java.math.BigDecimal partialAmount) {
        processPartialRefund(order, partialAmount, com.fooddelivery.common.enums.RefundDestination.GATEWAY);
    }

    public void processPartialRefund(Order order, java.math.BigDecimal partialAmount, com.fooddelivery.common.enums.RefundDestination refundDestination) {
        processPartialRefund(order, partialAmount, refundDestination, com.fooddelivery.common.enums.FaultType.UNKNOWN);
    }

    public void processPartialRefund(Order order, java.math.BigDecimal partialAmount, com.fooddelivery.common.enums.RefundDestination refundDestination, com.fooddelivery.common.enums.FaultType faultType) {
        log.info("Processing partial refund for Order {} to {}", order.getId(), refundDestination);
        if (partialAmount == null || partialAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Partial refund amount must be greater than zero");
        }
        
        try {
            transactionTemplate.executeWithoutResult(status -> {
                com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByInternalOrderIdForUpdate(order.getId())
                    .orElseThrow(() -> new IllegalStateException("PaymentIntent not found for internalOrderId " + order.getId() + ". Cannot process partial refund."));

                java.math.BigDecimal currentRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal remainingRefundable = order.getTotalAmount().subtract(currentRefunded);
                if (partialAmount.compareTo(remainingRefundable) > 0) {
                    throw new IllegalArgumentException("Partial refund amount " + partialAmount + " exceeds remaining refundable balance " + remainingRefundable);
                }

                if (intent.getStatus() == PaymentIntentStatus.SUCCESS || intent.getStatus() == PaymentIntentStatus.CAPTURED || intent.getStatus() == PaymentIntentStatus.PARTIALLY_REFUNDED || intent.getStatus() == PaymentIntentStatus.REFUND_FAILED) {
                    try {
                        Map<String, Object> payloadMap = new HashMap<>();
                        payloadMap.put("intentId", intent.getId().toString());
                        payloadMap.put("gatewayOrderId", intent.getGatewayOrderId());
                        payloadMap.put("amountInInr", partialAmount);
                        payloadMap.put("gatewayName", intent.getGatewayName());
                        payloadMap.put("orderId", order.getId().toString());
                        payloadMap.put("refundDestination", refundDestination != null ? refundDestination.name() : "GATEWAY");
                        String payloadStr = objectMapper.writeValueAsString(payloadMap);
                        OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.PAYMENT).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.PAYMENT_REFUND_REQUESTED).payload(payloadStr).createdAt(LocalDateTime.now()).build();
                        outboxEventRepository.save(outboxEvent);

                        Order latestOrder = orderRepository.findById(order.getId()).orElse(order);
                        if (latestOrder.getDeliveryStatus() == com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
                            UUID reversalId = UUID.randomUUID();
                            Map<String, Object> reversalPayload = new HashMap<>();
                            reversalPayload.put("reversalId", reversalId.toString());
                            reversalPayload.put("orderId", order.getId().toString());
                            reversalPayload.put("amount", partialAmount); 
                            reversalPayload.put("faultType", faultType != null ? faultType.name() : "UNKNOWN");
                            String reversalStr = objectMapper.writeValueAsString(reversalPayload);
                            OutboxEventEntity reversalEvent = OutboxEventEntity.builder()
                                .id(reversalId)
                                .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                                .aggregateId(order.getId().toString())
                                .eventType(com.fooddelivery.common.constants.EventType.valueOf("LEDGER_REVERSAL_REQUEST")) 
                                .payload(reversalStr)
                                .createdAt(LocalDateTime.now())
                                .build();
                            outboxEventRepository.save(reversalEvent);
                            log.info("Emitted partial LEDGER_REVERSAL_REQUEST for delivered order {}", order.getId());
                        }

                        latestOrder.setPaymentStatus(PaymentIntentStatus.REFUND_PENDING);
                        orderRepository.save(latestOrder);
                        
                        intent.setStatus(PaymentIntentStatus.REFUND_PENDING);
                        paymentIntentRepository.save(intent);
                    } catch (Exception e) {
                        log.error("Failed to enqueue payment refund event for order {}", order.getId(), e);
                        markRefundFailedWithRetry(intent);
                        throw new RuntimeException(e); // Rollback tx
                    }
                } else {
                    log.warn("Cannot refund PaymentIntent {} in status {}", intent.getId(), intent.getStatus());
                }
            });
            io.micrometer.core.instrument.Metrics.counter("order.saga.refund.partial.success").increment();
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process partial refund for order {}", order.getId(), e);
            io.micrometer.core.instrument.Metrics.counter("order.saga.refund.partial.failure").increment();
            throw e;
        }
    }

    private void markRefundFailedWithRetry(com.fooddelivery.order.entity.PaymentIntent intent) {
        int retries = 0;
        boolean success = false;
        while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    com.fooddelivery.order.entity.PaymentIntent latestIntent = paymentIntentRepository.findById(intent.getId()).orElse(intent);
                    latestIntent.setStatus(PaymentIntentStatus.REFUND_FAILED);
                    paymentIntentRepository.save(latestIntent);
                });
                success = true;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                retries++;
                if (retries >= AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                    log.error("CRITICAL: Failed to mark PaymentIntent {} as REFUND_FAILED after {} retries", intent.getId(), AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES, e);
                    return; 
                }
                log.warn("Optimistic locking failure while marking REFUND_FAILED for intent {}. Retrying {}/{}", intent.getId(), retries, AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES);
                try {
                    Thread.sleep((long) (Math.pow(2, retries) * 100));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                log.error("Failed to mark PaymentIntent {} as REFUND_FAILED", intent.getId(), e);
                return;
            }
        }
    }
}
