package com.fooddelivery.customer.exception;

import com.fooddelivery.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class CustomerExceptionHandler {

    @ExceptionHandler(DeliveryPartnerUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryPartnerUnavailableException(DeliveryPartnerUnavailableException ex) {
        log.warn("Delivery partner unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MenuItemsUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMenuItemsUnavailableException(MenuItemsUnavailableException ex) {
        log.warn("Menu items unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }
}
