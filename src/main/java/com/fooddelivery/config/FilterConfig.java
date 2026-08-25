package com.fooddelivery.config;

import com.fooddelivery.common.filter.IdempotencyFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.beans.factory.ObjectProvider;

@Configuration
@lombok.RequiredArgsConstructor
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(ObjectProvider<IdempotencyFilter> filterProvider) {
        IdempotencyFilter filter = filterProvider.getIfAvailable();
        if (filter == null) {
            FilterRegistrationBean<IdempotencyFilter> empty = new FilterRegistrationBean<>();
            empty.setEnabled(false);
            return empty;
        }
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(true);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
