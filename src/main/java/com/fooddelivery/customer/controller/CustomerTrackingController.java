package com.fooddelivery.customer.controller;

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
@PreAuthorize("hasRole(\'CUSTOMER\') and @customerSecurityHelper.isOrderOwner(#orderId, authentication.principal)")
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class CustomerTrackingController {
    

    private final StringRedisTemplate redisTemplate;
    private final com.fooddelivery.order.repository.IOrderRepository orderRepository;
    private final ObjectProvider<RedisMessageListenerContainer> redisMessageListenerContainerProvider;
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(4);

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter trackOrder(@PathVariable UUID orderId) {
        SseEmitter emitter = new SseEmitter(600000L); // 10 minutes timeout
        String trackingChannel = "tracking:order:" + orderId;
        MessageListener listener = (message, pattern) -> {
            try {
                log.info("Consumed location-update event from Redis for order: {}", orderId);
                String body = new String(message.getBody());
                emitter.send(SseEmitter.event().name("location-update").data(body));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        };
        org.springframework.data.redis.listener.ChannelTopic topic = new org.springframework.data.redis.listener.ChannelTopic(trackingChannel);
        redisMessageListenerContainerProvider.ifAvailable(container -> container.addMessageListener(listener, topic));
        java.util.concurrent.ScheduledFuture<?> heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                log.warn("Failed to send heartbeat ping for order {}, terminating connection", orderId);
                emitter.completeWithError(e);
            }
        }, 15, 15, java.util.concurrent.TimeUnit.SECONDS);
        // Cleanup: remove the listener when the SSE ends
        Runnable cleanup = () -> {
            try {
                heartbeatTask.cancel(false);
                redisMessageListenerContainerProvider.ifAvailable(container -> container.removeMessageListener(listener, topic));
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
            emitter.send(SseEmitter.event().name("connected").data("{\"type\":\"connected\",\"message\":\"Tracking started\"}"));
        } catch (IOException e) {
            cleanup.run();
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @jakarta.annotation.PreDestroy
    public void onDestroy() {
        scheduler.shutdown();
    }

    
}
