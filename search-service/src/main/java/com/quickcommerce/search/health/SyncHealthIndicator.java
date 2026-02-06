package com.quickcommerce.search.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Health indicator for tracking sync status
 * Provides health check endpoint for monitoring sync operations
 */
@Component
@Slf4j
public class SyncHealthIndicator implements ReactiveHealthIndicator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private final AtomicReference<SyncStatus> status = new AtomicReference<>(
        new SyncStatus(SyncState.NOT_STARTED, "Sync not yet initiated", null, 0)
    );

    @Override
    public Mono<Health> health() {
        SyncStatus currentStatus = status.get();
        
        Health.Builder builder = currentStatus.state == SyncState.HEALTHY
            ? Health.up()
            : currentStatus.state == SyncState.FAILED
                ? Health.down()
                : Health.unknown();

        return Mono.just(builder
            .withDetail("state", currentStatus.state)
            .withDetail("message", currentStatus.message)
            .withDetail("lastSync", currentStatus.lastSyncTime != null 
                ? currentStatus.lastSyncTime.format(FORMATTER) 
                : "Never")
            .withDetail("itemsSynced", currentStatus.itemsSynced)
            .build());
    }

    public void updateStatus(SyncState state, String message, int itemsSynced) {
        status.set(new SyncStatus(state, message, LocalDateTime.now(), itemsSynced));
        log.info("Sync status updated: state={}, message={}, items={}", state, message, itemsSynced);
    }

    public SyncStatus getCurrentStatus() {
        return status.get();
    }

    public enum SyncState {
        NOT_STARTED,
        IN_PROGRESS,
        HEALTHY,
        FAILED,
        DEGRADED
    }

    public record SyncStatus(
        SyncState state,
        String message,
        LocalDateTime lastSyncTime,
        int itemsSynced
    ) {}
}
