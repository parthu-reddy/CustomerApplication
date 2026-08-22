package com.fooddelivery;

import com.fooddelivery.common.outbox.config.EnableOutbox;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(
    scanBasePackages = {"com.fooddelivery", "com.fooddelivery.common"}
)
@org.springframework.boot.autoconfigure.domain.EntityScan(basePackages = {"com.fooddelivery", "com.fooddelivery.common.entity"})
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = {"com.fooddelivery", "com.fooddelivery.common.repository"})
@EnableScheduling
@EnableOutbox
@EnableFeignClients
@EnableCaching
public class FoodDeliveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodDeliveryApplication.class, args);
    }
}
