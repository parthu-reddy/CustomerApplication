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
import java.time.LocalDateTime;
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



    @KafkaListener(topics = KafkaConstants.TOPIC_CHAT_EVENTS, groupId = "customer-application-chat-group-chatrefundprocessorservice")
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

    private void handleQuoteRequest(OutboxEvent event) {
        transactionTemplate.executeWithoutResult(status -> {
            try {
                if (idempotencyKeyRepository.existsById("chat_event:" + event.getId())) return;
                
                // The payload is the chat message content the client sent, so this bind is the
                // validation boundary: a missing orderId or a malformed item id is rejected here
                // and reported back to the customer as CHAT_REFUND_ERROR.
                com.fooddelivery.common.event.ChatRefundRequestedEvent request =
                        eventBinder.bind(event.getPayload(),
                                com.fooddelivery.common.event.ChatRefundRequestedEvent.class);
                UUID orderId = request.getOrderId();
                Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));

                String refundType = request.getRefundType() != null ? request.getRefundType() : "FULL";
                BigDecimal quoteAmount;
                if ("FULL".equals(refundType)) {
                    quoteAmount = refundService.quote(orderId, List.of());
                } else {
                    quoteAmount = refundService.quote(orderId, toRefundItems(request.getItems()));
                }
                
                BigDecimal maxRefundable = order.getTotalAmount();
                if (quoteAmount.compareTo(maxRefundable) > 0) {
                    throw new IllegalStateException("Requested refund amount exceeds the remaining refundable balance.");
                }

                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("quoteAmount", quoteAmount);
                responseMap.put("refundType", refundType);

                OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                        .id(UUID.randomUUID())
                        .aggregateType(AggregateType.CHAT_SESSION)
                        .aggregateId(event.getAggregateId())
                        .eventType(EventType.valueOf("CHAT_REFUND_QUOTE_RESPONSE"))
                        .payload(objectMapper.writeValueAsString(responseMap))
                        .createdAt(LocalDateTime.now())
                        .build();
                outboxEventRepository.save(outboxEvent);
                log.info("Calculated quote {} for session {}", quoteAmount, event.getAggregateId());
            } catch (jakarta.validation.ConstraintViolationException e) {
                // A @NotNull/@Positive miss on client-supplied content is a bad request, not a
                // poison message: report it to the customer instead of retrying into the DLT.
                log.warn("Chat refund payload failed validation: {}", e.getMessage());
                publishErrorEvent(event.getAggregateId(), "Invalid request format.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.warn("Business validation failed for quote request: {}", e.getMessage());
                publishErrorEvent(event.getAggregateId(), e.getMessage());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.error("Failed to process JSON payload for quote request", e);
                publishErrorEvent(event.getAggregateId(), "Invalid request format.");
            } finally {
                idempotencyKeyRepository.save(new IdempotencyKey("chat_event:" + event.getId()));
            }
        });
    }

    private void handleRefundRequest(OutboxEvent event) {
        transactionTemplate.executeWithoutResult(status -> {
            try {
                com.fooddelivery.common.event.ChatRefundRequestedEvent request =
                        eventBinder.bind(event.getPayload(),
                                com.fooddelivery.common.event.ChatRefundRequestedEvent.class);
                UUID orderId = request.getOrderId();
                // Required here but not for a quote, so it is checked rather than annotated on the
                // shared class -- annotating it would reject every valid quote request.
                UUID customerId = request.getCustomerId();
                if (customerId == null) {
                    throw new IllegalArgumentException("customerId is required on a refund request");
                }

                if (supportTicketRepository.existsByOrderIdAndCustomerIdAndStatus(orderId, customerId, SupportTicket.TicketStatus.OPEN)) {
                    throw new IllegalStateException("An open refund request already exists for this order.");
                }

                String reason = request.getReason() != null ? request.getReason() : "OTHER";
                String description = request.getDescription() != null ? request.getDescription() : "";

                String refundType = request.getRefundType() != null ? request.getRefundType() : "FULL";
                Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
                BigDecimal quoteAmount;
                if ("FULL".equals(refundType)) {
                    quoteAmount = refundService.quote(orderId, List.of());
                } else {
                    quoteAmount = refundService.quote(orderId, toRefundItems(request.getItems()));
                }
                
                // Note: Financial Computations: Never use hardcoded fallback values for financial parameters... Fail Fast
                if (quoteAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Calculated refund amount must be greater than zero");
                }
                
                BigDecimal maxRefundable = order.getTotalAmount();
                if (quoteAmount.compareTo(maxRefundable) > 0) {
                    throw new IllegalStateException("Requested refund amount exceeds the remaining refundable balance.");
                }

                SupportTicket ticket = new SupportTicket();
                ticket.setOrderId(orderId);
                ticket.setCustomerId(customerId);
                ticket.setReason(reason + (description.isEmpty() ? "" : " - " + description));
                ticket.setStatus(SupportTicket.TicketStatus.OPEN); // Wait, if we process it, maybe it's resolved? Keep OPEN for now
                ticket.setChatSessionId(UUID.fromString(event.getAggregateId()));
                ticket.setRefundAmount(quoteAmount);
                if (request.getItems() != null && !request.getItems().isEmpty()) {
                    ticket.setRequestedRefundItems(objectMapper.writeValueAsString(request.getItems()));
                }
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
                
                // Publish CHAT_REFUND_DECISION to notify user
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("status", "OPEN");
                responseMap.put("ticketId", ticket.getId().toString());
                responseMap.put("message", "Your refund request has been submitted and is currently under review by our support team.");

                OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                        .id(UUID.randomUUID())
                        .aggregateType(AggregateType.CHAT_SESSION)
                        .aggregateId(event.getAggregateId())
                        .eventType(EventType.valueOf("CHAT_REFUND_DECISION"))
                        .payload(objectMapper.writeValueAsString(responseMap))
                        .createdAt(LocalDateTime.now())
                        .build();
                outboxEventRepository.save(outboxEvent);
                log.info("Created SupportTicket {} for session {}", ticket.getId(), event.getAggregateId());
            } catch (jakarta.validation.ConstraintViolationException e) {
                // A @NotNull/@Positive miss on client-supplied content is a bad request, not a
                // poison message: report it to the customer instead of retrying into the DLT.
                log.warn("Chat refund payload failed validation: {}", e.getMessage());
                publishErrorEvent(event.getAggregateId(), "Invalid request format.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.warn("Business validation failed for refund request: {}", e.getMessage());
                publishErrorEvent(event.getAggregateId(), e.getMessage());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.error("Failed to process JSON payload for refund request", e);
                publishErrorEvent(event.getAggregateId(), "Invalid request format.");
            } finally {
                idempotencyKeyRepository.save(new IdempotencyKey("chat_event:" + event.getId()));
            }
        });
    }

    @org.springframework.transaction.annotation.Transactional
    private void publishErrorEvent(String chatSessionId, String errorMessage) {
        try {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", errorMessage != null ? errorMessage : "An unknown error occurred while processing the refund request.");

            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.CHAT_SESSION)
                    .aggregateId(chatSessionId)
                    .eventType(EventType.valueOf("CHAT_REFUND_ERROR"))
                    .payload(objectMapper.writeValueAsString(errorMap))
                    .createdAt(LocalDateTime.now())
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish error event", e);
        }
    }


}
