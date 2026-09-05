package com.fooddelivery.order.service;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fooddelivery.common.client.PaymentServiceClient;
import com.fooddelivery.common.dto.payment.CreateOrderRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        log.info("Requesting Payment Intent for Order: {} using method: {} from PaymentGatewayIntegration service", order.getId(), paymentMethod);
        try {
            com.fooddelivery.common.enums.PaymentMethod method = paymentMethod != null ? paymentMethod : com.fooddelivery.common.enums.PaymentMethod.WALLET;
            if (method == com.fooddelivery.common.enums.PaymentMethod.WALLET || method == com.fooddelivery.common.enums.PaymentMethod.COD) {
                // Bypass external gateway for wallet and COD
                log.info("Bypassing external gateway for internal payment method: {}", method);
                PaymentIntent intent = PaymentIntent.builder()
                    .id(UUID.randomUUID())
                    .internalOrderId(order.getId())
                    .gatewayOrderId(INTERNAL_INTENT_PREFIX + UUID.randomUUID().toString())
                    .amount(order.getTotalAmount())
                    .status(com.fooddelivery.common.constants.PaymentIntentStatus.INITIATED)
                    .createdAt(LocalDateTime.now())
                    .gatewayName(null)
                    .build();
                paymentIntentRepository.save(intent);
                return intent.getGatewayOrderId();
            }

            // Map UI payment methods to Gateway enum values
            com.fooddelivery.common.enums.PaymentGateway targetGateway = com.fooddelivery.common.enums.PaymentGateway.RAZORPAY; // default for card/upi
            
            // Call PaymentGatewayIntegration service
            CreateOrderRequest request = new CreateOrderRequest(order.getId().toString(), order.getTotalAmount());
            String returnedGatewayOrderId = paymentClient.createOrder(targetGateway.name(), request);
            if (returnedGatewayOrderId != null && !returnedGatewayOrderId.isEmpty()) {
                PaymentIntent intent = PaymentIntent.builder().id(UUID.randomUUID()).internalOrderId(order.getId()).gatewayOrderId(returnedGatewayOrderId).amount(order.getTotalAmount()).status(com.fooddelivery.common.constants.PaymentIntentStatus.INITIATED).createdAt(LocalDateTime.now()).build();
                paymentIntentRepository.save(intent);
                return returnedGatewayOrderId;
            } else {
                log.error("Failed to generate payment intent.");
                throw new RuntimeException("Failed to generate payment intent.");
            }
        } catch (Exception e) {
            log.error("Error communicating with PaymentGatewayIntegration service", e);
            throw new RuntimeException("Error communicating with PaymentGatewayIntegration service", e);
        }
    }

    
}
