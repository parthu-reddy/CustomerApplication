package com.fooddelivery.customer.exception;

import com.fooddelivery.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@lombok.extern.slf4j.Slf4j
public class CustomerGlobalExceptionHandler {

    /*
     * The single advice for this service.
     *
     * There were two: this one and CustomerExceptionHandler, both registering a handler for
     * DeliveryPartnerUnavailableException AND MenuItemsUnavailableException, neither carrying
     * @Order -- so which one answered was undefined. The other was strictly worse: it dropped
     * the unavailable item ids and the error code. It has been deleted rather than deprecated,
     * per the project's no-backward-compatibility rule. It was referenced by nothing.
     */

    @ExceptionHandler(DeliveryPartnerUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryPartnerUnavailableException(DeliveryPartnerUnavailableException ex) {
        log.warn("DeliveryPartnerUnavailableException: {}", ex.getMessage());
        // The code is passed through. This called the one-argument ApiResponse.error(message)
        // and threw the code away, so the wire always carried errorCode: null even though both
        // throw sites supply AppConstants.ERROR_NO_DELIVERY_PARTNER_NEARBY. A client could not
        // then tell "no rider nearby" from "outside the service area" -- opposite remedies for
        // the customer -- and the UI had a dead branch matching on a code that never arrived.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(QuoteExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleQuoteExpiredException(QuoteExpiredException ex) {
        log.warn("QuoteExpiredException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage(), "QUOTE_EXPIRED"));
    }

    @ExceptionHandler(MenuItemsUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMenuItemsUnavailableException(MenuItemsUnavailableException ex) {
        log.warn("MenuItemsUnavailableException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.<Object>builder().success(false).message(ex.getMessage()).data(ex.getUnavailableItemIds()).build());
    }

}
