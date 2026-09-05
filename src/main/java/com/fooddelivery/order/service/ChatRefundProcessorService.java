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
                
                JsonNode payload = objectMapper.readTree(event.getPayload());
                UUID orderId = UUID.fromString(payload.get("orderId").asText());
                Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
                
                String refundType = payload.has("refundType") ? payload.get("refundType").asText() : "FULL";
                BigDecimal quoteAmount;
                if ("FULL".equals(refundType)) {
                    quoteAmount = refundService.quote(orderId, List.of());
                } else {
                    List<com.fooddelivery.order.refund.RefundCommand.Item> reqItems = new java.util.ArrayList<>();
                    if (payload.has("items")) {
                        for (JsonNode itemNode : payload.get("items")) {
                            com.fooddelivery.order.refund.RefundCommand.Item i = new com.fooddelivery.order.refund.RefundCommand.Item();
                            i.setOrderItemId(UUID.fromString(itemNode.get("itemId").asText()));
                            i.setQuantity(itemNode.get("quantity").asInt());
                            reqItems.add(i);
                        }
                    }
                    quoteAmount = refundService.quote(orderId, reqItems);
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
                
                BigDecimal quoteAmount;
                if ("FULL".equals(refundType)) {
                    quoteAmount = refundService.quote(orderId, List.of());
                } else {
                    List<com.fooddelivery.order.refund.RefundCommand.Item> reqItems = new java.util.ArrayList<>();
                    if (payload.has("items")) {
                        for (JsonNode itemNode : payload.get("items")) {
                            com.fooddelivery.order.refund.RefundCommand.Item i = new com.fooddelivery.order.refund.RefundCommand.Item();
                            i.setOrderItemId(UUID.fromString(itemNode.get("itemId").asText()));
                            i.setQuantity(itemNode.get("quantity").asInt());
                            reqItems.add(i);
                        }
                    }
                    quoteAmount = refundService.quote(orderId, reqItems);
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
                if (payload.has("items")) {
                    ticket.setRequestedRefundItems(payload.get("items").toString());
                }
                supportTicketRepository.save(ticket);
                
                // Issue refund request
                List<com.fooddelivery.order.refund.RefundCommand.Item> commandItems = new java.util.ArrayList<>();
                if (payload.has("items")) {
                    for (JsonNode itemNode : payload.get("items")) {
                        com.fooddelivery.order.refund.RefundCommand.Item ci = new com.fooddelivery.order.refund.RefundCommand.Item();
                        ci.setOrderItemId(UUID.fromString(itemNode.get("itemId").asText()));
                        ci.setQuantity(itemNode.get("quantity").asInt());
                        commandItems.add(ci);
                    }
                }
                
                com.fooddelivery.order.refund.RefundCommand cmd = com.fooddelivery.order.refund.RefundCommand.builder()
                        .orderId(orderId)
                        .amount(quoteAmount)
                        .items(commandItems.isEmpty() ? null : commandItems)
                        .reasonCode(reason)
                        .reasonText(description)
                        .faultType(com.fooddelivery.order.enums.FaultType.CUSTOMER_FAULT)
                        .destination(com.fooddelivery.common.enums.RefundDestination.STORE_CREDIT) // default to store credit for chat
                        .source(com.fooddelivery.order.enums.RefundSource.CUSTOMER_TICKET)
                        .initiatorType(com.fooddelivery.order.enums.InitiatorType.CUSTOMER)
                        .initiatorId(customerId)
                        .ticketId(ticket.getId())
                        .idempotencyKey("chat_" + event.getAggregateId())
                        .build();
                        
                refundService.request(cmd);
                
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


}
