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
import com.fooddelivery.customer.client.LedgerClient;
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
    private final LedgerClient ledgerClient;

    /**
     * A capture taken by an external gateway. Only CARD and UPI reach here.
     *
     * <p>Wallet routing happens on the payment method in {@code CreatedState}; this method is only
     * called when an external gateway is genuinely involved.
     */
    public void bookPaymentCaptured(Order order, String gateway) {
        if (gateway == null || gateway.isBlank()) {
            // Previously this hashed the literal string "null" and every card capture in the system
            // landed in one bogus account. Fail where the fact is missing, not where it is read.
            throw new IllegalStateException(
                    "Cannot book a gateway capture for order " + order.getId() + " without a gateway name");
        }

        UUID gatewayAccountId = LedgerAccounts.gatewayOwnerId(gateway);
        
        LedgerLeg leg = new LedgerLeg();
        leg.setFromType(LedgerAccountType.GATEWAY_RECEIVABLE);
        leg.setFromId(gatewayAccountId);
        leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
        leg.setToId(LedgerAccounts.PLATFORM_CLEARING);
        leg.setAmount(order.getTotalAmount());
        leg.setCategory(ChargeCategory.ORDER_TOTAL);

        UUID txId = DeterministicIdUtils.ledgerId("customer-application", order.getId(), "PAYMENT_CAPTURE");
        
        LedgerTransactionCommand cmd = new LedgerTransactionCommand(txId, order.getId(), "customer-application", "PAYMENT_CAPTURE", List.of(leg));
        saveOutboxEvent(txId, cmd);
    }


    /**
     * A capture taken from the customer's prepaid balance.
     *
     * <p>A wallet payment is a real capture and must appear in the book: without it the DELIVERED
     * transaction credits a restaurant and a driver against money the ledger says never arrived.
     * The money is already settled inside WalletService, so the leg debits the customer's prepaid
     * account rather than an external receivable -- there is no gateway holding it.
     */
    public void bookWalletCaptured(Order order) {
        if (order.getCustomerId() == null) {
            throw new IllegalStateException(
                    "Cannot book a wallet capture for order " + order.getId() + " without a customer");
        }

        LedgerLeg leg = new LedgerLeg();
        leg.setFromType(LedgerAccountType.CUSTOMER_CREDIT);
        leg.setFromId(order.getCustomerId());
        leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
        leg.setToId(LedgerAccounts.PLATFORM_CLEARING);
        leg.setAmount(order.getTotalAmount());
        leg.setCategory(ChargeCategory.ORDER_TOTAL);

        UUID txId = DeterministicIdUtils.ledgerId("customer-application", order.getId(), "WALLET_CAPTURE");

        LedgerTransactionCommand cmd = new LedgerTransactionCommand(txId, order.getId(), "customer-application", "WALLET_CAPTURE", List.of(leg));
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
        LedgerTransactionCommand cmd = new LedgerTransactionCommand(txId, order.getId(), "customer-application", "DELIVERED", legs);
        saveOutboxEvent(txId, cmd);
    }

    public void bookRefund(Order order, Refund refund, String gatewayName) {
        List<LedgerLeg> legs = new ArrayList<>();
        
        // 1. Main refund leg (only for ORIGINAL_METHOD)
        if (refund.getDestination() == RefundDestination.ORIGINAL_METHOD) {
            // The gateway comes from the payment intent, not from the payment method: CARD and UPI are
            // how the customer paid, not where the money is held.
            UUID gatewayAccountId = LedgerAccounts.gatewayOwnerId(gatewayName);
            
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
                if (order.getRestaurantId() == null) {
                    throw new IllegalStateException("Cannot calculate clawback: Restaurant payout is null on the Order");
                }
                if (order.getRestaurantPayout() == null) {
                    throw new IllegalStateException("Cannot calculate clawback: Restaurant payout is null on the Order");
                }
                
                java.math.BigDecimal alreadyClawedBack = getAlreadyClawedBack(order.getId(), order.getRestaurantId(), LedgerAccountType.RESTAURANT_PAYABLE);
                java.math.BigDecimal restPayout = order.getRestaurantPayout();
                
                java.math.BigDecimal clawback = restPayout.multiply(refundRatio).setScale(2, java.math.RoundingMode.HALF_UP);
                if (clawback.compareTo(refund.getAmount()) > 0) clawback = refund.getAmount();
                
                java.math.BigDecimal maxAllowed = restPayout.subtract(alreadyClawedBack);
                if (clawback.compareTo(maxAllowed) > 0) clawback = maxAllowed;
                
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
                if (order.getDeliveryExecutiveId() == null) {
                    throw new IllegalStateException("Cannot calculate clawback: Rider payout is null on the Order");
                }
                if (order.getDriverNetPayout() == null) {
                    throw new IllegalStateException("Cannot calculate clawback: Rider payout is null on the Order");
                }
                
                java.math.BigDecimal alreadyClawedBack = getAlreadyClawedBack(order.getId(), order.getDeliveryExecutiveId(), LedgerAccountType.DRIVER_PAYABLE);
                // The tip reached the rider too (a DELIVERY_FEE leg at delivery), so a refund for the
                // rider's fault claws it back in the same proportion as the fee.
                java.math.BigDecimal riderPayout = order.getDriverNetPayout()
                        .add(order.getTipAmount() != null ? order.getTipAmount() : java.math.BigDecimal.ZERO);
                
                java.math.BigDecimal clawback = riderPayout.multiply(refundRatio).setScale(2, java.math.RoundingMode.HALF_UP);
                if (clawback.compareTo(refund.getAmount()) > 0) clawback = refund.getAmount();
                
                java.math.BigDecimal maxAllowed = riderPayout.subtract(alreadyClawedBack);
                if (clawback.compareTo(maxAllowed) > 0) clawback = maxAllowed;
                
                if (clawback.compareTo(java.math.BigDecimal.ZERO) > 0) {
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
            String refundLeg = "REFUND:" + refund.getId();
            UUID txId = DeterministicIdUtils.ledgerId("customer-application", order.getId(), refundLeg);
            LedgerTransactionCommand cmd = new LedgerTransactionCommand(txId, order.getId(), "customer-application", refundLeg, legs);
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

    /**
     * What has already been clawed back from this payee on this order.
     *
     * <p>Matched on the account's <em>owner</em>, not on {@code accountId}: a statement line's
     * accountId is the ledger account's own surrogate key, so comparing it against a restaurant or
     * driver id matched nothing and the cap silently never applied -- two partial refunds each clawed
     * back a full pro-rata share.
     *
     * <p>Fails closed. An unreachable ledger used to yield zero, which is indistinguishable from
     * "nothing clawed back yet" and would let the cap be exceeded. Refusing to book is recoverable;
     * over-charging a restaurant is not.
     */
    private java.math.BigDecimal getAlreadyClawedBack(UUID orderId, UUID payeeId, LedgerAccountType payeeAccountType) {
        List<com.fooddelivery.common.dto.ledger.LedgerStatementLineDto> lines;
        try {
            lines = ledgerClient.getStatementByReference(orderId);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot cap the clawback for order " + orderId + ": the ledger statement is unavailable ("
                    + e.getMessage() + "). Booking without the cap could claw back more than the payee earned.", e);
        }
        if (lines == null || lines.isEmpty()) return java.math.BigDecimal.ZERO;

        return lines.stream()
            .filter(l -> payeeId.equals(l.getOwnerId()) && l.getOwnerType() == payeeAccountType)
            .filter(l -> l.getCategory() == ChargeCategory.CLAWBACK)
            .filter(l -> l.getDirection() == com.fooddelivery.common.enums.TransactionDirection.DEBIT)
            .map(com.fooddelivery.common.dto.ledger.LedgerStatementLineDto::getAmount)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
