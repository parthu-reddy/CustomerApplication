package com.fooddelivery.customer.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/live-tracking")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CUSTOMER') and @customerSecurityHelper.isOrderOwner(#orderId, authentication.principal)")
public class CustomerTrackingController {

    private final StringRedisTemplate redisTemplate;
    private final com.fooddelivery.order.repository.IOrderRepository orderRepository;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter trackOrder(@PathVariable UUID orderId) {

        SseEmitter emitter = new SseEmitter(600000L); // 10 minutes timeout
        
        String trackingChannel = "tracking:order:" + orderId;
        
        // Acquire a dedicated Redis connection for Pub/Sub
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        
        MessageListener listener = new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                try {
                    String body = new String(message.getBody());
                    emitter.send(SseEmitter.event().name("location-update").data(body));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }
        };
        
        // Run subscribe in a separate thread because connection.subscribe() is a BLOCKING call
        // If run on the main request thread, it prevents the controller from returning,
        // which causes the Open-Session-In-View filter to hold the JDBC connection forever!
        new Thread(() -> {
            connection.subscribe(listener, trackingChannel.getBytes());
        }).start();
        
        // Cleanup: unsubscribe and close the Redis connection when the SSE ends
        Runnable cleanup = () -> {
            try {
                if (connection.isSubscribed()) {
                    connection.getSubscription().unsubscribe();
                }
                connection.close();
                log.info("Cleaned up Redis subscription for order: {}", orderId);
            } catch (Exception e) {
                log.warn("Error during Redis subscription cleanup for order: {}", orderId, e);
            }
        };
        
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            log.info("SSE timeout for order: {}", orderId);
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.warn("SSE error for order: {}", orderId, ex);
            cleanup.run();
        });
        
        // Send initial connection event
        try {
            emitter.send(SseEmitter.event().name("connected").data("Tracking started for order: " + orderId));
        } catch (IOException e) {
            cleanup.run();
            emitter.completeWithError(e);
        }
        
        return emitter;
    }
}
