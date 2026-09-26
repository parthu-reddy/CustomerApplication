package com.fooddelivery.customer.service;

import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.common.client.WalletServiceClient;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletCheckoutService {
    private static final String SERVICE_NAME = "CustomerService";
    
    private final WalletServiceClient walletServiceClient;
    private final TransactionTemplate transactionTemplate;
    private final OutboxEventRepository outboxEventRepository;

    public void processWallet(Order order, PaymentMethod method, String intent) {
        if (method == PaymentMethod.WALLET) {
            transactionTemplate.executeWithoutResult(status -> {
                com.fooddelivery.common.dto.wallet.TransactionRequest txReq = new com.fooddelivery.common.dto.wallet.TransactionRequest();
                txReq.setAmount(order.getTotalAmount());
                txReq.setReferenceId(intent.startsWith("INTERNAL_") ? UUID.fromString(intent.replace("INTERNAL_", "")) : UUID.fromString(intent));
                txReq.setCategory(com.fooddelivery.common.enums.ChargeCategory.ORDER_TOTAL);
                txReq.setDescription("Order " + order.getId());
                log.info("WALLET_DEBIT_REQUESTED orderId={} customerId={} paymentIntentId={} amount={}",
                        order.getId(), order.getCustomerId(), intent, order.getTotalAmount());
                walletServiceClient.debit(com.fooddelivery.common.enums.WalletEntityType.CUSTOMER.name(), order.getCustomerId(), txReq, SERVICE_NAME);
                String payload = String.format("{\"eventType\":\"PAYMENT_COMPLETED\", \"orderId\":\"%s\", \"gatewayOrderId\":\"%s\"}", order.getId(), intent);
                com.fooddelivery.common.outbox.entity.OutboxEventEntity evt = com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.PAYMENT).aggregateId(intent).eventType(com.fooddelivery.common.constants.EventType.PAYMENT_COMPLETED).payload(payload).createdAt(java.time.Instant.now()).build();
                outboxEventRepository.save(evt);
                log.info("WALLET_PAYMENT_COMPLETED_ENQUEUED orderId={} paymentIntentId={} eventId={}",
                        order.getId(), intent, evt.getId());
            });
        }
    }
}
