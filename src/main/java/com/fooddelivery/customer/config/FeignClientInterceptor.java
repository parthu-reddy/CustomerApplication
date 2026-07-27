package com.fooddelivery.customer.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignClientInterceptor implements RequestInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FeignClientInterceptor.class);

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // Forward trusted internal headers instead of JWT, as microservices trust these directly
            String userId = request.getHeader("X-User-Id");
            if (userId != null) template.header("X-User-Id", userId);
            
            String userRoles = request.getHeader("X-User-Roles");
            if (userRoles != null) template.header("X-User-Roles", userRoles);
            
            String userPhone = request.getHeader("X-User-Phone");
            if (userPhone != null) template.header("X-User-Phone", userPhone);
            
            // Still forward Authorization header just in case some legacy downstream needs it
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null) {
                template.header(HttpHeaders.AUTHORIZATION, authHeader);
            }
            log.info("FeignClientInterceptor - Forwarding from Attributes -> X-User-Id={}, X-User-Roles={}", userId, userRoles);
        } else {
            // Background Job / Async Execution
            template.header("X-User-Id", "system-internal");
            template.header("X-User-Roles", "INTERNAL_SERVICE,CUSTOMER,ADMIN");
            log.info("FeignClientInterceptor - No attributes found. Forwarding as Background Job -> X-User-Id=system-internal, X-User-Roles=INTERNAL_SERVICE,CUSTOMER,ADMIN");
        }
    }
}
