package com.quickcommerce.search.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Metrics collector for search operations using Micrometer
 */
@Slf4j
@Component
public class SearchMetrics {

    private final Counter searchRequestCounter;
    private final Counter searchErrorCounter;
    private final Counter noResultsCounter;
    private final Timer searchDurationTimer;
    private final MeterRegistry meterRegistry;

    public SearchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Counter for total search requests
        this.searchRequestCounter = Counter.builder("search.requests.total")
            .description("Total number of search requests")
            .register(meterRegistry);

        // Counter for search errors
        this.searchErrorCounter = Counter.builder("search.errors.total")
            .description("Total number of search errors")
            .register(meterRegistry);

        // Counter for searches with no results
        this.noResultsCounter = Counter.builder("search.no_results.total")
            .description("Total number of searches with no results")
            .register(meterRegistry);

        // Timer for search duration
        this.searchDurationTimer = Timer.builder("search.duration")
            .description("Search request duration")
            .register(meterRegistry);
    }

    /**
     * Increment search request counter
     */
    public void incrementSearchRequests() {
        searchRequestCounter.increment();
    }

    /**
     * Increment search error counter with error type tag
     */
    public void incrementSearchErrors(String errorType) {
        Counter.builder("search.errors.total")
            .tag("error_type", errorType)
            .description("Search errors by type")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Increment no results counter
     */
    public void incrementNoResults() {
        noResultsCounter.increment();
    }

    /**
     * Record search duration
     */
    public void recordSearchDuration(long durationMs) {
        searchDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Record search result count
     */
    public void recordSearchResults(int count) {
        meterRegistry.gauge("search.results.count", count);
    }

    /**
     * Record search with store ID tag
     */
    public void recordSearchByStore(Long storeId, int resultCount) {
        Counter.builder("search.by_store.total")
            .tag("store_id", String.valueOf(storeId))
            .description("Search requests by store")
            .register(meterRegistry)
            .increment();
    }
}
