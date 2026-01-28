package com.quickcommerce.search.controller;

import com.quickcommerce.search.entity.SearchSynonym;
import com.quickcommerce.search.service.SearchConfigurationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
public class SearchAdminController {

    private final SearchConfigurationService configurationService;

    /**
     * Create or Update a synonym group
     */
    @PostMapping("/synonyms")
    public Mono<ResponseEntity<SearchSynonym>> upsertSynonym(@RequestBody SynonymRequest request) {
        return Mono.fromCallable(() -> {
            SearchSynonym saved = configurationService.saveSynonym(request.getTerm(), request.getSynonyms());
            return ResponseEntity.ok(saved);
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

    @Data
    public static class SynonymRequest {
        private String term;
        private List<String> synonyms;
    }
}
