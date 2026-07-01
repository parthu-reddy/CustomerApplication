package com.fooddelivery.customer.exception;

public class DeliveryPartnerUnavailableException extends RuntimeException {
    
    private final String errorCode;

    public DeliveryPartnerUnavailableException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
