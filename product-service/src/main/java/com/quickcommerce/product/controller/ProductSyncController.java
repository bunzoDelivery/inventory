package com.quickcommerce.product.controller;

import com.quickcommerce.product.dto.BulkSyncRequest;
import com.quickcommerce.product.dto.BulkSyncResponse;
import com.quickcommerce.product.service.ProductSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for bulk product and inventory synchronization
 * Provides store-centric bulk upsert operations
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product Sync", description = "Bulk product and inventory synchronization APIs")
public class ProductSyncController {
    
    private final ProductSyncService syncService;
    
    /**
     * Bulk sync products and inventory for a store
     * Creates products if they don't exist, updates if they do
     * Upserts inventory for the specified store
     * 
     * @param request Store-centric sync request with products and inventory data
     * @return Aggregated response with per-item results
     */
    @PostMapping("/sync")
    @Operation(
        summary = "Bulk sync products and inventory",
        description = """
            Upserts multiple products with inventory in a single call for a specific store.
            - Creates new products if SKU doesn't exist
            - Updates existing products if SKU exists
            - Creates or updates inventory for the specified store
            - Processes up to 500 items per request
            - Returns partial success (some items may succeed while others fail)
            - Invalidates caches automatically
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Sync completed successfully (may have partial failures - check individual item results)"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (validation errors or store not found)"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public Mono<ResponseEntity<BulkSyncResponse>> syncProductsAndInventory(
            @Valid @RequestBody BulkSyncRequest request) {
        
        log.info("Received bulk sync request for store {} with {} items", 
            request.getStoreId(), request.getItems().size());
        
        return syncService.syncProductsAndInventory(request)
            .map(ResponseEntity::ok)
            .doOnSuccess(response -> {
                BulkSyncResponse body = response.getBody();
                if (body != null) {
                    log.info("Bulk sync completed for store {}: {} success, {} failed, {}ms", 
                        request.getStoreId(),
                        body.getSuccessCount(),
                        body.getFailureCount(),
                        body.getProcessingTimeMs());
                }
            })
            .onErrorResume(IllegalArgumentException.class, error -> {
                log.warn("Bulk sync validation error: {}", error.getMessage());
                return Mono.just(ResponseEntity.badRequest().build());
            });
    }
}
