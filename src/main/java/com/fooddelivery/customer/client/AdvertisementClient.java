package com.fooddelivery.customer.client;

import com.fooddelivery.customer.dto.AdRequestDTO;
import com.fooddelivery.customer.dto.SponsoredListingDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "bidding-engine", fallback = AdvertisementClientFallback.class)
public interface AdvertisementClient {

    @PostMapping("/api/v1/ads/serve")
    List<SponsoredListingDTO> fetchAds(@RequestBody AdRequestDTO bidRequest);
}
