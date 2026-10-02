package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AggregateType;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.event.OutboxEvent;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.entity.SupportTicket;
import com.fooddelivery.common.entity.IdempotencyKey;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class ChatRefundProcessorService {

    private final IOrderRepository orderRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final com.fooddelivery.order.refund.RefundService refundService;
    private final com.fooddelivery.order.repository.RefundRepository refundRepository;
    private final com.fooddelivery.common.event.EventBinder eventBinder;

    /** Both handlers built this list identically from the JSON; it is one place now. */
    private static List<com.fooddelivery.order.refund.RefundCommand.Item> toRefundItems(
            List<com.fooddelivery.common.event.ChatRefundRequestedEvent.Item> items) {
        List<com.fooddelivery.order.refund.RefundCommand.Item> out = new java.util.ArrayList<>();
        if (items == null) {
            return out;
        }
        for (com.fooddelivery.common.event.ChatRefundRequestedEvent.Item item : items) {
            com.fooddelivery.order.refund.RefundCommand.Item i =
                    new com.fooddelivery.order.refund.RefundCommand.Item();
            i.setOrderItemId(item.getItemId());
            i.setQuantity(item.getQuantity());
            out.add(i);
        }
        return out;
    }



    private static String validatedRefundType(com.fooddelivery.common.event.ChatRefundRequestedEvent request) {
        String type = request.getRefundType() != null ? request.getRefundType() : "FULL";
        if (!"FULL".equals(type) && !"PARTIAL".equals(type)) {
            throw new IllegalArgumentException("Refund type must be FULL or PARTIAL");
        }
        return type;
    }

    private static List<com.fooddelivery.order.refund.RefundCommand.Item> validatedRefundItems(
            com.fooddelivery.common.event.ChatRefundRequestedEvent request, String type) {
        if ("PARTIAL".equals(type) && (request.getItems() == null || request.getItems().isEmpty())) {
            // RefundService deliberately interprets an empty list as a full-order quote.
            // A partial request must never reach that full-refund convention.
            throw new IllegalArgumentException("Partial refund items are required");
        }
        if ("FULL".equals(type) && request.getItems() != null && !request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Full refund cannot include a partial item selection");
        }
        return toRefundItems(request.getItems());
    }

    @KafkaListener(topics = KafkaConstants.TOPIC_CHAT_EVENTS, groupId = "customer-application-chat-group-chatrefundprocessorservice")
    public void handleChatRecord(String payload, @org.springframework.messaging.handler.annotation.Headers Map<String, Object> headers) {
        String type = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventType");
        if (!"CHAT_REFUND_QUOTE_REQUESTED".equals(type) && !"CHAT_REFUND_REQUESTED".equals(type)) return;
        String id = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        String sessionId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers,
                org.springframework.kafka.support.KafkaHeaders.RECEIVED_KEY);
        if (id == null || id.isBlank() || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Chat refund event requires eventId and session key");
        }
        UUID.fromString(sessionId);
        handleChatEvents(OutboxEvent.builder().id(id).type(type).aggregateId(sessionId).payload(payload).build());
    }

    public void handleChatEvents(OutboxEvent event) {
        Boolean alreadyProcessed = transactionTemplate.execute(status -> {
            if (idempotencyKeyRepository.existsById("chat_event:" + event.getId())) {
                return true;
            }
            return false;
        });

        if (Boolean.TRUE.equals(alreadyProcessed)) {
            log.info("Event {} already processed. Skipping.", event.getId());
            return;
        }

        if ("CHAT_REFUND_QUOTE_REQUESTED".equals(event.getType())) {
            handleQuoteRequest(event);
        } else if ("CHAT_REFUND_REQUESTED".equals(event.getType())) {
            handleRefundRequest(event);
        }
    }

    /*
     * Each handler works in two steps. It validates and prices the request first, outside any
     * transaction, then records its reply in one write transaction. RefundService.quote is a
     * proxied @Transactional method: a refusal it throws (ITEM_ALREADY_REFUNDED and the like) marks
     * any transaction it joins rollback-only, even when the caller catches it. When the quote ran
     * inside the write transaction, the commit threw UnexpectedRollbackException, the customer's
     * error reply and the idempotency key rolled back, and the request was dead-lettered.
     */

    private void handleQuoteRequest(OutboxEvent event) {
        OutboxEventEntity reply;
        try {
            // The payload is the chat message content the client sent, so this bind is the
            // validation boundary: a missing orderId or a malformed item id is rejected here
            // and reported back to the customer as CHAT_REFUND_ERROR.
            com.fooddelivery.common.event.ChatRefundRequestedEvent request =
                    eventBinder.bind(event.getPayload(),
                            com.fooddelivery.common.event.ChatRefundRequestedEvent.class);
            UUID orderId = request.getOrderId();
            Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
            requireOrderCustomerActor(request, order);
            String refundType = validatedRefundType(request);
            BigDecimal quoteAmount = quote(request, order, refundType);

            com.fooddelivery.common.event.ChatRefundQuoteResponseEvent responseEvent = com.fooddelivery.common.event.ChatRefundQuoteResponseEvent.builder()
                    .quoteAmount(quoteAmount)
                    .refundType(refundType)
                    .orderId(orderId)
                    .items("PARTIAL".equals(refundType) ? List.copyOf(request.getItems()) : List.of())
                    .reason(request.getReason())
                    .build();
            reply = chatSessionEvent(event.getAggregateId(), "CHAT_REFUND_QUOTE_RESPONSE", responseEvent);
            log.info("Calculated quote {} for session {}", quoteAmount, event.getAggregateId());
        } catch (jakarta.validation.ConstraintViolationException e) {
            // A @NotNull/@Positive miss on client-supplied content is a bad request, not a
            // poison message: report it to the customer instead of retrying into the DLT.
            log.warn("Chat refund payload failed validation: {}", e.getMessage());
            reply = errorEvent(event.getAggregateId(), "Invalid request format.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Business validation failed for quote request: {}", e.getMessage());
            reply = errorEvent(event.getAggregateId(), e.getMessage());
        }
        OutboxEventEntity answer = reply;
        transactionTemplate.executeWithoutResult(status -> {
            if (alreadyProcessed(event)) return;
            outboxEventRepository.save(answer);
            markProcessed(event);
        });
    }

    private void handleRefundRequest(OutboxEvent event) {
        com.fooddelivery.common.event.ChatRefundRequestedEvent request;
        UUID customerId;
        BigDecimal quoteAmount;
        String requestedItems;
        try {
            request = eventBinder.bind(event.getPayload(),
                    com.fooddelivery.common.event.ChatRefundRequestedEvent.class);
            Order order = orderRepository.findById(request.getOrderId()).orElseThrow(() -> new IllegalArgumentException("Order not found"));
            customerId = requireOrderCustomerActor(request, order);
            quoteAmount = quote(request, order, validatedRefundType(request));
            // Note: Financial Computations: Never use hardcoded fallback values for financial parameters... Fail Fast
            if (quoteAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Calculated refund amount must be greater than zero");
            }
            requestedItems = request.getItems() != null && !request.getItems().isEmpty()
                    ? objectMapper.writeValueAsString(request.getItems()) : null;
        } catch (jakarta.validation.ConstraintViolationException e) {
            // A @NotNull/@Positive miss on client-supplied content is a bad request, not a
            // poison message: report it to the customer instead of retrying into the DLT.
            log.warn("Chat refund payload failed validation: {}", e.getMessage());
            recordError(event, "Invalid request format.");
            return;
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Business validation failed for refund request: {}", e.getMessage());
            recordError(event, e.getMessage());
            return;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to process JSON payload for refund request", e);
            recordError(event, "Invalid request format.");
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            if (alreadyProcessed(event)) return;
            UUID orderId = request.getOrderId();
            // Checked in the same transaction as the insert, so it sees tickets committed meanwhile.
            if (supportTicketRepository.existsByOrderIdAndCustomerIdAndStatus(orderId, customerId, SupportTicket.TicketStatus.OPEN)) {
                log.warn("Refund request for order {} refused: an open ticket already exists", orderId);
                outboxEventRepository.save(errorEvent(event.getAggregateId(), "An open refund request already exists for this order."));
                markProcessed(event);
                return;
            }

            String reason = request.getReason() != null ? request.getReason() : "OTHER";
            String description = request.getDescription() != null ? request.getDescription() : "";
            SupportTicket ticket = new SupportTicket();
            ticket.setOrderId(orderId);
            ticket.setCustomerId(customerId);
            ticket.setReason(reason + (description.isEmpty() ? "" : " - " + description));
            ticket.setStatus(SupportTicket.TicketStatus.OPEN);
            ticket.setChatSessionId(UUID.fromString(event.getAggregateId()));
            ticket.setRefundAmount(quoteAmount);
            ticket.setRequestedRefundItems(requestedItems);
            supportTicketRepository.save(ticket);

            // The ticket is the request; it is not the refund.
            //
            // This flow used to raise an OPEN ticket *and* immediately issue a refund, while
            // telling the customer their request was "under review by our support team". An
            // administrator resolving that same ticket through AdminRefundController then issued
            // a second refund: the remaining-amount guard stopped the money leaving twice, but
            // it did so by throwing, so resolving a chat ticket always failed with a 500.
            //
            // A customer cannot approve their own refund. The quote is recorded on the ticket
            // and an administrator decides, which is what the customer is told happens.
            com.fooddelivery.common.event.ChatRefundDecisionEvent responseEvent = com.fooddelivery.common.event.ChatRefundDecisionEvent.builder()
                    .status("OPEN")
                    .amount(quoteAmount)
                    .ticketId(ticket.getId().toString())
                    .message("Your refund request has been submitted and is currently under review by our support team.")
                    .build();
            outboxEventRepository.save(chatSessionEvent(event.getAggregateId(), "CHAT_REFUND_DECISION", responseEvent));
            markProcessed(event);
            log.info("Created SupportTicket {} for session {}", ticket.getId(), event.getAggregateId());
        });
    }

    /** Validates the selection and prices it. Runs outside any transaction; see the note above. */
    private BigDecimal quote(com.fooddelivery.common.event.ChatRefundRequestedEvent request, Order order, String refundType) {
        List<com.fooddelivery.order.refund.RefundCommand.Item> selectedItems = validatedRefundItems(request, refundType);
        BigDecimal quoteAmount = refundService.quote(order.getId(), "FULL".equals(refundType) ? List.of() : selectedItems);
        if (quoteAmount.compareTo(order.getTotalAmount()) > 0) {
            throw new IllegalStateException("Requested refund amount exceeds the remaining refundable balance.");
        }
        return quoteAmount;
    }

    private boolean alreadyProcessed(OutboxEvent event) {
        return idempotencyKeyRepository.existsById("chat_event:" + event.getId());
    }

    private void markProcessed(OutboxEvent event) {
        idempotencyKeyRepository.save(new IdempotencyKey("chat_event:" + event.getId()));
    }

    private void recordError(OutboxEvent event, String errorMessage) {
        OutboxEventEntity reply = errorEvent(event.getAggregateId(), errorMessage);
        transactionTemplate.executeWithoutResult(status -> {
            if (alreadyProcessed(event)) return;
            outboxEventRepository.save(reply);
            markProcessed(event);
        });
    }

    private OutboxEventEntity errorEvent(String chatSessionId, String errorMessage) {
        return chatSessionEvent(chatSessionId, "CHAT_REFUND_ERROR",
                com.fooddelivery.common.event.ChatRefundErrorEvent.builder()
                        .error(errorMessage != null ? errorMessage : "An unknown error occurred while processing the refund request.")
                        .build());
    }

    private OutboxEventEntity chatSessionEvent(String chatSessionId, String type, Object payload) {
        try {
            return OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.CHAT_SESSION)
                    .aggregateId(chatSessionId)
                    .eventType(EventType.valueOf(type))
                    .payload(objectMapper.writeValueAsString(payload))
                    .createdAt(Instant.now())
                    .build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Our own reply DTO failed to serialize: a defect, not a customer error. Throw so the
            // record is retried and dead-lettered rather than answered with nothing.
            throw new IllegalStateException("Cannot serialize " + type + " for session " + chatSessionId, e);
        }
    }

    /**
     * The event must carry the actor stamped by ChatMessageService, not a browser-provided
     * customerId. The actual ticket customer comes from the authoritative Order record.
     */
    private UUID requireOrderCustomerActor(com.fooddelivery.common.event.ChatRefundRequestedEvent request,
                                           Order order) {
        UUID orderCustomerId = order.getCustomerId();
        if (orderCustomerId == null
                || request.getActorId() == null
                || request.getActorId().isBlank()
                || !"CUSTOMER".equalsIgnoreCase(request.getActorType())
                || !orderCustomerId.toString().equals(request.getActorId())) {
            throw new IllegalArgumentException("Only the customer who placed this order may request a refund.");
        }
        return orderCustomerId;
    }


}
