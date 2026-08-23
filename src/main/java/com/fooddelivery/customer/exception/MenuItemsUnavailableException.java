package com.fooddelivery.customer.exception;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class MenuItemsUnavailableException extends RuntimeException {
    private final List<UUID> unavailableItemIds;

    public MenuItemsUnavailableException(String message, List<UUID> unavailableItemIds) {
        super(message);
        this.unavailableItemIds = unavailableItemIds;
    }

    
    public List<UUID> getUnavailableItemIds() {
        return this.unavailableItemIds;
    }
}
