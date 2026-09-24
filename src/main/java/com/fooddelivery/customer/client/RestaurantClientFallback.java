package com.fooddelivery.customer.client;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.service.CustomerOrderService.MenuItemDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fooddelivery.customer.dto.RestaurantDto;

@Component("customerRestaurantClientFallback")
@lombok.RequiredArgsConstructor
public class RestaurantClientFallback implements RestaurantClient {

    @Override
    public ApiResponse<List<RestaurantDto>> getNearbyRestaurants(double lat, double lng, double radius) {
        return ApiResponse.success(new ArrayList<>(), "Restaurant service is currently unavailable");
    }

    @Override
    public ApiResponse<List<RestaurantDto>> getBrandOutlets(UUID brandId, double lat, double lng, double radius) {
        return ApiResponse.success(new ArrayList<>(), "Restaurant service is currently unavailable");
    }

    @Override
    public Map<String, Object> getRestaurantById(UUID id) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("id", id.toString());
        fallback.put("name", "Restaurant Unavailable");
        fallback.put("isActive", false);
        return fallback;
    }

    @Override
    public Map<String, Object> getInvoiceDetails(UUID outletId) {
        // An invoice must not be issued on made-up supplier details; the caller turns this into a 503.
        throw new IllegalStateException("Restaurant service unavailable for invoice details");
    }

    @Override
    public List<MenuItemDTO> getMenuItemsBatch(UUID id, String ids) {
        return new ArrayList<>();
    }

    @Override
    public Map<String, String> getInternalOrderStatus(UUID orderId) {
        Map<String, String> fallback = new HashMap<>();
        fallback.put("status", "UNKNOWN");
        return fallback;
    }
}
