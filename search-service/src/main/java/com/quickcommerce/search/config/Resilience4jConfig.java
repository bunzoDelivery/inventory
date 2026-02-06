package com.quickcommerce.search.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j configuration for circuit breakers and rate limiters
 */
@Configuration
public class Resilience4jConfig {

    /**
     * Circuit breaker for inventory service calls
     */
    @Bean
    public CircuitBreaker inventoryCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(10) // Number of calls to track
            .failureRateThreshold(50) // Open circuit if 50% fail
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before trying again
            .permittedNumberOfCallsInHalfOpenState(5) // Test with 5 calls in half-open state
            .slowCallDurationThreshold(Duration.ofMillis(500)) // Call is slow if > 500ms
            .slowCallRateThreshold(80) // Open if 80% of calls are slow
            .build();

        return registry.circuitBreaker("inventoryService", config);
    }

    /**
     * Circuit breaker for catalog service calls
     */
    @Bean
    public CircuitBreaker catalogCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slowCallDurationThreshold(Duration.ofMillis(800)) // Catalog can be slightly slower
            .slowCallRateThreshold(80)
            .build();

        return registry.circuitBreaker("catalogService", config);
    }

    /**
     * Rate limiter for search endpoint
     */
    @Bean
    public RateLimiter searchRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(100) // 100 requests
            .limitRefreshPeriod(Duration.ofMinutes(1)) // Per minute
            .timeoutDuration(Duration.ofMillis(100)) // Wait up to 100ms for permission
            .build();

        return registry.rateLimiter("searchEndpoint", config);
    }
}
