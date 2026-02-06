package com.quickcommerce.search.controller;

import com.quickcommerce.search.provider.MeilisearchProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin controller for index management operations
 * Requires ADMIN role for all operations
 */
@Slf4j
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final MeilisearchProvider meilisearchProvider;

    /**
     * Create products index with settings
     */
    @PostMapping("/index/create")
    public Mono<ResponseEntity<Map<String, String>>> createIndex() {
        log.info("Admin: Creating search index");

        return meilisearchProvider.createIndex()
                .then(Mono.fromCallable(() -> ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Index created successfully"))))
                .onErrorResume(e -> {
                    log.error("Failed to create index", e);
                    return Mono.just(ResponseEntity.status(500).body(Map.of(
                            "status", "error",
                            "message", e.getMessage())));
                });
    }

    /**
     * Update index settings (synonyms, searchable attributes, etc.)
     */
    @PutMapping("/index/settings")
    public Mono<ResponseEntity<Map<String, String>>> updateSettings() {
        log.info("Admin: Updating index settings");

        return meilisearchProvider.updateIndexSettings()
                .then(Mono.fromCallable(() -> ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Settings updated successfully"))))
                .onErrorResume(e -> {
                    log.error("Failed to update settings", e);
                    return Mono.just(ResponseEntity.status(500).body(Map.of(
                            "status", "error",
                            "message", e.getMessage())));
                });
    }

    /**
     * Get index statistics
     */
    @GetMapping("/index/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getStats() {
        log.info("Admin: Getting index stats");

        return meilisearchProvider.getIndexStats()
                .map(stats -> ResponseEntity.ok(stats))
                .onErrorResume(e -> {
                    log.error("Failed to get stats", e);
                    return Mono.just(ResponseEntity.status(500).body(Map.of(
                            "status", "error",
                            "message", e.getMessage())));
                });
    }

    /**
     * Delete index (dev only)
     */
    @DeleteMapping("/index")
    @Profile("dev")
    public Mono<ResponseEntity<Map<String, String>>> deleteIndex() {
        log.warn("Admin: Deleting search index");

        return meilisearchProvider.deleteIndex()
                .then(Mono.fromCallable(() -> ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Index deleted successfully"))))
                .onErrorResume(e -> {
                    log.error("Failed to delete index", e);
                    return Mono.just(ResponseEntity.status(500).body(Map.of(
                            "status", "error",
                            "message", e.getMessage())));
                });
    }
}
