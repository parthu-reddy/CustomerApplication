package com.fooddelivery.customer.exception;

import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
public class MenuItemsUnavailableException extends RuntimeException {
    private final List<UUID> unavailableItemIds;

    public MenuItemsUnavailableException(String message, List<UUID> unavailableItemIds) {
        super(message);
        this.unavailableItemIds = unavailableItemIds;
    }
}
