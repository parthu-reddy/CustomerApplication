package com.fooddelivery.customer.client;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.service.CustomerOrderService.MenuItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fooddelivery.customer.dto.RestaurantDto;

@FeignClient(name = "restaurant-service", fallback = RestaurantClientFallback.class, contextId = "restaurantClient")
public interface RestaurantClient {

    @Cacheable(value = "customer-app:nearbyRestaurants", key = "#lat + '-' + #lng + '-' + #radius", sync = true)
    @GetMapping("/api/v1/restaurants/nearby")
    ApiResponse<List<RestaurantDto>> getNearbyRestaurants(@RequestParam("lat") double lat, 
                                                   @RequestParam("lng") double lng, 
                                                   @RequestParam("radius") double radius);

    @Cacheable(value = "customer-app:brandOutlets", key = "#brandId + '-' + #lat + '-' + #lng + '-' + #radius", sync = true)
    @GetMapping("/api/v1/restaurants/brands/{brandId}/outlets")
    ApiResponse<List<RestaurantDto>> getBrandOutlets(@PathVariable("brandId") UUID brandId, 
                                              @RequestParam("lat") double lat, 
                                              @RequestParam("lng") double lng, 
                                              @RequestParam("radius") double radius);

    @Cacheable(value = "customer-app:restaurantDetails", key = "#id", sync = true)
    @GetMapping("/api/v1/restaurants/{id}")
    Map<String, Object> getRestaurantById(@PathVariable("id") UUID id);

    /**
     * The outlet's name, brand and IANA {@code timeZone}. The earnings summary reads its calendar
     * periods in that zone. Not cached: a stale or made-up zone would put orders in the wrong day.
     */
    @GetMapping("/api/v1/internal/restaurants/outlets/{outletId}/summary")
    Map<String, String> getOutletSummary(@PathVariable("outletId") UUID outletId);

    /** Supplier details for a tax invoice (legal name, GSTIN, FSSAI). Not cached: read once per invoice issued. */
    @GetMapping("/api/v1/internal/restaurants/outlets/{outletId}/invoice-details")
    Map<String, Object> getInvoiceDetails(@PathVariable("outletId") UUID outletId);

    @Cacheable(value = "customer-app:menuItemsBatch", key = "#id + '-' + #ids", sync = true)
    @GetMapping("/api/v1/restaurants/{id}/menu/batch")
    List<MenuItemDTO> getMenuItemsBatch(@PathVariable("id") UUID id, @RequestParam("ids") String ids);

    @GetMapping("/api/v1/internal/restaurants/orders/{orderId}/status")
    Map<String, String> getInternalOrderStatus(@PathVariable("orderId") UUID orderId);
}
