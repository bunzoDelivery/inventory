package com.quickcommerce.search.controller;

import com.quickcommerce.search.provider.MeilisearchProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin controller for index management operations
 * Only available in dev/non-production environments
 */
@Slf4j
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
public class AdminController {

    private final MeilisearchProvider meilisearchProvider;

    /**
     * Create products index with settings
     */
    @PostMapping("/index/create")
    public ResponseEntity<Map<String, String>> createIndex() {
        log.info("Admin: Creating search index");
        
        try {
            meilisearchProvider.createIndex();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Index created successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to create index", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Update index settings (synonyms, searchable attributes, etc.)
     */
    @PutMapping("/index/settings")
    public ResponseEntity<Map<String, String>> updateSettings() {
        log.info("Admin: Updating index settings");
        
        try {
            meilisearchProvider.updateIndexSettings();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Settings updated successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to update settings", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get index statistics
     */
    @GetMapping("/index/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.info("Admin: Getting index stats");
        
        try {
            Map<String, Object> stats = meilisearchProvider.getIndexStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get stats", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Delete index (dev only)
     */
    @DeleteMapping("/index")
    @Profile("dev")
    public ResponseEntity<Map<String, String>> deleteIndex() {
        log.warn("Admin: Deleting search index");
        
        try {
            meilisearchProvider.deleteIndex();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Index deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to delete index", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
}
