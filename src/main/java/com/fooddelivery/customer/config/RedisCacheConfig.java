package com.fooddelivery.customer.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.redis.enabled", matchIfMissing = true)
@EnableCaching
public class RedisCacheConfig {

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // CustomerApp default TTL
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper())))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> initialCacheConfigs = new HashMap<>();

        // Create a non-locking cache writer wrapped in a dynamic proxy to inject randomized TTL jitter
        // This prevents cache stampedes (thundering herd) when bulk cached keys expire simultaneously
        RedisCacheWriter defaultWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        RedisCacheWriter jitterWriter = (RedisCacheWriter) Proxy.newProxyInstance(
                RedisCacheWriter.class.getClassLoader(),
                new Class<?>[]{ RedisCacheWriter.class },
                (proxy, method, args) -> {
                    if (("put".equals(method.getName()) || "putIfAbsent".equals(method.getName())) && args != null && args.length >= 4) {
                        if (args[3] instanceof Duration) {
                            Duration ttl = (Duration) args[3];
                            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                                long seconds = ttl.getSeconds();
                                // Add random jitter between 0 and 15% of base TTL (capped at 180 seconds max)
                                long maxJitter = Math.max(1, Math.min(seconds / 6, 180));
                                long jitterSeconds = ThreadLocalRandom.current().nextLong(0, maxJitter);
                                args[3] = ttl.plusSeconds(jitterSeconds);
                            }
                        }
                    }
                    return method.invoke(defaultWriter, args);
                }
        );

        return RedisCacheManager.builder(jitterWriter)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(initialCacheConfigs)
                .build();
    }
}
