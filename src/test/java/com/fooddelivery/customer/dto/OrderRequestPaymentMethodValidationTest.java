package com.fooddelivery.customer.dto;

import com.fooddelivery.common.enums.PaymentMethod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRequestPaymentMethodValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private OrderRequest request(PaymentMethod method) {
        return OrderRequest.builder()
                .quoteId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .restaurantId(UUID.randomUUID())
                .deliveryAddressId(UUID.randomUUID())
                .paymentMethod(method)
                .items(List.of(OrderItemRequest.builder()
                        .menuItemId(UUID.randomUUID())
                        .quantity(1)
                        .build()))
                .build();
    }

    @Test
    void prepaidMethodsAreAccepted() {
        for (PaymentMethod method : List.of(PaymentMethod.CARD, PaymentMethod.UPI, PaymentMethod.WALLET)) {
            assertTrue(validator.validate(request(method)).isEmpty(), method + " should be accepted");
        }
    }

    @Test
    void missingPaymentMethodIsRejected() {
        assertTrue(validator.validate(request(null)).stream()
                .anyMatch(v -> v.getMessage().contains("required")));
    }
}
