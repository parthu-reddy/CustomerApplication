package com.fooddelivery.customer.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import com.fooddelivery.common.enums.PaymentMethod;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderRequest {
    /**
     * The quote being redeemed. Required: checkout charges the quoted price rather than
     * recomputing one, so an order without a quote has no price.
     */
    @NotNull
    private UUID quoteId;
    @NotNull
    private UUID customerId;
    private String customerName;
    @NotNull
    private UUID restaurantId;
    @NotNull
    private UUID deliveryAddressId;
    @NotNull(message = "paymentMethod is required")
    @io.swagger.v3.oas.annotations.media.Schema(
            allowableValues = {"CARD", "UPI", "WALLET"},
            description = "Prepaid payment method for a new order.")
    private PaymentMethod paymentMethod;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;
}
