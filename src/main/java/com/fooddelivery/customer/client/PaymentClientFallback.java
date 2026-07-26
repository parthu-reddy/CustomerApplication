package com.fooddelivery.customer.client;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentClientFallback implements PaymentClient {

    @Override
    public String createOrder(String gateway, Map<String, Object> request) {
        // Return empty string so PaymentGatewayOrchestrator can detect fallback and throw clean exception
        return "";
    }

    @Override
    public String refundOrder(String gateway, Map<String, Object> request) {
        return "";
    }
}
