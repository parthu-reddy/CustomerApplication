package com.fooddelivery.order.service;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayOrchestrator {

    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final com.fooddelivery.order.repository.IOutboxEventRepository outboxEventRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${payment-service.base-url:http://localhost:8080}")
    private String paymentServiceBaseUrl;

    @Transactional
    public String generateUpiIntent(Order order) {
        log.info("Requesting Payment Intent for Order: {} from PaymentGatewayIntegration service", order.getId());

        try {
            // Call PaymentGatewayIntegration service
            String paymentServiceUrl = paymentServiceBaseUrl + "/api/v1/payments/create-order?gateway=VYAPAR";
            java.util.Map<String, Object> request = java.util.Map.of(
                "internalOrderId", order.getId().toString(),
                "amountInInr", order.getTotalAmount()
            );
            
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(paymentServiceUrl, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String returnedGatewayOrderId = response.getBody();
                PaymentIntent intent = PaymentIntent.builder()
                        .id(UUID.randomUUID())
                        .internalOrderId(order.getId())
                        .gatewayOrderId(returnedGatewayOrderId)
                        .amount(order.getTotalAmount())
                        .status("INITIATED")
                        .createdAt(LocalDateTime.now())
                        .build();
        
                paymentIntentRepository.save(intent);
                return returnedGatewayOrderId;
            } else {
                log.error("Failed to generate payment intent. Status code: {}", response.getStatusCode());
                throw new RuntimeException("Failed to generate payment intent. Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error communicating with PaymentGatewayIntegration service", e);
            throw new RuntimeException("Error communicating with PaymentGatewayIntegration service", e);
        }
    }
}
