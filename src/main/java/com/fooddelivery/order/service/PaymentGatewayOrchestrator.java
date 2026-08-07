package com.fooddelivery.order.service;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fooddelivery.customer.client.PaymentClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class PaymentGatewayOrchestrator {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentGatewayOrchestrator.class);
    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public String generateUpiIntent(Order order) {
        log.info("Requesting Payment Intent for Order: {} from PaymentGatewayIntegration service", order.getId());
        try {
            // Call PaymentGatewayIntegration service
            java.util.Map<String, Object> request = java.util.Map.of("internalOrderId", order.getId().toString(), "amountInInr", order.getTotalAmount());
            String returnedGatewayOrderId = paymentClient.createOrder("VYAPAR", request);
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

    @java.lang.SuppressWarnings("all")
    public PaymentGatewayOrchestrator(final IPaymentIntentRepository paymentIntentRepository, final IOrderRepository orderRepository, final PaymentClient paymentClient) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
    }
}
