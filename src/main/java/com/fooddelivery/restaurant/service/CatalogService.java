package com.fooddelivery.restaurant.service;

import com.fooddelivery.restaurant.entity.MenuItem;
import com.fooddelivery.restaurant.repository.IMenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final IMenuItemRepository menuItemRepository;

    @Transactional
    public MenuItem addMenuItem(UUID restaurantId, MenuItem item) {
        if (item.getPrice() == null || item.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Menu item price must be positive");
        }
        item.setRestaurantId(restaurantId);
        if (item.getId() == null) {
            item.setId(UUID.randomUUID());
        }
        return menuItemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<MenuItem> getMenuItems(UUID restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId);
    }

    @Transactional
    public MenuItem updateMenuItem(UUID restaurantId, UUID itemId, MenuItem updatedItem) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        
        if (!item.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Menu item does not belong to this restaurant");
        }
        
        if (updatedItem.getPrice() != null && updatedItem.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Menu item price must be positive");
        }
        
        if (updatedItem.getPrice() != null) {
            item.setPrice(updatedItem.getPrice());
        }
        if (updatedItem.getIsAvailable() != null) {
            item.setIsAvailable(updatedItem.getIsAvailable());
        }
        if (updatedItem.getName() != null) {
            item.setName(updatedItem.getName());
        }
        if (updatedItem.getDescription() != null) {
            item.setDescription(updatedItem.getDescription());
        }
        
        return menuItemRepository.save(item);
    }
}
