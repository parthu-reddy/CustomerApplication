package com.fooddelivery.customer.exception;

import java.util.List;
import java.util.UUID;

public class MenuItemsUnavailableException extends RuntimeException {
    private final List<UUID> unavailableItemIds;

    public MenuItemsUnavailableException(String message, List<UUID> unavailableItemIds) {
        super(message);
        this.unavailableItemIds = unavailableItemIds;
    }

    @java.lang.SuppressWarnings("all")
    public List<UUID> getUnavailableItemIds() {
        return this.unavailableItemIds;
    }
}
