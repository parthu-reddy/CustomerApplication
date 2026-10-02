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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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

    /** A PROCESSING refund with no outcome for this long is re-dispatched by {@link #retryStuck()}. */
    static final java.time.Duration STUCK_AFTER = java.time.Duration.ofMinutes(5);
    /** Re-dispatches of one queueing before the sweeper declares the refund FAILED. */
    static final int MAX_SWEEP_ATTEMPTS = 3;

    @Transactional
    public RefundView request(RefundCommand command) {
        Plan plan = plan(command);
        if (plan.refusal() != null) {
            throw new IllegalStateException(plan.refusal());
        }
        return plan.existing() != null ? plan.existing() : record(plan);
    }

    /**
     * {@link #request} for an automatic refund whose caller must keep its own state change when the
     * refund cannot be routed: the order and payment event consumers. The refusal is returned, not
     * thrown. Thrown out of this bean's {@code @Transactional} proxy, it marks the caller's
     * transaction rollback-only, so the caller's commit failed and its state change was lost even
     * though it caught the exception. Only refusals decided before anything is written are
     * returned; a failure after the refund row is written still throws and rolls the caller back.
     */
    @Transactional
    public Optional<String> requestUnlessRefused(RefundCommand command) {
        Plan plan = plan(command);
        if (plan.refusal() != null) {
            return Optional.of(plan.refusal());
        }
        if (plan.existing() == null) {
            record(plan);
        }
        return Optional.empty();
    }

    /** What {@link #plan} decided: the refund to write, the one already written, or a refusal. */
    private record Plan(RefundCommand command, BigDecimal amount, PaymentIntent intent, Order order,
                        RefundDestination destination, RefundView existing, String refusal) {
        static Plan refused(String reason) {
            return new Plan(null, null, null, null, null, null, reason);
        }
    }

    /**
     * Validates and routes a refund without writing anything. An invalid command throws; a refund
     * that is valid but cannot be given (nothing left to refund, no routable payment, an item quote
     * that no longer holds) comes back as a refusal.
     */
    private Plan plan(RefundCommand command) {
        BigDecimal amount = validateRequest(command);
        Optional<Refund> existing = refundRepository.findByIdempotencyKey(command.getIdempotencyKey());
        if (existing.isPresent()) {
            Order order = orderRepository.findById(existing.get().getOrderId()).orElseThrow();
            return new Plan(command, amount, null, order, null, toView(existing.get(), order), null);
        }

        PaymentIntent intent = paymentIntentRepository.findByInternalOrderIdForUpdate(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found"));

        Order order = orderRepository.findById(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        try {
            if (command.getItems() != null) {
                BigDecimal quote = quote(order, command.getItems());
                // Support may award less than its validated item quote. Preserve those items
                // for accounting, and allow the reduction only for an audited admin ticket.
                boolean supportDecision = command.getInitiatorType() == com.fooddelivery.order.enums.InitiatorType.ADMIN
                        && command.getSource() == com.fooddelivery.order.enums.RefundSource.CUSTOMER_TICKET
                        && command.getTicketId() != null;
                if (amount.compareTo(quote) > 0 || (amount.compareTo(quote) != 0 && !supportDecision)) {
                    throw new IllegalArgumentException("REFUND_AMOUNT_DOES_NOT_MATCH_QUOTE");
                }
            }

            BigDecimal committed = refundRepository.sumByOrderAndStatusIn(command.getOrderId(),
                    List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING, RefundStatus.COMPLETED));
            BigDecimal remaining = intent.getAmount().subtract(committed);
            if (amount.compareTo(remaining) > 0) {
                throw new IllegalStateException("REFUND_EXCEEDS_REMAINING");
            }

            return new Plan(command, amount, intent, order, resolveDestination(command, intent), null, null);
        } catch (IllegalStateException refusal) {
            return Plan.refused(refusal.getMessage());
        }
    }

    /** Writes the planned refund and hands it to its destination. */
    private RefundView record(Plan plan) {
        RefundCommand command = plan.command();
        PaymentIntent intent = plan.intent();
        Order order = plan.order();
        RefundDestination destination = plan.destination();
        Refund refund = Refund.builder()
                .id(UUID.randomUUID())
                .orderId(order.getId())
                .paymentIntentId(intent.getId())
                .amount(plan.amount())
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
            enqueueGatewayRefund(refund, intent, null);
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
        enqueueWalletCredit(refund, order, intent, "wallet_credit:" + refund.getId());
    }

    private void enqueueWalletCredit(Refund refund, Order order, PaymentIntent intent, String commandKey) {
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
                    .idempotencyKey(commandKey)
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
     * Asks the payment gateway to return {@code refund}'s amount to the original payment method,
     * through the outbox. The refund id is the gateway's and provider's identity for the refund on
     * every attempt; {@code commandKey} only distinguishes one dispatch of it from another.
     */
    private void enqueueGatewayRefund(Refund refund, PaymentIntent intent, String commandKey) {
        PaymentRefundRequestedEvent event = PaymentRefundRequestedEvent.builder()
                .refundId(refund.getId().toString())
                .orderId(refund.getOrderId().toString())
                .gatewayOrderId(intent.getGatewayOrderId())
                .amount(refund.getAmount())
                .gatewayName(intent.getGatewayName())
                .build();
        try {
            outboxEventRepository.save(OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.PAYMENT)
                    .aggregateId(intent.getId().toString())
                    .eventType(EventType.PAYMENT_REFUND_REQUESTED)
                    .idempotencyKey(commandKey)
                    .payload(objectMapper.writeValueAsString(event))
                    .createdAt(Instant.now())
                    .status(OutboxStatus.UNPROCESSED)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue gateway refund " + refund.getId(), e);
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
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found"));
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            return;
        }
        RefundStateMachine.validateTransition(refund.getStatus(), RefundStatus.COMPLETED);
        
        Order order = orderRepository.findById(refund.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
        PaymentIntent intent = paymentIntentRepository.findByInternalOrderIdForUpdate(refund.getOrderId()).orElseThrow();
        BigDecimal committed = refundRepository.sumByOrderAndStatusIn(refund.getOrderId(), List.of(RefundStatus.COMPLETED));
        if (committed.add(refund.getAmount()).compareTo(intent.getAmount()) > 0) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason("OVER_REFUND_BLOCKED");
            refundRepository.save(refund);
            return;
        }
                
        completeInternal(refund, gatewayRefundId, order, intent, committed.add(refund.getAmount()));
    }

    private void completeInternal(Refund refund, String gatewayRefundId, Order order) {
        PaymentIntent intent = paymentIntentRepository.findById(refund.getPaymentIntentId()).orElseThrow();
        // NONE means no capture occurred; its completion must not claim money was returned.
        completeInternal(refund, gatewayRefundId, order, intent, null);
    }

    private void completeInternal(Refund refund, String gatewayRefundId, Order order,
            PaymentIntent intent, BigDecimal totalCompleted) {
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setGatewayRefundId(gatewayRefundId);
        refund.setCompletedAt(java.time.Instant.now());
        if (refund.getDestination() != RefundDestination.NONE && totalCompleted != null) {
            intent.setStatus(totalCompleted.compareTo(intent.getAmount()) >= 0
                    ? PaymentIntentStatus.REFUNDED : PaymentIntentStatus.PARTIALLY_REFUNDED);
            paymentIntentRepository.save(intent);
        }
        String gatewayName = intent.getGatewayName() != null ? intent.getGatewayName().name() : null;
        ledgerBookkeeper.bookRefund(order, refund, gatewayName);
        sendSuccessNotification(order, refund);
    }

    @Transactional
    public void fail(UUID refundId, String reason) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
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
    
    /** Queues only the chosen failed refund; never dispatches the global age-based sweep. */
    @Transactional
    public void retryFailed(UUID refundId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found"));
        if (refund.getStatus() != RefundStatus.FAILED) {
            throw new IllegalStateException("Only FAILED refunds can be retried");
        }
        // Serialize with new refund requests and completions for this order. A previously failed
        // amount might have been awarded elsewhere since it stopped reserving the balance.
        PaymentIntent intent = paymentIntentRepository.findByInternalOrderIdForUpdate(refund.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found"));
        if (!intent.getId().equals(refund.getPaymentIntentId())) {
            throw new IllegalStateException("REFUND_PAYMENT_INTENT_MISMATCH");
        }
        BigDecimal amount = monetaryAmount(refund.getAmount(), "REFUND_AMOUNT_INVALID");
        if (intent.getStatus() != PaymentIntentStatus.SUCCESS
                && intent.getStatus() != PaymentIntentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("REFUND_STATE_INVALID");
        }
        BigDecimal committed = refundRepository.sumByOrderAndStatusIn(refund.getOrderId(),
                List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING, RefundStatus.COMPLETED));
        if (amount.compareTo(intent.getAmount().subtract(committed)) > 0) {
            throw new IllegalStateException("REFUND_EXCEEDS_REMAINING");
        }
        Order order = orderRepository.findById(refund.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (refund.getDestination() != RefundDestination.ORIGINAL_METHOD
                && refund.getDestination() != RefundDestination.STORE_CREDIT) {
            throw new IllegalStateException("REFUND_DESTINATION_NOT_RETRYABLE");
        }
        int attempt = Math.addExact(refund.getAttempts(), 1);
        String commandKey = "refund_retry:" + refund.getId() + ":" + attempt;
        if (refund.getDestination() == RefundDestination.STORE_CREDIT) {
            // A new transport attempt retains the refundId consumed by WalletService's credit
            // idempotency. Do not try to insert the original outbox's unique wallet_credit key.
            enqueueWalletCredit(refund, order, intent, commandKey);
        } else {
            if (intent.getGatewayName() == null || intent.getGatewayOrderId() == null
                    || intent.getGatewayOrderId().isBlank()) {
                throw new IllegalStateException("REFUND_GATEWAY_REQUIRED");
            }
            enqueueGatewayRefund(refund, intent, commandKey);
        }
        RefundStateMachine.validateTransition(refund.getStatus(), RefundStatus.PROCESSING);
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setAttempts(attempt); // Preserve total attempt history; do not reset it for an admin.
        // The attempt that failed exhausted its sweeper budget; this queueing gets its own.
        refund.setSweepAttempts(0);
        refund.setFailureReason(null);
        refund.setUpdatedAt(Instant.now());
        refundRepository.save(refund);
        log.info("Admin refund retry queued refundId={} orderId={} attempt={}", refundId, order.getId(), attempt);
    }

    /**
     * Re-dispatches refunds that have been PROCESSING without an outcome, to the destination they
     * were queued for, and gives up after {@link #MAX_SWEEP_ATTEMPTS} re-dispatches of one queueing.
     * A store-credit refund goes back to WalletService; the payment gateway never took that money.
     */
    @Transactional
    public void retryStuck() {
        List<Refund> stuck = refundRepository.findStuckProcessing(Instant.now().minus(STUCK_AFTER));
        for (Refund refund : stuck) {
            refund.setAttempts(refund.getAttempts() + 1);
            refund.setSweepAttempts(refund.getSweepAttempts() + 1);
            if (refund.getSweepAttempts() > MAX_SWEEP_ATTEMPTS) {
                fail(refund.getId(), "Max retries exceeded");
                continue;
            }
            refund.setUpdatedAt(Instant.now());
            String commandKey = "refund_sweep:" + refund.getId() + ":" + refund.getAttempts();
            try {
                PaymentIntent intent = paymentIntentRepository.findById(refund.getPaymentIntentId()).orElseThrow();
                switch (refund.getDestination()) {
                    case ORIGINAL_METHOD -> enqueueGatewayRefund(refund, intent, commandKey);
                    case STORE_CREDIT -> enqueueWalletCredit(refund,
                            orderRepository.findById(refund.getOrderId()).orElseThrow(), intent, commandKey);
                    default -> throw new IllegalStateException("REFUND_DESTINATION_NOT_RETRYABLE");
                }
            } catch (Exception e) {
                log.error("Failed to re-dispatch stuck refund refundId={} destination={}",
                        refund.getId(), refund.getDestination(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal quote(UUID orderId, List<RefundCommand.Item> items) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        return quote(order, items);
    }

    private BigDecimal quote(Order order, List<RefundCommand.Item> items) {
        if (items == null) {
            throw new IllegalArgumentException("REFUND_ITEMS_INVALID");
        }
        if (items.isEmpty()) {
            return quoteAmount(order.getTotalAmount());
        }
        if (order.getItemTotal() == null || order.getItemTotal().compareTo(BigDecimal.ZERO) == 0
                || order.getOrderItems() == null) {
            throw new IllegalStateException("QUOTE_UNAVAILABLE");
        }

        BigDecimal itemsRefundTotal = BigDecimal.ZERO;
        Set<UUID> itemIds = new HashSet<>();
        for (RefundCommand.Item itemCmd : items) {
            if (itemCmd == null || itemCmd.getOrderItemId() == null || itemCmd.getQuantity() <= 0) {
                throw new IllegalArgumentException("REFUND_ITEMS_INVALID");
            }
            if (!itemIds.add(itemCmd.getOrderItemId())) {
                throw new IllegalArgumentException("REFUND_ITEM_DUPLICATE");
            }
            OrderItem item = order.getOrderItems().stream()
                .filter(i -> i.getId().equals(itemCmd.getOrderItemId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("REFUND_ITEM_NOT_ON_ORDER"));
            if (item.getQuantity() == null || item.getQuantity() <= 0 || item.getPrice() == null
                    || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("QUOTE_UNAVAILABLE");
            }
            
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

    /**
     * This is the final validation boundary before a refund row and its money-moving outbox event
     * are created. Controllers still validate their public DTOs, but background jobs and internal
     * callers also use this service and must receive controlled validation failures.
     */
    private BigDecimal validateRequest(RefundCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("REFUND_COMMAND_REQUIRED");
        }
        if (command.getOrderId() == null) {
            throw new IllegalArgumentException("REFUND_ORDER_REQUIRED");
        }
        if (command.getSource() == null) {
            throw new IllegalArgumentException("REFUND_SOURCE_REQUIRED");
        }
        if (command.getInitiatorType() == null) {
            throw new IllegalArgumentException("REFUND_INITIATOR_REQUIRED");
        }
        if (command.getInitiatorType() != com.fooddelivery.order.enums.InitiatorType.SYSTEM
                && command.getInitiatorId() == null) {
            throw new IllegalArgumentException("REFUND_INITIATOR_ID_REQUIRED");
        }
        if (command.getFaultType() == null) {
            throw new IllegalArgumentException("REFUND_FAULT_TYPE_REQUIRED");
        }
        if (command.getReasonCode() == null || command.getReasonCode().isBlank()
                || command.getReasonCode().length() > 40) {
            throw new IllegalArgumentException("REFUND_REASON_CODE_INVALID");
        }
        if (command.getReasonText() != null && command.getReasonText().length() > 2000) {
            throw new IllegalArgumentException("REFUND_REASON_TEXT_INVALID");
        }
        if (command.getIdempotencyKey() == null || command.getIdempotencyKey().isBlank()
                || command.getIdempotencyKey().length() > 255) {
            throw new IllegalArgumentException("REFUND_IDEMPOTENCY_KEY_INVALID");
        }
        return monetaryAmount(command.getAmount(), "REFUND_AMOUNT_INVALID");
    }

    private BigDecimal quoteAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("QUOTE_UNAVAILABLE");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (integerDigits(normalized) > 12) {
                throw new IllegalStateException("QUOTE_UNAVAILABLE");
            }
            return normalized;
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("QUOTE_UNAVAILABLE", ex);
        }
    }

    private BigDecimal monetaryAmount(BigDecimal amount, String errorCode) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(errorCode);
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (integerDigits(normalized) > 12) {
                throw new IllegalArgumentException(errorCode);
            }
            return normalized;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(errorCode, ex);
        }
    }

    private int integerDigits(BigDecimal amount) {
        return Math.max(0, amount.precision() - amount.scale());
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
