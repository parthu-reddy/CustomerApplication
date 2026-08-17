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
import java.util.Map;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
public class ChatRefundProcessorService {

    private final IOrderRepository orderRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ChatRefundProcessorService(IOrderRepository orderRepository,
                                      SupportTicketRepository supportTicketRepository,
                                      OutboxEventRepository outboxEventRepository,
                                      IIdempotencyKeyRepository idempotencyKeyRepository,
                                      ObjectMapper objectMapper,
                                      TransactionTemplate transactionTemplate) {
        this.orderRepository = orderRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @KafkaListener(topics = KafkaConstants.TOPIC_CHAT_EVENTS, groupId = "customer-application-chat-group")
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
                
                JsonNode payload = objectMapper.readTree(event.getPayload());
                UUID orderId = UUID.fromString(payload.get("orderId").asText());
                Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
                String refundType = payload.has("refundType") ? payload.get("refundType").asText() : "FULL";
                BigDecimal quoteAmount = calculateQuoteAmount(order, refundType, payload);
                
                BigDecimal maxRefundable = order.getTotalAmount().subtract(order.getRefundedAmount() != null ? order.getRefundedAmount() : BigDecimal.ZERO);
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
                JsonNode payload = objectMapper.readTree(event.getPayload());
                UUID orderId = UUID.fromString(payload.get("orderId").asText());
                UUID customerId = UUID.fromString(payload.get("customerId").asText());
                
                if (supportTicketRepository.existsByOrderIdAndCustomerIdAndStatus(orderId, customerId, SupportTicket.TicketStatus.OPEN)) {
                    throw new IllegalStateException("An open refund request already exists for this order.");
                }
                
                String reason = payload.has("reason") ? payload.get("reason").asText() : "OTHER";
                String description = payload.has("description") ? payload.get("description").asText() : "";
                
                String refundType = payload.has("refundType") ? payload.get("refundType").asText() : "FULL";
                Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
                BigDecimal quoteAmount = calculateQuoteAmount(order, refundType, payload);
                
                // Note: Financial Computations: Never use hardcoded fallback values for financial parameters... Fail Fast
                if (quoteAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Calculated refund amount must be greater than zero");
                }
                
                BigDecimal maxRefundable = order.getTotalAmount().subtract(order.getRefundedAmount() != null ? order.getRefundedAmount() : BigDecimal.ZERO);
                if (quoteAmount.compareTo(maxRefundable) > 0) {
                    throw new IllegalStateException("Requested refund amount exceeds the remaining refundable balance.");
                }

                SupportTicket ticket = new SupportTicket();
                ticket.setOrderId(orderId);
                ticket.setCustomerId(customerId);
                ticket.setReason(reason + (description.isEmpty() ? "" : " - " + description));
                ticket.setStatus(SupportTicket.TicketStatus.OPEN);
                ticket.setChatSessionId(UUID.fromString(event.getAggregateId()));
                ticket.setRefundAmount(quoteAmount.doubleValue());
                if (payload.has("items")) {
                    ticket.setRequestedRefundItems(payload.get("items").toString());
                }
                supportTicketRepository.save(ticket);
                
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

    private BigDecimal calculateQuoteAmount(Order order, String refundType, JsonNode payload) {
        BigDecimal quoteAmount = BigDecimal.ZERO;
        if ("FULL".equals(refundType)) {
            if (order.getStatus().getSequence() >= OrderStatus.ACCEPTED.getSequence()) {
                throw new IllegalStateException("Full cancellation refund is not allowed after the restaurant has accepted the order.");
            }
            quoteAmount = order.getTotalAmount();
        } else if ("PARTIAL".equals(refundType)) {
            if (order.getStatus() != OrderStatus.HANDED_OVER) {
                throw new IllegalStateException("Partial refund can only be requested after the order has been delivered.");
            }
            JsonNode items = payload.get("items");
            BigDecimal itemsRefundTotal = BigDecimal.ZERO;
            if (items != null && items.isArray()) {
                for (JsonNode itemNode : items) {
                    UUID itemId = UUID.fromString(itemNode.get("itemId").asText());
                    int quantity = itemNode.get("quantity").asInt();
                    OrderItem orderItem = order.getOrderItems().stream()
                            .filter(i -> i.getId().equals(itemId)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Invalid item ID"));
                    
                    int currentRefunded = orderItem.getRefundedQuantity() != null ? orderItem.getRefundedQuantity() : 0;
                    if (quantity > (orderItem.getQuantity() - currentRefunded) || quantity <= 0) {
                        throw new IllegalArgumentException("Invalid quantity or item already refunded for item " + itemId);
                    }
                    itemsRefundTotal = itemsRefundTotal.add(orderItem.getPrice().multiply(BigDecimal.valueOf(quantity)));
                }
            }
            
            if (order.getItemTotal() != null && order.getItemTotal().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal itemRatio = itemsRefundTotal.divide(order.getItemTotal(), 4, java.math.RoundingMode.HALF_UP);
                BigDecimal proratedSgst = (order.getSgst() != null) ? order.getSgst().multiply(itemRatio) : BigDecimal.ZERO;
                BigDecimal proratedCgst = (order.getCgst() != null) ? order.getCgst().multiply(itemRatio) : BigDecimal.ZERO;
                quoteAmount = itemsRefundTotal.add(proratedSgst).add(proratedCgst).setScale(2, java.math.RoundingMode.HALF_UP);
            } else {
                quoteAmount = itemsRefundTotal.setScale(2, java.math.RoundingMode.HALF_UP);
            }
        }
        return quoteAmount;
    }
}
