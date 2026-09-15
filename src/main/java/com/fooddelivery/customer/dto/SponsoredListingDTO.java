package com.fooddelivery.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SponsoredListingDTO {
    private String adId;
    private String campaignId;
    private String impressionUrl;
    private String clickUrl;
    private String adm;
    private String creativeFormat;
    private String advertiserId;
}
