package com.fooddelivery.customer.exception;

import com.fooddelivery.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CustomerGlobalExceptionHandler {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CustomerGlobalExceptionHandler.class);

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled Exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("An unexpected error occurred."));
    }
}
