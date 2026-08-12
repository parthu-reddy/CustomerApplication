package com.fooddelivery.customer.client;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.List;

@Component("customerAdvertisementClientFallback")
public class AdvertisementClientFallback implements AdvertisementClient {

    @Override
    public Object fetchAds(Map<String, Object> bidRequest) {
        // Fallback returns empty list so that customer app doesn't break
        return java.util.Collections.emptyList();
    }
}
