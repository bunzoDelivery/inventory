package com.quickcommerce.product.health;

import com.quickcommerce.product.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Custom health indicator for MySQL database connectivity and performance
 */
@Component("database")
@RequiredArgsConstructor
@Slf4j
public class DatabaseHealthIndicator implements ReactiveHealthIndicator {

    private final InventoryItemRepository inventoryRepository;
    
    @Override
    public Mono<Health> health() {
        long startTime = System.currentTimeMillis();
        
        return inventoryRepository.count()
                .map(count -> {
                    long responseTime = System.currentTimeMillis() - startTime;
                    
                    Health.Builder builder = Health.up()
                            .withDetail("database", "MySQL")
                            .withDetail("inventory_items_count", count)
                            .withDetail("response_time_ms", responseTime);
                    
                    // Warn if query is slow
                    if (responseTime > 500) {
                        builder.withDetail("status", "slow_response");
                    }
                    
                    return builder.build();
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.error("Database health check failed", e);
                    return Mono.just(Health.down()
                            .withDetail("database", "MySQL")
                            .withDetail("error", e.getMessage())
                            .withException(e)
                            .build());
                });
    }
}
