package com.fooddelivery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

@Configuration
@EntityScan(basePackages = {"com.fooddelivery", "com.fooddelivery.common"})
@EnableJpaRepositories(basePackages = {"com.fooddelivery", "com.fooddelivery.common"})
@EnableScheduling
@EnableFeignClients(basePackages = {"com.fooddelivery", "com.fooddelivery.common"})
@EnableCaching
public class CustomerAppConfig {
}
