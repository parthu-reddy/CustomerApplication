package com.fooddelivery.order.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AggregateType;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.LedgerAccounts;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.OutboxStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.common.util.DeterministicIdUtils;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderCharge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerBookkeeper {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final LedgerAccountResolver accountResolver;

    public void bookPaymentCaptured(Order order, String gateway) {
        if ("COD".equalsIgnoreCase(gateway) || "WALLET".equalsIgnoreCase(gateway)) {
            return;
        }

        UUID gatewayAccountId = DeterministicIdUtils.ledgerId("gateway", gateway, "");
        
        LedgerLeg leg = new LedgerLeg();
        leg.setFromType(LedgerAccountType.GATEWAY_RECEIVABLE);
        leg.setFromId(gatewayAccountId);
        leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
        leg.setToId(LedgerAccounts.PLATFORM_CLEARING);
        leg.setAmount(order.getTotalAmount());
        leg.setCategory(ChargeCategory.ORDER_TOTAL);

        UUID txId = DeterministicIdUtils.ledgerId("customer-application", order.getId(), "PAYMENT_CAPTURE");
        
        LedgerTransactionCommand cmd = new LedgerTransactionCommand(txId, order.getId(), "customer-application", List.of(leg));
        saveOutboxEvent(txId, cmd);
    }

    public void bookPaymentSuccess(Order order) {
        bookPaymentCaptured(order, "unknown");
    }

    public void bookCashCollected(Order order) {
        LedgerLeg leg = new LedgerLeg();
        leg.setFromType(LedgerAccountType.DRIVER_PAYABLE);
        leg.setFromId(order.getDeliveryExecutiveId());
        leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
        leg.setToId(LedgerAccounts.PLATFORM_CLEARING);
        leg.setAmount(order.getTotalAmount());
        leg.setCategory(ChargeCategory.CASH_COLLECTED);

        UUID txId = DeterministicIdUtils.ledgerId("customer-application", order.getId(), "CASH_COLLECTED");
        
        LedgerTransactionCommand cmd = new LedgerTransactionCommand(txId, order.getId(), "customer-application", List.of(leg));
        saveOutboxEvent(txId, cmd);
    }

    public void bookDelivered(Order order) {
        List<LedgerLeg> legs = new ArrayList<>();
        
        for (OrderCharge charge : order.getCharges()) {
            ResolvedAccount from = accountResolver.resolve(charge.getPayerType(), charge.getPayerId(), order);
            ResolvedAccount to = accountResolver.resolve(charge.getPayeeType(), charge.getPayeeId(), order);
            
            LedgerLeg leg = new LedgerLeg();
            leg.setFromType(from.getType());
            leg.setFromId(from.getId());
            leg.setToType(to.getType());
            leg.setToId(to.getId());
            leg.setAmount(charge.getAmount());
            leg.setCategory(charge.getCategory());
            leg.setDescription(charge.getDescription());
            legs.add(leg);
        }
        


        UUID txId = DeterministicIdUtils.ledgerId("customer-application", order.getId(), "DELIVERED");
        LedgerTransactionCommand cmd = new LedgerTransactionCommand(txId, order.getId(), "customer-application", legs);
        saveOutboxEvent(txId, cmd);
    }

    public void bookRefund(Order order, Refund refund) {
        List<LedgerLeg> legs = new ArrayList<>();
        
        // 1. Main refund leg (only for ORIGINAL_METHOD)
        if (refund.getDestination() == RefundDestination.ORIGINAL_METHOD) {
            String gateway = order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "unknown";
            UUID gatewayAccountId = DeterministicIdUtils.ledgerId("gateway", gateway, "");
            
            LedgerLeg leg = new LedgerLeg();
            leg.setFromType(LedgerAccountType.PLATFORM_CLEARING);
            leg.setFromId(LedgerAccounts.PLATFORM_CLEARING);
            leg.setToType(LedgerAccountType.GATEWAY_RECEIVABLE);
            leg.setToId(gatewayAccountId);
            leg.setAmount(refund.getAmount());
            leg.setCategory(ChargeCategory.REFUND);
            leg.setAuthorizedBy(refund.getInitiatedByType().name());
            legs.add(leg);
        }

        // 2. Clawbacks (only if order was delivered)
        if (order.getDeliveredAt() != null || "DELIVERED".equals(order.getDeliveryStatus() != null ? order.getDeliveryStatus().name() : "")) {
            java.math.BigDecimal totalCustomerCharge = order.getTotalAmount();
            java.math.BigDecimal refundRatio = java.math.BigDecimal.ONE;
            if (refund.getAmount() != null && totalCustomerCharge != null && totalCustomerCharge.compareTo(java.math.BigDecimal.ZERO) > 0) {
                refundRatio = refund.getAmount().divide(totalCustomerCharge, 4, java.math.RoundingMode.HALF_UP);
                if (refundRatio.compareTo(java.math.BigDecimal.ONE) > 0) refundRatio = java.math.BigDecimal.ONE;
            }

            if (refund.getFaultType() == FaultType.RESTAURANT_FAULT) {
                if (order.getRestaurantPayout() == null) {
                    throw new IllegalStateException("Cannot calculate clawback: Restaurant payout is null on the Order");
                }
                java.math.BigDecimal restPayout = order.getRestaurantPayout();
                java.math.BigDecimal clawback = restPayout.multiply(refundRatio).setScale(2, java.math.RoundingMode.HALF_UP);
                if (clawback.compareTo(refund.getAmount()) > 0) clawback = refund.getAmount();
                if (clawback.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    LedgerLeg leg = new LedgerLeg();
                    leg.setFromType(LedgerAccountType.RESTAURANT_PAYABLE);
                    leg.setFromId(order.getRestaurantId());
                    leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
                    leg.setToId(LedgerAccounts.PLATFORM_CLEARING);
                    leg.setAmount(clawback);
                    leg.setCategory(ChargeCategory.CLAWBACK);
                    leg.setDescription("Refund: " + refund.getId() + " - " + refund.getReasonCode());
                    leg.setAuthorizedBy(refund.getInitiatedByType().name());
                    legs.add(leg);
                }
            } else if (refund.getFaultType() == FaultType.RIDER_FAULT) {
                if (order.getDriverNetPayout() == null) {
                    throw new IllegalStateException("Cannot calculate clawback: Rider payout is null on the Order");
                }
                java.math.BigDecimal riderPayout = order.getDriverNetPayout();
                java.math.BigDecimal clawback = riderPayout.multiply(refundRatio).setScale(2, java.math.RoundingMode.HALF_UP);
                if (clawback.compareTo(refund.getAmount()) > 0) clawback = refund.getAmount();
                if (clawback.compareTo(java.math.BigDecimal.ZERO) > 0 && order.getDeliveryExecutiveId() != null) {
                    LedgerLeg leg = new LedgerLeg();
                    leg.setFromType(LedgerAccountType.DRIVER_PAYABLE);
                    leg.setFromId(order.getDeliveryExecutiveId());
                    leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
                    leg.setToId(LedgerAccounts.PLATFORM_CLEARING);
                    leg.setAmount(clawback);
                    leg.setCategory(ChargeCategory.CLAWBACK);
                    leg.setDescription("Refund: " + refund.getId() + " - " + refund.getReasonCode());
                    leg.setAuthorizedBy(refund.getInitiatedByType().name());
                    legs.add(leg);
                }
            } else {
                java.math.BigDecimal clawback = refund.getAmount();
                LedgerLeg leg = new LedgerLeg();
                leg.setFromType(LedgerAccountType.PLATFORM_REVENUE);
                leg.setFromId(LedgerAccounts.PLATFORM_REVENUE);
                leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
                leg.setToId(LedgerAccounts.PLATFORM_CLEARING);
                leg.setAmount(clawback);
                leg.setCategory(ChargeCategory.CLAWBACK);
                leg.setDescription("Refund: " + refund.getId() + " - " + refund.getReasonCode());
                leg.setAuthorizedBy(refund.getInitiatedByType().name());
                legs.add(leg);
            }
        }
        
        if (!legs.isEmpty()) {
            UUID txId = DeterministicIdUtils.ledgerId("customer-application", refund.getId(), "REFUND");
            LedgerTransactionCommand cmd = new LedgerTransactionCommand(txId, order.getId(), "customer-application", legs);
            saveOutboxEvent(txId, cmd);
        }
    }

    private void saveOutboxEvent(UUID transferId, LedgerTransactionCommand cmd) {
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AggregateType.LEDGER)
                    .aggregateId(transferId.toString())
                    .eventType(EventType.LEDGER_TRANSACTION_REQUEST)
                    .payload(objectMapper.writeValueAsString(cmd))
                    .createdAt(LocalDateTime.now())
                    .status(OutboxStatus.UNPROCESSED)
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.info("Saved LEDGER_TRANSACTION_REQUEST to outbox for transfer: {}", transferId);
        } catch (Exception e) {
            log.error("Failed to save LEDGER_TRANSACTION_REQUEST event", e);
            throw new RuntimeException("Failed to save LEDGER_TRANSACTION_REQUEST event", e);
        }
    }
}
