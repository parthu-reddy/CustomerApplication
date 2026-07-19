package com.fooddelivery.customer.filter;

import com.fooddelivery.customer.entity.Customer;
import com.fooddelivery.customer.repository.ICustomerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerIdentityFilter extends OncePerRequestFilter {

    private final ICustomerRepository customerRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String phone = request.getHeader("X-User-Phone");
        String userId = request.getHeader("X-User-Id");
        
        log.info("CustomerIdentityFilter executed for URI: {}, phone: {}, userId: {}", request.getRequestURI(), phone, userId);
        
        if (phone != null && !phone.isEmpty() && userId != null && !userId.isEmpty()) {
            java.util.UUID jwtUserId = java.util.UUID.fromString(userId);
            
            // First, try to find by the JWT user ID (the canonical identity)
            Customer customer = customerRepository.findById(jwtUserId)
                    .orElseGet(() -> {
                        try {
                            log.info("Creating new customer for userId {} phone {} based on IdentityService JWT", userId, phone);
                            return customerRepository.save(Customer.builder()
                                    .id(jwtUserId)
                                    .phoneNumber(phone)
                                    .build());
                        } catch (org.springframework.dao.DataIntegrityViolationException e) {
                            log.info("Customer already created concurrently for userId {}, fetching existing record", userId);
                            return customerRepository.findById(jwtUserId).orElseThrow();
                        }
                    });
            
            // Allow downstream controllers to access the local Customer UUID
            request.setAttribute("CUSTOMER_ID", customer.getId().toString());
        }

        filterChain.doFilter(request, response);
    }
}
