package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.common.entity.IdempotencyKey;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final String FIELD_ORDER_ID = "orderId";
    private static final String FIELD_GATEWAY_ORDER_ID = "gatewayOrderId";
    private static final String FIELD_EVENT_TYPE = "eventType";
    private static final String FIELD_FAILURE_REASON = "failureReason";
    private static final String REFUND_TX_PREFIX = "REFUND_";
    private static final UUID PLATFORM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderRefundService orderRefundService;



    @KafkaListener(topics = KafkaConstants.TOPIC_PAYMENT_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY + "-paymenteventconsumer")
    public void handlePaymentEvents(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        log.info("Received Payment Event: {}", payload);
        String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        if (extractedEventId == null) {
            throw new IllegalArgumentException("Missing eventId header");
        }
        final String resolvedEventId = extractedEventId;
        try {
            int retries = 0;
            boolean success = false;
            while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        String idempotencyKeyStr = "processed_event:payment:" + resolvedEventId;
                        if (idempotencyKeyRepository.existsById(idempotencyKeyStr)) {
                            log.info("Duplicate event ignored inside transaction: {}", resolvedEventId);
                            return;
                        }
                        idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKeyStr));
                        
                        Order orderToRefund = null;
                        try {
                            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
                            if (!rootNode.has(FIELD_ORDER_ID) || !rootNode.has(FIELD_GATEWAY_ORDER_ID)) {
                                log.info("Ignoring unrecognized event payload: {}", payload);
                                return;
                            }
                            String gatewayOrderId = rootNode.get(FIELD_GATEWAY_ORDER_ID).asText();
                            String internalOrderId = rootNode.get(FIELD_ORDER_ID).asText();
                            if (rootNode.has(FIELD_EVENT_TYPE) && com.fooddelivery.common.constants.EventType.PAYMENT_REFUNDED.name().equals(rootNode.get(FIELD_EVENT_TYPE).asText())) {
                                log.info("Processing PAYMENT_REFUNDED event for order: {}", internalOrderId);
                                com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
                                if (intent != null) {
                                    intent.setStatus(PaymentIntentStatus.REFUNDED);
                                    paymentIntentRepository.save(intent);
                                    Order order = orderRepository.findById(intent.getInternalOrderId()).orElse(null);
                                    if (order != null) {
                                        order.setPaymentStatus(PaymentIntentStatus.REFUNDED);
                                        java.math.BigDecimal currentRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : java.math.BigDecimal.ZERO;
                                        java.math.BigDecimal remainingToRefund = order.getTotalAmount().subtract(currentRefunded);
                                        if (remainingToRefund.compareTo(java.math.BigDecimal.ZERO) > 0) {
                                            order.setRefundedAmount(order.getTotalAmount());
                                            orderRepository.save(order);
                                            UUID refundTransferId = com.fooddelivery.common.util.DeterministicIdUtils.generateId(REFUND_TX_PREFIX + order.getId() + "_FULL");
                                            orderActionService.recordLedgerTransaction(refundTransferId, order.getId(), PLATFORM_ACCOUNT_ID, AccountType.PLATFORM, order.getCustomerId(), AccountType.CUSTOMER, remainingToRefund, com.fooddelivery.common.enums.ChargeCategory.REFUND);
                                            try {
                                                Map<String, Object> notifPayload = new HashMap<>();
                                                notifPayload.put("orderId", order.getId().toString());
                                                notifPayload.put("customerId", order.getCustomerId().toString());
                                                notifPayload.put("refundedAmount", remainingToRefund);
                                                OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST).payload(objectMapper.writeValueAsString(Map.of("template", "ORDER_REFUNDED", "data", notifPayload))).createdAt(LocalDateTime.now()).build();
                                                outboxEventRepository.save(outboxEvent);
                                            } catch (Exception e) {
                                                log.error("Failed to publish ORDER_REFUNDED notification", e);
                                            }

                                            if (rootNode.has("refundDestination") && "WALLET".equals(rootNode.get("refundDestination").asText())) {
                                                try {
                                                    com.fasterxml.jackson.databind.node.ObjectNode walletPayload = objectMapper.createObjectNode();
                                                    walletPayload.put("entityId", order.getCustomerId().toString());
                                                    walletPayload.put("entityType", "CUSTOMER");
                                                    walletPayload.put("amount", remainingToRefund.toString());
                                                    walletPayload.put("referenceId", "REFUND_" + order.getId());
                                                    walletPayload.put("description", "Wallet refund for order " + order.getId());
                                                    
                                                    com.fasterxml.jackson.databind.node.ObjectNode walletEvent = objectMapper.createObjectNode();
                                                    walletEvent.put("eventType", "REFUND_GENERATED");
                                                    walletEvent.set("payload", walletPayload);
                                                    
                                                    OutboxEventEntity walletOutbox = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.WALLET).aggregateId(order.getCustomerId().toString()).eventType(com.fooddelivery.common.constants.EventType.valueOf("REFUND_GENERATED")).payload(objectMapper.writeValueAsString(walletEvent)).createdAt(LocalDateTime.now()).build();
                                                    outboxEventRepository.save(walletOutbox);
                                                } catch (Exception e) {
                                                    log.error("Failed to publish REFUND_GENERATED event", e);
                                                }
                                            }
                                        }
                                    }
                                }
                                return;
                            }
                            if (rootNode.has(FIELD_EVENT_TYPE) && com.fooddelivery.common.constants.EventType.PAYMENT_PARTIALLY_REFUNDED.name().equals(rootNode.get(FIELD_EVENT_TYPE).asText())) {
                                log.info("Processing PAYMENT_PARTIALLY_REFUNDED event for order: {}", internalOrderId);
                                com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
                                if (intent != null) {
                                    intent.setStatus(PaymentIntentStatus.PARTIALLY_REFUNDED);
                                    paymentIntentRepository.save(intent);
                                    Order order = orderRepository.findById(intent.getInternalOrderId()).orElse(null);
                                    if (order != null) {
                                        order.setPaymentStatus(PaymentIntentStatus.PARTIALLY_REFUNDED);
                                        java.math.BigDecimal partialAmount = rootNode.has("amountRefunded") ? new java.math.BigDecimal(rootNode.get("amountRefunded").asText()) : order.getTotalAmount();
                                        java.math.BigDecimal currentRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : java.math.BigDecimal.ZERO;
                                        order.setRefundedAmount(currentRefunded.add(partialAmount));
                                        orderRepository.save(order);
                                        String uniqueSuffix = resolvedEventId != null ? resolvedEventId : String.valueOf(System.currentTimeMillis());
                                        UUID refundTransferId = com.fooddelivery.common.util.DeterministicIdUtils.generateId(REFUND_TX_PREFIX + "PARTIAL_" + order.getId() + "_" + uniqueSuffix);
                                        orderActionService.recordLedgerTransaction(refundTransferId, order.getId(), PLATFORM_ACCOUNT_ID, AccountType.PLATFORM, order.getCustomerId(), AccountType.CUSTOMER, partialAmount, com.fooddelivery.common.enums.ChargeCategory.REFUND);
                                        try {
                                            Map<String, Object> notifPayload = new HashMap<>();
                                            notifPayload.put("orderId", order.getId().toString());
                                            notifPayload.put("customerId", order.getCustomerId().toString());
                                            notifPayload.put("refundedAmount", partialAmount);
                                            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST).payload(objectMapper.writeValueAsString(Map.of("template", "ORDER_PARTIALLY_REFUNDED", "data", notifPayload))).createdAt(LocalDateTime.now()).build();
                                            outboxEventRepository.save(outboxEvent);
                                        } catch (Exception e) {
                                            log.error("Failed to publish ORDER_PARTIALLY_REFUNDED notification", e);
                                        }

                                        if (rootNode.has("refundDestination") && "WALLET".equals(rootNode.get("refundDestination").asText())) {
                                            try {
                                                com.fasterxml.jackson.databind.node.ObjectNode walletPayload = objectMapper.createObjectNode();
                                                walletPayload.put("entityId", order.getCustomerId().toString());
                                                walletPayload.put("entityType", "CUSTOMER");
                                                walletPayload.put("amount", partialAmount.toString());
                                                walletPayload.put("referenceId", "REFUND_PARTIAL_" + order.getId() + "_" + uniqueSuffix);
                                                walletPayload.put("description", "Partial wallet refund for order " + order.getId());
                                                
                                                com.fasterxml.jackson.databind.node.ObjectNode walletEvent = objectMapper.createObjectNode();
                                                walletEvent.put("eventType", "REFUND_GENERATED");
                                                walletEvent.set("payload", walletPayload);
                                                
                                                OutboxEventEntity walletOutbox = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.WALLET).aggregateId(order.getCustomerId().toString()).eventType(com.fooddelivery.common.constants.EventType.valueOf("REFUND_GENERATED")).payload(objectMapper.writeValueAsString(walletEvent)).createdAt(LocalDateTime.now()).build();
                                                outboxEventRepository.save(walletOutbox);
                                            } catch (Exception e) {
                                                log.error("Failed to publish REFUND_GENERATED event", e);
                                            }
                                        }
                                    }
                                }
                                return;
                            }
                            boolean isFailure = rootNode.has(FIELD_FAILURE_REASON) || (rootNode.has(FIELD_EVENT_TYPE) && com.fooddelivery.common.constants.EventType.PAYMENT_FAILED.name().equals(rootNode.get(FIELD_EVENT_TYPE).asText()));
                            log.info("Payment event for gateway order {}. Finding internal order {}. IsFailure: {}", gatewayOrderId, internalOrderId, isFailure);
                            com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
                            if (intent == null) {
                                log.warn("PaymentIntent for gateway order {} not found", gatewayOrderId);
                                return;
                            }
                            UUID orderUUID = intent.getInternalOrderId();
                            Order order = orderRepository.findById(orderUUID).orElse(null);
                            if (order == null) {
                                log.warn("Order {} not found, skipping status update", orderUUID);
                                return;
                            }
                            com.fooddelivery.order.service.state.OrderContext context = new com.fooddelivery.order.service.state.OrderContext(order, rootNode, orderActionService);
                            com.fooddelivery.order.service.state.OrderState state = com.fooddelivery.order.service.state.OrderStateFactory.getState(order.getStatus());
                            try {
                                if (isFailure) {
                                    state.handlePaymentFailure(context);
                                } else {
                                    state.handlePaymentSuccess(context);
                                }
                            } catch (com.fooddelivery.common.exception.IllegalStateTransitionException e) {
                                log.error("ILLEGAL_STATE_TRANSITION: {}", e.getMessage());
                            }
                            if (context.isRequiresRefund()) {
                                orderToRefund = order;
                            }
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to process payment event", e);
                        }
                        
                        if (orderToRefund != null) {
                            orderRefundService.processRefund(orderToRefund);
                        }
                    });
                    success = true;
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    log.warn("Optimistic locking failure in handlePaymentEvents, delegating to Kafka retry.");
                    throw e;
                } catch (Exception e) {
                    log.error("Error processing payment event payload", e);
                    throw new RuntimeException("Failed to process payment event", e);
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }
}
