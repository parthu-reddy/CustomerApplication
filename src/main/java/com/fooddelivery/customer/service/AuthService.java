package com.fooddelivery.customer.service;

import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.common.service.NotificationRouterService;
import com.fooddelivery.customer.entity.Customer;
import com.fooddelivery.customer.repository.ICustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final ICustomerRepository customerRepository;
    private final NotificationRouterService notificationRouterService;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public void initiateLogin(String phoneNumber) {
        String otp = String.format("%06d", secureRandom.nextInt(999999));
        
        // Save to Redis with 5 minute expiry
        redisTemplate.opsForValue().set("OTP:" + phoneNumber, otp, Duration.ofMinutes(5));

        // Create or get customer for ID mapping (Optional: depending on flow)
        Optional<Customer> customerOpt = customerRepository.findByPhoneNumber(phoneNumber);
        UUID customerId = customerOpt.map(Customer::getId).orElse(null);

        // Dispatch Notification
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .userId(customerId)
                .explicitRecipient(phoneNumber)
                .channel(com.fooddelivery.common.enums.ChannelType.SMS)
                .build();
                
        notificationRouterService.routeNotification(event);
        log.info("Initiated login for {}, OTP generated.", phoneNumber);
    }

    public String verifyOtp(String phoneNumber, String otp) {
        String cachedOtp = redisTemplate.opsForValue().get("OTP:" + phoneNumber);
        if (cachedOtp != null && cachedOtp.equals(otp)) {
            // Success, delete OTP
            redisTemplate.delete("OTP:" + phoneNumber);
            
            Customer customer = customerRepository.findByPhoneNumber(phoneNumber)
                    .orElseGet(() -> customerRepository.save(Customer.builder().phoneNumber(phoneNumber).build()));
            
            // In a real app, generate JWT here. Returning dummy token for now.
            return "dummy-jwt-token-for-" + customer.getId();
        }
        throw new IllegalArgumentException("Invalid or expired OTP");
    }
}
