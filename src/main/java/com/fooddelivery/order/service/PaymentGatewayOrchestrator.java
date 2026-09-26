package com.fooddelivery.order.service;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fooddelivery.common.client.PaymentServiceClient;
import com.fooddelivery.common.dto.payment.CreateOrderRequest;
import com.fooddelivery.common.dto.payment.CreatePaymentResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class PaymentGatewayOrchestrator {
    

    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final PaymentServiceClient paymentClient;

    private static final String INTERNAL_INTENT_PREFIX = "INTERNAL_";

    public String generateIntent(Order order, com.fooddelivery.common.enums.PaymentMethod paymentMethod) {
        log.info("PAYMENT_INTENT_REQUESTED orderId={} paymentMethod={} amount={}", order.getId(), paymentMethod, order.getTotalAmount());
        try {
            if (paymentMethod == null) {
                throw new IllegalArgumentException("paymentMethod is required");
            }
            com.fooddelivery.common.enums.PaymentMethod method = paymentMethod;
            if (method == com.fooddelivery.common.enums.PaymentMethod.WALLET) {
                log.info("PAYMENT_GATEWAY_BYPASSED orderId={} paymentMethod=WALLET", order.getId());
                PaymentIntent intent = PaymentIntent.builder()
                    .id(UUID.randomUUID())
                    .internalOrderId(order.getId())
                    .gatewayOrderId(INTERNAL_INTENT_PREFIX + UUID.randomUUID().toString())
                    .amount(order.getTotalAmount())
                    .status(com.fooddelivery.common.constants.PaymentIntentStatus.INITIATED)
                    .paymentMethod(method)
                    .createdAt(java.time.Instant.now())
                    .gatewayName(null)
                    .build();
                paymentIntentRepository.save(intent);
                return intent.getGatewayOrderId();
            }

            CreateOrderRequest request = new CreateOrderRequest(order.getId().toString(), order.getTotalAmount())
                    .paymentMethod(method);
            CreatePaymentResponse response = paymentClient.createOrder(request);
            if (response != null) {
                PaymentIntent intent = PaymentIntent.builder().id(UUID.randomUUID()).internalOrderId(order.getId()).gatewayOrderId(response.gatewayOrderId()).amount(order.getTotalAmount()).status(com.fooddelivery.common.constants.PaymentIntentStatus.INITIATED).paymentMethod(method).gatewayName(response.gateway()).createdAt(java.time.Instant.now()).build();
                paymentIntentRepository.save(intent);
                log.info("PAYMENT_INTENT_CREATED orderId={} gateway={} gatewayOrderId={} paymentMethod={}",
                        order.getId(), response.gateway(), response.gatewayOrderId(), method);
                return response.gatewayOrderId();
            } else {
                log.error("PAYMENT_INTENT_EMPTY_RESPONSE orderId={} paymentMethod={}", order.getId(), method);
                throw new RuntimeException("Failed to generate payment intent.");
            }
        } catch (Exception e) {
            log.error("PAYMENT_INTENT_FAILED orderId={} paymentMethod={} errorType={} error={}",
                    order.getId(), paymentMethod, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Error communicating with PaymentGatewayIntegration service", e);
        }
    }

    
}
