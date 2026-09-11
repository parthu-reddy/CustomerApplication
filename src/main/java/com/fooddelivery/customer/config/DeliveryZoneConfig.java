package com.fooddelivery.customer.config;

import com.fooddelivery.common.constants.AppConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * The two delivery radii, and the city the fleet is searched in.
 *
 * <p>Two radii, not one: {@code maxRadiusKm} is how far a customer may be from a restaurant, and
 * {@code fleetSearchRadiusKm} is how far from the restaurant a rider may be. They default to the
 * same value and are separate because they are separate questions.
 *
 * <p>Before this, the quote check used a hardcoded {@code 7.0} while the fleet check used
 * {@code AppConstants.MAX_DELIVERY_RADIUS_KM} (5.0) and the README said 5 km. A restaurant 6 km
 * away therefore quoted successfully and then failed at checkout with "All our delivery partners
 * are currently busy", which was not what had happened. The city was the literal {@code "BLR"}.
 */
@Configuration
@ConfigurationProperties(prefix = "delivery.zone")
@RefreshScope
@lombok.Data
public class DeliveryZoneConfig {
    private double maxRadiusKm = AppConstants.MAX_DELIVERY_RADIUS_KM;
    private double fleetSearchRadiusKm = AppConstants.FLEET_SEARCH_RADIUS_KM;
    private String defaultCity = "BLR";
}
