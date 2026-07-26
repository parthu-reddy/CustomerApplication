package com.fooddelivery.customer.client;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.service.CustomerOrderService.MenuItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "restaurant-service", fallback = RestaurantClientFallback.class)
public interface RestaurantClient {

    @GetMapping("/api/v1/restaurants/nearby")
    ApiResponse<List<Object>> getNearbyRestaurants(@RequestParam("lat") double lat, 
                                                   @RequestParam("lng") double lng, 
                                                   @RequestParam("radius") double radius);

    @GetMapping("/api/v1/restaurants/brands/{brandId}/outlets")
    ApiResponse<List<Object>> getBrandOutlets(@PathVariable("brandId") UUID brandId, 
                                              @RequestParam("lat") double lat, 
                                              @RequestParam("lng") double lng, 
                                              @RequestParam("radius") double radius);

    @GetMapping("/api/v1/restaurants/{id}")
    Map<String, Object> getRestaurantById(@PathVariable("id") UUID id);

    @GetMapping("/api/v1/restaurants/{id}/menu/batch")
    List<MenuItemDTO> getMenuItemsBatch(@PathVariable("id") UUID id, @RequestParam("ids") String ids);

    @GetMapping("/api/v1/internal/restaurants/orders/{orderId}/status")
    Map<String, String> getInternalOrderStatus(@PathVariable("orderId") UUID orderId);
}
