package com.fooddelivery.order.enums;

public enum ChargeEntityType {
    CUSTOMER(10),
    RESTAURANT(20),
    PLATFORM(30),
    DRIVER(40),
    GOVERNMENT(50);

    private final int code;

    ChargeEntityType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
