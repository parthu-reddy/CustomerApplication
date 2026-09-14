package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.KafkaConstants;
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


    private static final String REFUND_TX_PREFIX = "REFUND_";

    
    private final com.fooddelivery.common.event.EventBinder eventBinder;

    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final OutboxEventRepository outboxEventRepository;
    private final com.fooddelivery.order.refund.RefundService refundService;
    private final com.fooddelivery.order.ledger.LedgerBookkeeper ledgerBookkeeper;



    @KafkaListener(topics = KafkaConstants.TOPIC_PAYMENT_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY + "-paymenteventconsumer")
    public void handlePaymentEvents(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        log.info("Received Payment Event: {}", payload);
        String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        if (extractedEventId == null) {
            throw new IllegalArgumentException("Missing eventId header");
        }
        final String resolvedEventId = extractedEventId;
        // No retry loop here. This used to read
        //     int retries = 0; boolean success = false;
        //     while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) { ... }
        // with `retries` never incremented and every catch rethrowing, so the body ran exactly once
        // by construction while reading as if it retried three times. Retrying is the listener's
        // job: the optimistic-lock catch below rethrows so the retry topic handles it.
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
                    String eventTypeStr = com.fooddelivery.common.util.KafkaHeaderUtils.extractEventType(headers, rootNode);
                    
                    if (com.fooddelivery.common.constants.EventType.PAYMENT_REFUNDED.name().equals(eventTypeStr)) {
                        log.info("Processing PAYMENT_REFUNDED event");
                        com.fooddelivery.common.event.PaymentRefundedEvent refundedEvent = eventBinder.bindIf(
                            com.fooddelivery.common.constants.EventType.PAYMENT_REFUNDED, eventTypeStr, payload, com.fooddelivery.common.event.PaymentRefundedEvent.class).orElse(null);
                            
                        if (refundedEvent != null && refundedEvent.getRefundId() != null && refundedEvent.getIsSuccess() != null) {
                            UUID refundId = UUID.fromString(refundedEvent.getRefundId());
                            if (refundedEvent.getIsSuccess()) {
                                refundService.complete(refundId, refundedEvent.getGatewayRefundId());
                            } else {
                                refundService.fail(refundId, refundedEvent.getFailureReason() != null ? refundedEvent.getFailureReason() : "Unknown failure");
                            }
                        } else {
                            log.warn("PAYMENT_REFUNDED event missing refundId or isSuccess");
                        }
                        return;
                    }
                    
                    if (com.fooddelivery.common.constants.EventType.PAYMENT_PARTIALLY_REFUNDED.name().equals(eventTypeStr)) {
                        log.warn("Ignoring deprecated PAYMENT_PARTIALLY_REFUNDED event");
                        return;
                    }
                    
                    String gatewayOrderId = null;
                    String internalOrderId = null;
                    boolean isFailure = false;
                    Object typedEvent = null;

                    // Fallback infer failure if eventType is null but we can parse it as PaymentFailedEvent
                    if (com.fooddelivery.common.constants.EventType.PAYMENT_FAILED.name().equals(eventTypeStr) || (eventTypeStr == null && payload.contains("\"failureReason\""))) {
                        isFailure = true;
                        com.fooddelivery.common.event.PaymentFailedEvent ev = eventBinder.bindIf(
                            com.fooddelivery.common.constants.EventType.PAYMENT_FAILED, com.fooddelivery.common.constants.EventType.PAYMENT_FAILED.name(), payload, com.fooddelivery.common.event.PaymentFailedEvent.class).orElse(null);
                        if (ev != null) {
                            typedEvent = ev;
                            gatewayOrderId = ev.gatewayOrderId();
                            internalOrderId = ev.orderId() != null ? ev.orderId().toString() : null;
                        }
                    } else {
                        com.fooddelivery.common.event.PaymentSucceededEvent ev = eventBinder.bindIf(
                            com.fooddelivery.common.constants.EventType.PAYMENT_COMPLETED, com.fooddelivery.common.constants.EventType.PAYMENT_COMPLETED.name(), payload, com.fooddelivery.common.event.PaymentSucceededEvent.class).orElse(null);
                        if (ev != null) {
                            typedEvent = ev;
                            gatewayOrderId = ev.gatewayOrderId();
                            internalOrderId = ev.orderId();
                        }
                    }

                    if (gatewayOrderId == null || internalOrderId == null) {
                        log.info("Ignoring unrecognized event payload (missing orderId or gatewayOrderId): {}", payload);
                        return;
                    }

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
                    com.fooddelivery.order.service.state.OrderContext context = new com.fooddelivery.order.service.state.OrderContext(
                            // null, not typedEvent: OrderContext's payload is typed to
                            // OrderScopedEvent, and payment events are not order-scoped --
                            // PaymentSucceededEvent's orderId can be the WALLET_<id> form, which is
                            // not an order UUID at all. Nothing is lost: no state reached from
                            // handlePaymentSuccess or handlePaymentFailure reads the payload
                            // (verified across every OrderState implementation). A payment-path
                            // state that needs the event should take it as a parameter rather than
                            // widening this field back to Object.
                            order, null, orderActionService, ledgerBookkeeper,
                            intent != null && intent.getGatewayName() != null ? intent.getGatewayName().name() : null,
                            intent.getPaymentMethod());
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
                     com.fooddelivery.order.refund.RefundCommand cmd = com.fooddelivery.order.refund.RefundCommand.builder()
                        .orderId(orderToRefund.getId())
                        .amount(orderToRefund.getTotalAmount())
                        .faultType(com.fooddelivery.order.enums.FaultType.UNKNOWN)
                        // No destination: RefundService routes it from the payment method
                        // and intent state.
                        .initiatorType(com.fooddelivery.order.enums.InitiatorType.SYSTEM)
                        .reasonCode("SYSTEM_AUTO_REFUND")
                        .idempotencyKey("payment_fail_" + orderToRefund.getId())
                        .build();
                     try {
                         refundService.request(cmd);
                     } catch (IllegalStateException e) {
                         // See OrderEventConsumer: a routing refusal must not unwind the
                         // payment-state transition and re-poison the partition.
                         log.error("REFUND_NOT_ROUTED: order {} needs a refund but it could not "
                                 + "be routed ({}). The payment state change is kept; resolve "
                                 + "this from the admin refund queue.", orderToRefund.getId(), e.getMessage());
                     }
                }
            });
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure in handlePaymentEvents, delegating to Kafka retry.");
            throw e;
        } catch (Exception e) {
            log.error("Error processing payment event payload", e);
            throw new RuntimeException("Failed to process payment event", e);
        }
    }
}
