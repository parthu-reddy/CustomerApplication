package com.fooddelivery.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A deliberate cancellation after dispatch has reached manual intervention.
 */
public record AdminManualCancellationRequest(
        @NotBlank @Size(min = 5, max = 500) String reason) {
}
