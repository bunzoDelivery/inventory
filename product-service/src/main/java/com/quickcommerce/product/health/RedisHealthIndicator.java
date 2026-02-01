package com.quickcommerce.product.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Custom health indicator for Redis connectivity and performance
 */
@Component("redis")
@RequiredArgsConstructor
@Slf4j
public class RedisHealthIndicator implements ReactiveHealthIndicator {

    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    
    @Override
    public Mono<Health> health() {
        long startTime = System.currentTimeMillis();
        
        return reactiveRedisTemplate.execute(connection -> connection.ping())
                .next()
                .map(response -> {
                    long responseTime = System.currentTimeMillis() - startTime;
                    
                    Health.Builder builder = Health.up()
                            .withDetail("cache", "Redis")
                            .withDetail("ping_response", response)
                            .withDetail("response_time_ms", responseTime);
                    
                    // Warn if Redis is slow
                    if (responseTime > 100) {
                        builder.withDetail("status", "slow_response");
                    }
                    
                    return builder.build();
                })
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> {
                    log.error("Redis health check failed", e);
                    return Mono.just(Health.down()
                            .withDetail("cache", "Redis")
                            .withDetail("error", e.getMessage())
                            .withException(e)
                            .build());
                });
    }
}
