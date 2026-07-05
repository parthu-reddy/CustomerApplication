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
        
        if (phone != null && !phone.isEmpty()) {
            Customer customer = customerRepository.findByPhoneNumber(phone)
                    .orElseGet(() -> {
                        log.info("Creating new customer seamlessly for phone {} based on IdentityService JWT", phone);
                        return customerRepository.save(Customer.builder()
                                .phoneNumber(phone)
                                .build());
                    });
            
            // Allow downstream controllers to access the local Customer UUID
            request.setAttribute("CUSTOMER_ID", customer.getId().toString());
        }

        filterChain.doFilter(request, response);
    }
}
