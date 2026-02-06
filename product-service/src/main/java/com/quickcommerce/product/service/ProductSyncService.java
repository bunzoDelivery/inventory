package com.quickcommerce.product.service;

import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.catalog.repository.ProductRepository;
import com.quickcommerce.product.config.InventoryProperties;
import com.quickcommerce.product.domain.InventoryItem;
import com.quickcommerce.product.dto.BulkSyncRequest;
import com.quickcommerce.product.dto.BulkSyncResponse;
import com.quickcommerce.product.dto.BulkSyncResponse.ItemResult;
import com.quickcommerce.product.dto.ProductSyncItem;
import com.quickcommerce.product.repository.InventoryItemRepository;
import com.quickcommerce.product.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for bulk synchronization of products and inventory
 * Handles create-if-not-exists and update-if-exists logic with batch processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSyncService {
    
    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryRepository;
    private final StoreRepository storeRepository;
    private final TransactionalOperator transactionalOperator;
    private final InventoryProperties properties;
    
    /**
     * Bulk sync products and inventory for a specific store
     * Validates store exists before processing
     */
    public Mono<BulkSyncResponse> syncProductsAndInventory(BulkSyncRequest request) {
        long startTime = System.currentTimeMillis();
        
        // Validate store exists first - fail fast if not found
        return storeRepository.findById(request.getStoreId())
            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                "Store not found: " + request.getStoreId())))
            .flatMap(store -> {
                log.info("Processing sync for store: {} ({}) with {} items", 
                    store.getId(), store.getName(), request.getItems().size());
                
                int batchSize = properties.getSync().getBatchSize();
                
                return Flux.fromIterable(request.getItems())
                    .buffer(batchSize)  // Split into batches of 50
                    .flatMap(batch -> processBatch(batch, request.getStoreId()))
                    .collectList()
                    .map(results -> buildResponse(results, startTime));
            });
    }
    
    /**
     * Process a batch of items
     */
    private Flux<ItemResult> processBatch(List<ProductSyncItem> batch, Long storeId) {
        log.info("Processing batch of {} items for store {}", batch.size(), storeId);
        
        return Flux.fromIterable(batch)
            .flatMap(item -> upsertProductAndInventory(item, storeId)
                .onErrorResume(error -> {
                    log.error("Failed to sync SKU: {} for store: {}", 
                        item.getSku(), storeId, error);
                    return Mono.just(ItemResult.builder()
                        .sku(item.getSku())
                        .status("FAILED")
                        .errorMessage(error.getMessage())
                        .build());
                }));
    }
    
    /**
     * Upsert single product and inventory (transactional)
     */
    private Mono<ItemResult> upsertProductAndInventory(ProductSyncItem item, Long storeId) {
        return productRepository.findBySku(item.getSku())
            .flatMap(existingProduct -> updateExistingProduct(item, existingProduct, storeId))
            .switchIfEmpty(Mono.defer(() -> createNewProduct(item, storeId)))
            .as(transactionalOperator::transactional);
    }
    
    /**
     * Create new product and inventory
     */
    private Mono<ItemResult> createNewProduct(ProductSyncItem item, Long storeId) {
        Product product = mapToProduct(item);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        
        return productRepository.save(product)
            .flatMap(savedProduct -> {
                InventoryItem inventory = mapToInventory(item, savedProduct.getId(), storeId);
                return inventoryRepository.save(inventory)
                    .flatMap(savedInventory -> 
                        evictCaches(item.getSku())
                            .thenReturn(ItemResult.builder()
                                .sku(item.getSku())
                                .status("SUCCESS")
                                .operation("CREATED")
                                .productId(savedProduct.getId())
                                .inventoryId(savedInventory.getId())
                                .build())
                    );
            });
    }
    
    /**
     * Update existing product and upsert inventory
     */
    private Mono<ItemResult> updateExistingProduct(ProductSyncItem item, 
                                                    Product existingProduct, 
                                                    Long storeId) {
        updateProductFields(existingProduct, item);
        existingProduct.setUpdatedAt(LocalDateTime.now());
        
        return productRepository.save(existingProduct)
            .flatMap(savedProduct -> 
                inventoryRepository.findByStoreIdAndSku(storeId, item.getSku())
                    .flatMap(existingInventory -> updateInventory(item, existingInventory))
                    .switchIfEmpty(Mono.defer(() -> {
                        InventoryItem newInventory = mapToInventory(item, savedProduct.getId(), storeId);
                        return inventoryRepository.save(newInventory);
                    }))
                    .flatMap(inventory -> 
                        evictCaches(item.getSku())
                            .thenReturn(ItemResult.builder()
                                .sku(item.getSku())
                                .status("SUCCESS")
                                .operation("UPDATED")
                                .productId(savedProduct.getId())
                                .inventoryId(inventory.getId())
                                .build())
                    )
            );
    }
    
    /**
     * Update inventory item
     */
    private Mono<InventoryItem> updateInventory(ProductSyncItem item, InventoryItem existing) {
        existing.setCurrentStock(item.getCurrentStock());
        existing.setSafetyStock(item.getSafetyStock());
        existing.setMaxStock(item.getMaxStock());
        existing.setUnitCost(item.getUnitCost());
        existing.setLastUpdated(LocalDateTime.now());
        return inventoryRepository.save(existing);
    }
    
    /**
     * Map ProductSyncItem to Product domain entity
     */
    private Product mapToProduct(ProductSyncItem item) {
        Product product = new Product();
        product.setSku(item.getSku());
        product.setName(item.getName());
        product.setDescription(item.getDescription());
        product.setShortDescription(item.getShortDescription());
        product.setCategoryId(item.getCategoryId());
        product.setBrand(item.getBrand());
        product.setBasePrice(item.getBasePrice());
        product.setUnitOfMeasure(item.getUnitOfMeasure());
        product.setPackageSize(item.getPackageSize());
        product.setImages(item.getImages());
        product.setTags(item.getTags());
        product.setIsActive(item.getIsActive());
        product.setIsAvailable(item.getIsAvailable());
        // Auto-generate slug if not provided
        product.setSlug(item.getSlug() != null ? item.getSlug() : generateSlug(item.getName(), item.getSku()));
        product.setNutritionalInfo(item.getNutritionalInfo());
        product.setWeightGrams(item.getWeightGrams());
        product.setBarcode(item.getBarcode());
        return product;
    }
    
    /**
     * Generate URL-friendly slug from product name and SKU
     */
    private String generateSlug(String name, String sku) {
        // Convert to lowercase, replace spaces and special chars with hyphens
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
        
        // Append SKU to ensure uniqueness
        return slug + "-" + sku.toLowerCase();
    }
    
    /**
     * Map ProductSyncItem to InventoryItem domain entity
     */
    private InventoryItem mapToInventory(ProductSyncItem item, Long productId, Long storeId) {
        return InventoryItem.builder()
            .sku(item.getSku())
            .productId(productId)
            .storeId(storeId)  // Use storeId from request level
            .currentStock(item.getCurrentStock())
            .reservedStock(0)
            .safetyStock(item.getSafetyStock())
            .maxStock(item.getMaxStock())
            .unitCost(item.getUnitCost())
            // Do not set version for new entities - let R2DBC handle optimistic locking
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    /**
     * Update product fields from sync item
     */
    private void updateProductFields(Product product, ProductSyncItem item) {
        product.setName(item.getName());
        product.setDescription(item.getDescription());
        product.setShortDescription(item.getShortDescription());
        product.setCategoryId(item.getCategoryId());
        product.setBrand(item.getBrand());
        product.setBasePrice(item.getBasePrice());
        product.setUnitOfMeasure(item.getUnitOfMeasure());
        product.setPackageSize(item.getPackageSize());
        product.setImages(item.getImages());
        product.setTags(item.getTags());
        product.setIsActive(item.getIsActive());
        product.setIsAvailable(item.getIsAvailable());
        // Auto-generate slug if not provided
        product.setSlug(item.getSlug() != null ? item.getSlug() : generateSlug(item.getName(), item.getSku()));
        product.setNutritionalInfo(item.getNutritionalInfo());
        product.setWeightGrams(item.getWeightGrams());
        product.setBarcode(item.getBarcode());
    }
    
    /**
     * Evict caches for product and inventory
     */
    @CacheEvict(value = {"products", "inventory"}, key = "#sku")
    private Mono<Void> evictCaches(String sku) {
        log.debug("Evicting caches for SKU: {}", sku);
        return Mono.empty();
    }
    
    /**
     * Build aggregated response
     */
    private BulkSyncResponse buildResponse(List<ItemResult> results, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        int successCount = 0;
        int failureCount = 0;
        
        for (ItemResult result : results) {
            if ("SUCCESS".equals(result.getStatus())) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        
        return BulkSyncResponse.builder()
            .totalItems(results.size())
            .successCount(successCount)
            .failureCount(failureCount)
            .processingTimeMs(processingTime)
            .results(results)
            .build();
    }
}
