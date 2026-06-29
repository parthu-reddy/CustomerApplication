package com.fooddelivery.restaurant.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.restaurant.entity.MenuItem;
import com.fooddelivery.restaurant.repository.IMenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final com.fooddelivery.restaurant.service.CatalogService catalogService;

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<MenuItem>> addMenuItem(@PathVariable UUID restaurantId, @RequestBody MenuItem item) {
        try {
            MenuItem savedItem = catalogService.addMenuItem(restaurantId, item);
            return ResponseEntity.ok(ApiResponse.success(savedItem, "Menu item added successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<MenuItem>>> getMenuItems(@PathVariable UUID restaurantId) {
        List<MenuItem> items = catalogService.getMenuItems(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(items, "Menu items retrieved"));
    }

    @org.springframework.web.bind.annotation.PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<MenuItem>> updateMenuItem(
            @PathVariable UUID restaurantId, 
            @PathVariable UUID itemId, 
            @RequestBody MenuItem updatedItem) {
        try {
            MenuItem saved = catalogService.updateMenuItem(restaurantId, itemId, updatedItem);
            return ResponseEntity.ok(ApiResponse.success(saved, "Menu item updated"));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().equals("Menu item not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().equals("Menu item does not belong to this restaurant")) {
                return ResponseEntity.status(403).body(ApiResponse.<MenuItem>error(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
