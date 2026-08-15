package com.fooddelivery.customer.exception;

import com.fooddelivery.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@lombok.extern.slf4j.Slf4j
public class CustomerGlobalExceptionHandler {
    @java.lang.SuppressWarnings("all")

    @ExceptionHandler(DeliveryPartnerUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryPartnerUnavailableException(DeliveryPartnerUnavailableException ex) {
        log.warn("DeliveryPartnerUnavailableException: {}", ex.getMessage());
        // Return 409 Conflict with the specific error code injected into the message.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getErrorCode() + ": " + ex.getMessage()));
    }

    @ExceptionHandler(MenuItemsUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMenuItemsUnavailableException(MenuItemsUnavailableException ex) {
        log.warn("MenuItemsUnavailableException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.<Object>builder().success(false).message(ex.getMessage()).data(ex.getUnavailableItemIds()).build());
    }

}
