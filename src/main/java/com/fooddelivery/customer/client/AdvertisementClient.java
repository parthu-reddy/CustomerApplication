package com.fooddelivery.customer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;
import java.util.List;

@FeignClient(name = "bidding-engine", fallback = AdvertisementClientFallback.class)
public interface AdvertisementClient {

    @PostMapping("/api/v1/ads/serve")
    Object fetchAds(@RequestBody Map<String, Object> bidRequest);
}
