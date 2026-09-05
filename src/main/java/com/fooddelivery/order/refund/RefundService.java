package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.client.WalletInternalClient;
import com.fooddelivery.common.constants.AggregateType;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.dto.wallet.TransactionRequest;
import com.fooddelivery.common.enums.OutboxStatus;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.enums.WalletEntityType;
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
import java.time.LocalDateTime;
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
    private final WalletInternalClient walletClient;

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

        BigDecimal remaining = intent.getAmount().subtract(refundRepository.sumCompletedByOrder(command.getOrderId()));
        if (command.getAmount().compareTo(remaining) > 0) {
            throw new IllegalStateException("REFUND_EXCEEDS_REMAINING");
        }

        RefundDestination destination = command.getDestination();
        if (destination == null) {
            if (intent.getPaymentMethod() == PaymentMethod.CARD || intent.getPaymentMethod() == PaymentMethod.UPI) {
                destination = RefundDestination.ORIGINAL_METHOD;
            } else if (intent.getPaymentMethod() == PaymentMethod.WALLET) {
                destination = RefundDestination.STORE_CREDIT;
            } else if (intent.getPaymentMethod() == PaymentMethod.COD) {
                if (intent.getStatus() == PaymentIntentStatus.PENDING_COLLECTION) {
                    destination = RefundDestination.NONE;
                } else {
                    destination = RefundDestination.STORE_CREDIT;
                }
            } else {
                destination = RefundDestination.STORE_CREDIT;
            }
        }

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
                        .createdAt(LocalDateTime.now())
                        .status(OutboxStatus.UNPROCESSED)
                        .build();
                outboxEventRepository.save(outboxEvent);
            } catch (Exception e) {
                throw new RuntimeException("Failed to save outbox event", e);
            }
        } else if (destination == RefundDestination.STORE_CREDIT) {
            TransactionRequest txReq = new TransactionRequest()
                .amount(refund.getAmount())
                .referenceId(refund.getId())
                .category(com.fooddelivery.common.enums.ChargeCategory.STORE_CREDIT)
                .description("RefundService");
            walletClient.credit(WalletEntityType.CUSTOMER, order.getCustomerId(), txReq, "RefundService");
            completeInternal(refund, "WALLET", order);
        } else if (destination == RefundDestination.NONE) {
            completeInternal(refund, null, order);
        }

        return toView(refund, order);
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
                
        completeInternal(refund, gatewayRefundId, order);
    }

    private void completeInternal(Refund refund, String gatewayRefundId, Order order) {
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setGatewayRefundId(gatewayRefundId);
        refund.setCompletedAt(LocalDateTime.now());
        ledgerBookkeeper.bookRefund(order, refund);
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
        List<Refund> stuck = refundRepository.findStuckProcessing(LocalDateTime.now().minusMinutes(5));
        for (Refund refund : stuck) {
            refund.setAttempts(refund.getAttempts() + 1);
            if (refund.getAttempts() > 3) {
                fail(refund.getId(), "Max retries exceeded");
            } else {
                refund.setUpdatedAt(LocalDateTime.now());
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
                            .createdAt(LocalDateTime.now())
                            .status(OutboxStatus.UNPROCESSED)
                            .build();
                    outboxEventRepository.save(outboxEvent);
                } catch (Exception e) {
                    log.error("Failed to enqueue retry", e);
                }
            }
        }
    }

    public BigDecimal quote(UUID orderId, List<RefundCommand.Item> items) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        BigDecimal total = BigDecimal.ZERO;
        for (RefundCommand.Item itemCmd : items) {
            OrderItem item = order.getOrderItems().stream()
                .filter(i -> i.getId().equals(itemCmd.getOrderItemId()))
                .findFirst().orElseThrow();
            total = total.add(item.getPrice().multiply(new BigDecimal(itemCmd.getQuantity())));
        }
        return total;
    }
    
    private void sendNotification(Order order, Refund refund) {
        try {
            NotificationRequestEvent notificationEvent = NotificationRequestEvent.builder()
                    .userId(order.getCustomerId())
                    .channel(ChannelType.PUSH)
                    .eventName("REFUND_REQUESTED")
                    .templateParams(List.of(order.getId().toString(), refund.getAmount().toString(), refund.getDestination().name()))
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.NOTIFICATION)
                    .aggregateId(order.getCustomerId().toString())
                    .eventType(EventType.NOTIFICATION_REQUEST)
                    .payload(objectMapper.writeValueAsString(notificationEvent))
                    .createdAt(LocalDateTime.now())
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
                    .eventName("REFUND_FAILED")
                    .templateParams(List.of(order.getId().toString(), refund.getAmount().toString()))
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.NOTIFICATION)
                    .aggregateId(order.getCustomerId().toString())
                    .eventType(EventType.NOTIFICATION_REQUEST)
                    .payload(objectMapper.writeValueAsString(notificationEvent))
                    .createdAt(LocalDateTime.now())
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
            String eventName = isPartial ? "PAYMENT_PARTIALLY_REFUNDED" : "PAYMENT_REFUNDED";
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
                    .createdAt(LocalDateTime.now())
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
                .expectedBy(refund.getCreatedAt() != null ? refund.getCreatedAt().plusDays(5) : LocalDateTime.now().plusDays(5))
                .build();
    }
}
