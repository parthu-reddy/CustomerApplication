package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AggregateType;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OutboxStatus;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.entity.RefundItem;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.repository.RefundItemRepository;
import com.fooddelivery.order.repository.RefundRepository;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.event.PaymentRefundRequestedEvent;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.common.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;
    private final IOrderRepository orderRepository;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final LedgerBookkeeper ledgerBookkeeper;
    private final ObjectMapper objectMapper;
    private final RefundItemRepository refundItemRepository;

    @Transactional
    public RefundView request(RefundCommand command) {
        Optional<Refund> existing = refundRepository.findByIdempotencyKey(command.getIdempotencyKey());
        if (existing.isPresent()) {
            Order order = orderRepository.findById(existing.get().getOrderId()).orElseThrow();
            return toView(existing.get(), order);
        }

        PaymentIntent intent = paymentIntentRepository.findByInternalOrderIdForUpdate(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found"));

        Order order = orderRepository.findById(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        BigDecimal committed = refundRepository.sumByOrderAndStatusIn(command.getOrderId(),
                List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING, RefundStatus.COMPLETED));
        BigDecimal remaining = intent.getAmount().subtract(committed);
        if (command.getAmount().compareTo(remaining) > 0) {
            throw new IllegalStateException("REFUND_EXCEEDS_REMAINING");
        }

        RefundDestination destination = resolveDestination(command, intent);

        Refund refund = Refund.builder()
                .id(UUID.randomUUID())
                .orderId(order.getId())
                .paymentIntentId(intent.getId())
                .amount(command.getAmount())
                .currency("INR")
                .reasonCode(command.getReasonCode())
                .reasonText(command.getReasonText())
                .faultType(command.getFaultType())
                .destination(destination)
                .source(command.getSource())
                .initiatedByType(command.getInitiatorType())
                .initiatedById(command.getInitiatorId())
                .status(RefundStatus.REQUESTED)
                .ticketId(command.getTicketId())
                .idempotencyKey(command.getIdempotencyKey())
                .build();

        final Refund finalRefund = refund;
        if (command.getItems() != null) {
            Set<RefundItem> items = command.getItems().stream().map(i -> {
                RefundItem item = new RefundItem();
                item.setId(UUID.randomUUID());
                item.setRefund(finalRefund);
                item.setOrderItemId(i.getOrderItemId());
                item.setQuantity(i.getQuantity());
                return item;
            }).collect(Collectors.toSet());
            refund.setRefundItems(items);
        }

        refund = refundRepository.save(refund);

        sendNotification(order, refund);

        if (destination == RefundDestination.ORIGINAL_METHOD) {
            refund.setStatus(RefundStatus.PROCESSING);
            PaymentRefundRequestedEvent event = PaymentRefundRequestedEvent.builder()
                    .refundId(refund.getId().toString())
                    .orderId(order.getId().toString())
                    .gatewayOrderId(intent.getGatewayOrderId())
                    .amount(refund.getAmount())
                    .gatewayName(intent.getGatewayName())
                    .build();
            try {
                OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                        .id(UUID.randomUUID())
                        .aggregateType(AggregateType.PAYMENT)
                        .aggregateId(intent.getId().toString())
                        .eventType(EventType.PAYMENT_REFUND_REQUESTED)
                        .payload(objectMapper.writeValueAsString(event))
                        .createdAt(Instant.now())
                        .status(OutboxStatus.UNPROCESSED)
                        .build();
                outboxEventRepository.save(outboxEvent);
            } catch (Exception e) {
                throw new RuntimeException("Failed to save outbox event", e);
            }
        } else if (destination == RefundDestination.STORE_CREDIT) {
            // Handed off, not called. The wallet credit must not happen inside this transaction: a
            // rollback after a successful Feign call left the money credited with no refund row, and
            // the retry generated a new refund id, so the wallet's entity-scoped idempotency (keyed
            // on the refund id) could not recognise it as the same credit. The refund stays
            // PROCESSING until WalletService reports back, exactly as the gateway path does.
            refund.setStatus(RefundStatus.PROCESSING);
            enqueueWalletCredit(refund, order, intent);
        } else if (destination == RefundDestination.NONE) {
            completeInternal(refund, null, order);
        }

        return toView(refund, order);
    }

    /**
     * Asks WalletService to credit the customer, through the outbox.
     *
     * <p>Carries {@code orderId} and {@code gatewayOrderId} because the completion comes back as a
     * {@code PAYMENT_REFUNDED} event, and {@code PaymentEventConsumer} requires both fields before
     * it will look at one.
     */
    private void enqueueWalletCredit(Refund refund, Order order, PaymentIntent intent) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("eventType", EventType.WALLET_CREDIT_REQUESTED.name());
            payload.put("refundId", refund.getId().toString());
            payload.put("orderId", order.getId().toString());
            payload.put("gatewayOrderId", intent.getGatewayOrderId());
            payload.put("customerId", order.getCustomerId().toString());
            payload.put("amount", refund.getAmount().toPlainString());

            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.WALLET)
                    .aggregateId(order.getCustomerId().toString())
                    .eventType(EventType.WALLET_CREDIT_REQUESTED)
                    // The refund id is stable across retries of this request, so a redelivery
                    // cannot enqueue a second credit for the same refund.
                    .idempotencyKey("wallet_credit:" + refund.getId())
                    .payload(objectMapper.writeValueAsString(payload))
                    .createdAt(Instant.now())
                    .status(OutboxStatus.UNPROCESSED)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue store-credit refund " + refund.getId(), e);
        }
    }

    /**
     * Where the money goes. This method is the only authority on that question.
     *
     * <p>Callers used to pass a destination of their own -- six system paths hardcoded
     * {@code ORIGINAL_METHOD} and the chat path hardcoded {@code STORE_CREDIT} -- which bypassed the
     * matrix entirely: a wallet-paid order was pushed at a gateway that had never taken the money,
     * while a card-paid customer was handed store credit. Automatic callers must pass no
     * destination and take the routing below.
     *
     * <p>The single permitted override is an administrator granting store credit as goodwill. It is
     * recorded against that administrator via {@code initiatedById}.
     */
    private RefundDestination resolveDestination(RefundCommand command, PaymentIntent intent) {
        RefundDestination override = command.getDestination();
        if (override != null) {
            if (override != RefundDestination.STORE_CREDIT) {
                throw new IllegalStateException("REFUND_OVERRIDE_INVALID");
            }
            if (command.getInitiatorType() != com.fooddelivery.order.enums.InitiatorType.ADMIN) {
                throw new IllegalStateException("REFUND_OVERRIDE_UNAUTHORIZED");
            }
            return RefundDestination.STORE_CREDIT;
        }

        PaymentMethod method = intent.getPaymentMethod();
        if (method == null) {
            throw new IllegalStateException("REFUND_METHOD_UNKNOWN");
        }
        // A gateway that told us the payment failed took no money, so there is none to give back.
        // This is a real outcome of cancelling an order, not an error.
        if (intent.getStatus() == PaymentIntentStatus.FAILED) {
            return RefundDestination.NONE;
        }
        switch (method) {
            case CARD:
            case UPI:
                if (intent.getStatus() == PaymentIntentStatus.SUCCESS
                        || intent.getStatus() == PaymentIntentStatus.PARTIALLY_REFUNDED) {
                    return RefundDestination.ORIGINAL_METHOD;
                }
                // INITIATED is deliberately not routed: we asked the gateway for money and do not
                // know whether it took any. Refunding nothing could strand a real capture, so an
                // operator must look.
                throw new IllegalStateException("REFUND_STATE_INVALID");
            case WALLET:
                if (intent.getStatus() == PaymentIntentStatus.SUCCESS
                        || intent.getStatus() == PaymentIntentStatus.PARTIALLY_REFUNDED) {
                    return RefundDestination.STORE_CREDIT;
                }
                throw new IllegalStateException("REFUND_STATE_INVALID");
            default:
                throw new IllegalStateException("REFUND_METHOD_UNKNOWN");
        }
    }

    @Transactional
    public void complete(UUID refundId, String gatewayRefundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found"));
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            return;
        }
        RefundStateMachine.validateTransition(refund.getStatus(), RefundStatus.COMPLETED);
        
        Order order = orderRepository.findById(refund.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
        PaymentIntent intent = paymentIntentRepository.findById(refund.getPaymentIntentId()).orElseThrow();
        BigDecimal committed = refundRepository.sumByOrderAndStatusIn(refund.getOrderId(), List.of(RefundStatus.COMPLETED));
        if (committed.add(refund.getAmount()).compareTo(intent.getAmount()) > 0) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason("OVER_REFUND_BLOCKED");
            refundRepository.save(refund);
            return;
        }
                
        completeInternal(refund, gatewayRefundId, order);
    }

    private void completeInternal(Refund refund, String gatewayRefundId, Order order) {
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setGatewayRefundId(gatewayRefundId);
        refund.setCompletedAt(java.time.Instant.now());
        String gatewayName = paymentIntentRepository.findById(refund.getPaymentIntentId())
                .map(PaymentIntent::getGatewayName)
                .map(Enum::name)
                .orElse(null);
        ledgerBookkeeper.bookRefund(order, refund, gatewayName);
        sendSuccessNotification(order, refund);
    }

    @Transactional
    public void fail(UUID refundId, String reason) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found"));
        if (refund.getStatus() == RefundStatus.FAILED) {
            return;
        }
        RefundStateMachine.validateTransition(refund.getStatus(), RefundStatus.FAILED);
        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason(reason);
        
        Order order = orderRepository.findById(refund.getOrderId()).orElseThrow();
        sendFailedNotification(order, refund);
    }
    
    @Transactional
    public void retryStuck() {
        List<Refund> stuck = refundRepository.findStuckProcessing(java.time.Instant.now().minus(java.time.Duration.ofMinutes(5)));
        for (Refund refund : stuck) {
            refund.setAttempts(refund.getAttempts() + 1);
            if (refund.getAttempts() > 3) {
                fail(refund.getId(), "Max retries exceeded");
            } else {
                refund.setUpdatedAt(java.time.Instant.now());
                PaymentIntent intent = paymentIntentRepository.findById(refund.getPaymentIntentId()).orElseThrow();
                PaymentRefundRequestedEvent event = PaymentRefundRequestedEvent.builder()
                        .refundId(refund.getId().toString())
                        .orderId(refund.getOrderId().toString())
                        .gatewayOrderId(intent.getGatewayOrderId())
                        .amount(refund.getAmount())
                        .gatewayName(intent.getGatewayName())
                        .build();
                try {
                    OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                            .id(UUID.randomUUID())
                            .aggregateType(AggregateType.PAYMENT)
                            .aggregateId(intent.getId().toString())
                            .eventType(EventType.PAYMENT_REFUND_REQUESTED)
                            .payload(objectMapper.writeValueAsString(event))
                            .createdAt(Instant.now())
                            .status(OutboxStatus.UNPROCESSED)
                            .build();
                    outboxEventRepository.save(outboxEvent);
                } catch (Exception e) {
                    log.error("Failed to enqueue retry", e);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal quote(UUID orderId, List<RefundCommand.Item> items) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (order.getItemTotal() == null || order.getItemTotal().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("QUOTE_UNAVAILABLE");
        }

        BigDecimal itemsRefundTotal = BigDecimal.ZERO;
        for (RefundCommand.Item itemCmd : items) {
            OrderItem item = order.getOrderItems().stream()
                .filter(i -> i.getId().equals(itemCmd.getOrderItemId()))
                .findFirst().orElseThrow();
            
            int alreadyRefunded = refundItemRepository.sumCompletedQuantity(itemCmd.getOrderItemId());
            if (itemCmd.getQuantity() + alreadyRefunded > item.getQuantity()) {
                throw new IllegalStateException("ITEM_ALREADY_REFUNDED");
            }

            itemsRefundTotal = itemsRefundTotal.add(item.getPrice().multiply(new BigDecimal(itemCmd.getQuantity())));
        }

        BigDecimal itemRatio = itemsRefundTotal.divide(order.getItemTotal(), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal proratedSgst = order.getSgst() != null ? order.getSgst().multiply(itemRatio) : BigDecimal.ZERO;
        BigDecimal proratedCgst = order.getCgst() != null ? order.getCgst().multiply(itemRatio) : BigDecimal.ZERO;
        return itemsRefundTotal.add(proratedSgst).add(proratedCgst).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    
    private void sendNotification(Order order, Refund refund) {
        try {
            NotificationRequestEvent notificationEvent = NotificationRequestEvent.builder()
                    .userId(order.getCustomerId())
                    .channel(ChannelType.PUSH)
                    .eventName(com.fooddelivery.common.constants.NotificationTemplate.REFUND_REQUESTED)
                    .templateParams(List.of(order.getId().toString(), refund.getAmount().toString(), refund.getDestination().name()))
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.NOTIFICATION)
                    .aggregateId(order.getCustomerId().toString())
                    .eventType(EventType.NOTIFICATION_REQUEST)
                    .payload(objectMapper.writeValueAsString(notificationEvent))
                    .createdAt(Instant.now())
                    .status(OutboxStatus.UNPROCESSED)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to save notification request to outbox", e);
        }
    }
    
    private void sendFailedNotification(Order order, Refund refund) {
        try {
            NotificationRequestEvent notificationEvent = NotificationRequestEvent.builder()
                    .userId(order.getCustomerId())
                    .channel(ChannelType.PUSH)
                    .eventName(com.fooddelivery.common.constants.NotificationTemplate.REFUND_FAILED)
                    .templateParams(List.of(order.getId().toString(), refund.getAmount().toString()))
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.NOTIFICATION)
                    .aggregateId(order.getCustomerId().toString())
                    .eventType(EventType.NOTIFICATION_REQUEST)
                    .payload(objectMapper.writeValueAsString(notificationEvent))
                    .createdAt(Instant.now())
                    .status(OutboxStatus.UNPROCESSED)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to save notification request to outbox", e);
        }
    }

    private void sendSuccessNotification(Order order, Refund refund) {
        try {
            boolean isPartial = refund.getAmount().compareTo(order.getTotalAmount()) < 0;
            com.fooddelivery.common.constants.NotificationTemplate eventName = isPartial
                    ? com.fooddelivery.common.constants.NotificationTemplate.PAYMENT_PARTIALLY_REFUNDED
                    : com.fooddelivery.common.constants.NotificationTemplate.PAYMENT_REFUNDED;
            NotificationRequestEvent notificationEvent = NotificationRequestEvent.builder()
                    .userId(order.getCustomerId())
                    .channel(ChannelType.PUSH)
                    .eventName(eventName)
                    .templateParams(List.of(order.getId().toString(), refund.getAmount().toString()))
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.NOTIFICATION)
                    .aggregateId(order.getCustomerId().toString())
                    .eventType(EventType.NOTIFICATION_REQUEST)
                    .payload(objectMapper.writeValueAsString(notificationEvent))
                    .createdAt(Instant.now())
                    .status(OutboxStatus.UNPROCESSED)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to save notification request to outbox", e);
        }
    }

    private RefundView toView(Refund refund, Order order) {
        return RefundView.builder()
                .id(refund.getId())
                .orderId(refund.getOrderId())
                .amount(refund.getAmount())
                .status(refund.getStatus())
                .destination(refund.getDestination())
                .method(order.getPaymentMethod())
                .reasonCode(refund.getReasonCode())
                .requestedAt(refund.getCreatedAt())
                .completedAt(refund.getCompletedAt())
                .expectedBy(refund.getCreatedAt() != null ? refund.getCreatedAt().plus(java.time.Duration.ofDays(5)) : java.time.Instant.now().plus(java.time.Duration.ofDays(5)))
                .build();
    }
}
