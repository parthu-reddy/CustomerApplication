package com.fooddelivery.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A deliberate override of automatic dispatch.
 *
 * <p>The reason is kept with the durable outbox event, so an administrator's decision remains
 * explainable after the order itself moves beyond the intervention queue.
 */
public record AdminManualAssignmentRequest(
        @NotNull UUID deliveryExecutiveId,
        @NotBlank @Size(min = 5, max = 500) String reason) {
}
