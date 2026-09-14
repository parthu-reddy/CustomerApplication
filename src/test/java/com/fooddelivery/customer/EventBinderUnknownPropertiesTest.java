package com.fooddelivery.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.PaymentRefundRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class EventBinderUnknownPropertiesTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testUnknownProperty() throws Exception {
        String json = "{\"orderId\":\"123\",\"gatewayOrderId\":\"456\",\"amount\":1.00,\"gatewayName\":\"RAZORPAY\",\"eventType\":\"PAYMENT_REFUND_REQUESTED\"}";
        PaymentRefundRequestedEvent event = objectMapper.readValue(json, PaymentRefundRequestedEvent.class);
        System.out.println("TEST_PASSED_SUCCESSFULLY: " + event.getOrderId());
        assertNotNull(event.getOrderId());
    }
}
