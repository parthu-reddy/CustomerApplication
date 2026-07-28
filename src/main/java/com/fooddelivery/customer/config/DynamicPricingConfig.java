package com.fooddelivery.customer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "pricing.delivery")
@RefreshScope
@Data
public class DynamicPricingConfig {
    private BigDecimal basePrice = new BigDecimal("15.00");
    private BigDecimal perKmRate = new BigDecimal("8.00");
    private BigDecimal restMaxContributionPercent = new BigDecimal("0.15");
    private BigDecimal fixedPlatformFee = new BigDecimal("5.00");
    private BigDecimal platformExcessCutPercent = new BigDecimal("0.50");
    private BigDecimal sgstPercent = new BigDecimal("0.025");
    private BigDecimal cgstPercent = new BigDecimal("0.025");
}
