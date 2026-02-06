package com.quickcommerce.search.controller;

import com.quickcommerce.search.entity.SearchSynonym;
import com.quickcommerce.search.service.SearchConfigurationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Admin controller for search configuration management
 * Requires authentication and ADMIN role for all operations
 */
@Slf4j
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SearchAdminController {

    private final SearchConfigurationService configurationService;

    /**
     * Create or Update a synonym group
     */
    @PostMapping("/synonyms")
    public Mono<ResponseEntity<SearchSynonym>> upsertSynonym(
            @RequestBody SynonymRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("Upserting synonym: term={}, user={}", request.getTerm(), username);
        return configurationService.saveSynonym(request.getTerm(), request.getSynonyms(), username)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Failed to upsert synonym", e);
                    return Mono.just(ResponseEntity.status(400).build());
                });
    }

    /**
     * Trigger synchronization to Meilisearch
     */
    private final com.quickcommerce.search.service.IndexSyncService indexSyncService;

    /**
     * Trigger manual bulk sync of product data
     */
    @PostMapping("/index/sync-data")
    public Mono<ResponseEntity<Map<String, String>>> syncData() {
        // Run in background, don't wait for full completion to return response
        indexSyncService.syncAllProducts()
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe();

        return Mono.just(ResponseEntity.accepted().body(Map.of(
                "message", "Bulk data sync triggered in background")));
    }

    @PostMapping("/sync")
    public Mono<ResponseEntity<Map<String, Object>>> syncConfiguration() {
        return configurationService.publishConfiguration()
                .map(task -> ResponseEntity.ok(Map.<String, Object>of(
                        "status", "enqueued",
                        "taskUid", task.getTaskUid())))
                .onErrorResume(e -> {
                    log.error("Sync failed", e);
                    return Mono.just(ResponseEntity.status(500).body(Map.of(
                            "status", "error",
                            "message", e.getMessage())));
                });
    }

    /**
     * Get all synonyms
     */
    @GetMapping("/synonyms")
    public reactor.core.publisher.Flux<com.quickcommerce.search.entity.SearchSynonym> getAllSynonyms() {
        log.info("Getting all synonyms");
        return configurationService.getAllSynonyms();
    }

    /**
     * Delete a synonym by term
     */
    @DeleteMapping("/synonyms/{term}")
    public Mono<ResponseEntity<Map<String, String>>> deleteSynonym(
            @PathVariable String term,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("Deleting synonym: term={}, user={}", term, username);
        return configurationService.deleteSynonym(term)
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Synonym deleted successfully"))))
                .onErrorResume(e -> {
                    log.error("Failed to delete synonym", e);
                    return Mono.just(ResponseEntity.status(500).body(Map.of(
                            "status", "error",
                            "message", e.getMessage())));
                });
    }

    /**
     * Get all settings
     */
    @GetMapping("/settings")
    public reactor.core.publisher.Flux<com.quickcommerce.search.entity.SearchSetting> getAllSettings() {
        log.info("Getting all settings");
        return configurationService.getAllSettings();
    }

    /**
     * Rebuild search index
     */
    @PostMapping("/index/rebuild")
    public Mono<ResponseEntity<Map<String, String>>> rebuildIndex() {
        log.info("Triggering search index rebuild");
        indexSyncService.syncAllProducts()
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe(
                    count -> log.info("Index rebuild completed: {} products", count),
                    e -> log.error("Index rebuild failed", e)
                );

        return Mono.just(ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "message", "Index rebuild triggered in background")));
    }

    @Data
    public static class SynonymRequest {
        private String term;
        private List<String> synonyms;
    }
}
