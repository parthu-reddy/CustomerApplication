package com.fooddelivery.customer.filter;

import com.fooddelivery.customer.entity.Customer;
import com.fooddelivery.customer.repository.ICustomerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@lombok.extern.slf4j.Slf4j
public class CustomerIdentityFilter extends OncePerRequestFilter {
    @java.lang.SuppressWarnings("all")

    private final ICustomerRepository customerRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String phone = request.getHeader(com.fooddelivery.common.constants.HeaderConstants.HEADER_USER_PHONE);
        String userId = request.getHeader(com.fooddelivery.common.constants.HeaderConstants.HEADER_USER_ID);
        log.info("CustomerIdentityFilter executed for URI: {}, phone: {}, userId: {}", request.getRequestURI(), phone, userId);
        if (phone != null && !phone.isEmpty() && userId != null && !userId.isEmpty()) {
            java.util.UUID jwtUserId = java.util.UUID.fromString(userId);
            String roles = request.getHeader(com.fooddelivery.common.constants.HeaderConstants.HEADER_USER_ROLES);
            // Only synchronize/create customer records if the user actually has the CUSTOMER role
            if (roles != null && roles.contains("CUSTOMER")) {
                // First, try to find by the JWT user ID (the canonical identity)
                Customer customer = customerRepository.findById(jwtUserId).orElseGet(() -> {
                    try {
                        log.info("Creating new customer for userId {} phone {} based on IdentityService JWT", userId, phone);
                        return customerRepository.save(Customer.builder().id(jwtUserId).phoneNumber(phone).build());
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        log.info("Customer already created concurrently for phone {}, fetching existing record", phone);
                        return customerRepository.findByPhoneNumber(phone).orElseThrow();
                    }
                });
                // Allow downstream controllers to access the local Customer UUID
                request.setAttribute("CUSTOMER_ID", customer.getId().toString());
            }
        }
        filterChain.doFilter(request, response);
    }

    @java.lang.SuppressWarnings("all")
    public CustomerIdentityFilter(final ICustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }
}
