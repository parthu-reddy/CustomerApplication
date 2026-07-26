package com.fooddelivery.customer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@FeignClient(name = "payment-service", fallback = PaymentClientFallback.class)
public interface PaymentClient {

    @PostMapping("/api/v1/payments/create-order")
    String createOrder(@RequestParam("gateway") String gateway, @RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/payments/refund")
    String refundOrder(@RequestParam("gateway") String gateway, @RequestBody Map<String, Object> request);
}
