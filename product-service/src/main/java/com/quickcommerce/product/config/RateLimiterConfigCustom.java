package com.quickcommerce.product.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for Resilience4j Rate Limiter
 * Prevents API abuse and ensures fair resource usage
 */
@Configuration
public class RateLimiterConfigCustom {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(60))
                .timeoutDuration(Duration.ZERO)
                .build();

        return RateLimiterRegistry.of(config);
    }

    @Bean
    public RateLimiter inventoryApiRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("inventory-api");
    }
}
