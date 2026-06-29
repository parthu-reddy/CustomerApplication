package com.fooddelivery.order.controller;

import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.filter.RequestCachingFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.UUID;
import com.fooddelivery.order.service.WebhookProcessingService;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookProcessingService webhookProcessingService;

    @Value("${vyapar.webhook.secret:test_secret}")
    private String webhookSecret;

    @PostMapping("/vyapar")
    public ResponseEntity<String> handleVyaparWebhook(HttpServletRequest request) {
        try {
            // Get raw payload from cached wrapper
            if (!(request instanceof RequestCachingFilter.CachedBodyHttpServletRequest)) {
                log.error("Request is not cached. Missing RequestCachingFilter.");
                return ResponseEntity.internalServerError().build();
            }

            byte[] rawPayload = ((RequestCachingFilter.CachedBodyHttpServletRequest) request).getCachedBody();
            String signatureHeader = request.getHeader("X-Vyapar-Signature");
            
            if (signatureHeader == null || !isValidSignature(rawPayload, signatureHeader)) {
                log.warn("Invalid webhook signature");
                return ResponseEntity.status(401).body("Invalid signature");
            }
            
            String payloadStr = new String(rawPayload, StandardCharsets.UTF_8);
            log.info("Received valid webhook payload: {}", payloadStr);
            
            webhookProcessingService.processAndStoreWebhook("VYAPAR", payloadStr);

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private boolean isValidSignature(byte[] payload, String expectedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] generatedMac = mac.doFinal(payload);
            String calculatedSignature = Base64.getEncoder().encodeToString(generatedMac);
            
            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(calculatedSignature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error calculating HMAC", e);
            return false;
        }
    }
}
