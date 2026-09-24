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

    /**
     * Tip for the rider, whole rupees, 0 to 500. Optional (absent is no tip). Added on top of the
     * quoted total: the quote prices the food and delivery, the tip is the customer's own choice.
     */
    @jakarta.validation.constraints.PositiveOrZero
    @jakarta.validation.constraints.Max(500)
    @jakarta.validation.constraints.Digits(integer = 3, fraction = 0, message = "tipAmount must be whole rupees")
    private java.math.BigDecimal tipAmount;
}
