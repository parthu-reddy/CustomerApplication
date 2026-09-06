package com.fooddelivery.customer.client;

import com.fooddelivery.customer.dto.AdRequestDTO;
import com.fooddelivery.customer.dto.SponsoredListingDTO;
import org.springframework.stereotype.Component;
import java.util.List;

@Component("customerAdvertisementClientFallback")
@lombok.RequiredArgsConstructor
public class AdvertisementClientFallback implements AdvertisementClient {

    @Override
    public List<SponsoredListingDTO> fetchAds(AdRequestDTO bidRequest) {
        // Fallback returns empty list so that customer app doesn't break
        return java.util.Collections.emptyList();
    }
}
