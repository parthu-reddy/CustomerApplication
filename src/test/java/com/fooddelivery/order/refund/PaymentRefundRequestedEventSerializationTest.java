package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.PaymentRefundRequestedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PaymentRefundRequestedEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static PaymentRefundRequestedEvent refund() {
        return PaymentRefundRequestedEvent.builder()
                .refundId("ref_123")
                .orderId("6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02")
                .gatewayOrderId("pay_12345")
                .amount(new BigDecimal("15.50"))
                .gatewayName(com.fooddelivery.common.enums.PaymentGateway.RAZORPAY)
                .build();
    }

    @Test
    public void testSerializationMatchesContractKeys() {
        PaymentRefundRequestedEvent event = refund();

        var tree = objectMapper.valueToTree(event);
        
        Set<String> fieldNames = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(tree.fieldNames(), Spliterator.ORDERED), false)
                .collect(Collectors.toSet());

        Set<String> expectedKeys = Set.of(
                "refundId",
                "orderId",
                "gatewayOrderId",
                "amount",
                "gatewayName"
        );

        assertEquals(expectedKeys, fieldNames);
    }

    /**
     * PaymentGatewayIntegration's PaymentEventConsumer binds this class from the JSON RefundService
     * publishes. The keys matching is not enough: the gateway must get back the same refund, and
     * BigDecimal equality is scale-sensitive, so 15.50 arriving as 15.5 fails here.
     */
    @Test
    public void theGatewayBindsExactlyTheRefundThatWasRequested() throws Exception {
        PaymentRefundRequestedEvent event = refund();

        PaymentRefundRequestedEvent bound = objectMapper.readValue(objectMapper.writeValueAsString(event),
                PaymentRefundRequestedEvent.class);

        assertEquals(event, bound);
    }
}
