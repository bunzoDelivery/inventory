package com.quickcommerce.product.controller;

import com.quickcommerce.product.domain.InventoryItem;
import com.quickcommerce.product.dto.AddStockRequest;
import com.quickcommerce.product.dto.InventoryAvailabilityRequest;
import com.quickcommerce.product.dto.InventoryAvailabilityResponse;
import com.quickcommerce.product.dto.InventoryItemResponse;
import com.quickcommerce.product.dto.NearestStoreRequest;
import com.quickcommerce.product.dto.NearestStoreResponse;
import com.quickcommerce.product.dto.ReserveStockRequest;
import com.quickcommerce.product.dto.StockReservationResponse;
import com.quickcommerce.product.exception.InsufficientStockException;
import com.quickcommerce.product.exception.InventoryNotFoundException;
import com.quickcommerce.product.exception.InvalidReservationException;
import com.quickcommerce.product.exception.ReservationNotFoundException;
import com.quickcommerce.product.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST controller for inventory management operations
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

        private final InventoryService inventoryService;

        /**
         * Get inventory item by SKU
         */
        @GetMapping("/sku/{sku}")
        public Mono<ResponseEntity<InventoryItemResponse>> getInventoryBySku(@PathVariable String sku) {
                log.info("Getting inventory for SKU: {}", sku);

                return inventoryService.getInventoryBySku(sku)
                                .map(InventoryItemResponse::fromDomain)
                                .map(ResponseEntity::ok)
                                .onErrorReturn(InventoryNotFoundException.class, ResponseEntity.notFound().build())
                                .doOnNext(response -> log.debug("Retrieved inventory for SKU: {}", sku));
        }

        /**
         * Reserve stock for checkout (Bulk)
         */
        @PostMapping("/reserve")
        public Mono<ResponseEntity<List<StockReservationResponse>>> reserveStock(
                        @Valid @RequestBody ReserveStockRequest request) {
                log.info("Reserving stock for order: {}", request.getOrderId());

                return inventoryService.reserveStock(request)
                                .map(ResponseEntity::ok)
                                .onErrorResume(InsufficientStockException.class,
                                                e -> Mono.just(ResponseEntity.badRequest().build())) // Simplified error
                                                                                                     // handling
                                .onErrorResume(InventoryNotFoundException.class,
                                                e -> Mono.just(ResponseEntity.notFound().build()))
                                .doOnNext(response -> log.info("Stock reservation result (items): {}",
                                                response.getBody() != null ? response.getBody().size() : 0));
        }

        /**
         * Confirm reservation (convert to sale) - Single Item
         */
        @PostMapping("/reservations/{reservationId}/confirm")
        public Mono<ResponseEntity<Map<String, String>>> confirmReservation(@PathVariable String reservationId) {
                log.info("Confirming reservation: {}", reservationId);

                return inventoryService.confirmReservation(reservationId)
                                .then(Mono.just(ResponseEntity.ok(Map.of(
                                                "reservationId", reservationId,
                                                "status", "CONFIRMED",
                                                "message", "Reservation confirmed successfully"))))
                                .doOnSuccess(result -> log.info("Reservation confirmed successfully: {}",
                                                reservationId))
                                .doOnError(error -> log.error("Failed to confirm reservation: {}", reservationId,
                                                error));
        }

        /**
         * Confirm all reservations for an order (Bulk)
         */
        @PostMapping("/reservations/order/{orderId}/confirm")
        public Mono<ResponseEntity<Map<String, String>>> confirmOrderReservations(@PathVariable String orderId) {
                log.info("Confirming reservations for order: {}", orderId);

                return inventoryService.confirmOrderReservations(orderId)
                                .then(Mono.just(ResponseEntity.ok(Map.of(
                                                "orderId", orderId,
                                                "status", "CONFIRMED",
                                                "message", "Order reservations confirmed successfully"))))
                                .doOnSuccess(result -> log.info("Order reservations confirmed successfully: {}",
                                                orderId))
                                .doOnError(error -> log.error("Failed to confirm order reservations: {}", orderId,
                                                error));
        }

        /**
         * Cancel all reservations for an order
         */
        @PostMapping("/reservations/order/{orderId}/cancel")
        public Mono<ResponseEntity<Map<String, String>>> cancelOrderReservations(@PathVariable String orderId) {
                log.info("Cancelling reservations for order: {}", orderId);

                return inventoryService.cancelOrderReservations(orderId)
                                .then(Mono.just(ResponseEntity.ok(Map.of(
                                                "orderId", orderId,
                                                "status", "CANCELLED",
                                                "message", "Order reservations cancelled successfully"))))
                                .doOnSuccess(result -> log.info("Order reservations cancelled successfully: {}",
                                                orderId))
                                .doOnError(error -> log.error("Failed to cancel order reservations: {}", orderId,
                                                error));
        }

        /**
         * Cancel reservation
         */
        @PostMapping("/reservations/{reservationId}/cancel")
        public Mono<ResponseEntity<Map<String, String>>> cancelReservation(@PathVariable String reservationId) {
                log.info("Cancelling reservation: {}", reservationId);

                return inventoryService.cancelReservation(reservationId)
                                .then(Mono.just(ResponseEntity.ok(Map.of(
                                                "reservationId", reservationId,
                                                "status", "CANCELLED",
                                                "message", "Reservation cancelled successfully"))))
                                .doOnSuccess(result -> log.info("Reservation cancelled successfully: {}",
                                                reservationId))
                                .doOnError(error -> log.error("Failed to cancel reservation: {}", reservationId,
                                                error));
        }

        /**
         * Add stock to inventory
         */
        @PostMapping("/stock/add")
        public Mono<Void> addStock(@Valid @RequestBody AddStockRequest request) {
                log.info("Adding stock: {}", request);

                return inventoryService.addStock(request)
                                .doOnSuccess(result -> log.info("Stock added successfully for SKU: {}",
                                                request.getSku()))
                                .doOnError(error -> log.error("Failed to add stock for SKU: {}", request.getSku(),
                                                error));
        }

        /**
         * Get low stock items for a store
         */
        @GetMapping("/low-stock")
        public Flux<InventoryItemResponse> getLowStockItems(@RequestParam Long storeId) {
                log.info("Getting low stock items for store: {}", storeId);

                return inventoryService.getLowStockItems(storeId)
                                .map(InventoryItemResponse::fromDomain)
                                .doOnNext(item -> log.debug("Low stock item: {}", item.getSku()));
        }

        /**
         * Get items needing replenishment
         */
        @GetMapping("/replenishment")
        public Flux<InventoryItemResponse> getItemsNeedingReplenishment(@RequestParam Long storeId) {
                log.info("Getting items needing replenishment for store: {}", storeId);

                return inventoryService.getItemsNeedingReplenishment(storeId)
                                .map(InventoryItemResponse::fromDomain)
                                .doOnNext(item -> log.debug("Item needing replenishment: {}", item.getSku()));
        }

        /**
         * Get inventory by barcode (assuming barcode maps to SKU)
         */
        @GetMapping("/barcode/{barcode}")
        public Mono<ResponseEntity<InventoryItemResponse>> getInventoryByBarcode(@PathVariable String barcode) {
                log.info("Getting inventory by barcode: {}", barcode);

                return inventoryService.getInventoryBySku(barcode)
                                .map(InventoryItemResponse::fromDomain)
                                .map(ResponseEntity::ok)
                                .onErrorReturn(InventoryNotFoundException.class, ResponseEntity.notFound().build())
                                .doOnNext(response -> log.debug("Retrieved inventory by barcode: {}", barcode));
        }

        /**
         * Check inventory availability for multiple SKUs in a store
         * This is the scalable API for bulk availability checks
         */
        @PostMapping("/availability")
        public Mono<ResponseEntity<InventoryAvailabilityResponse>> checkInventoryAvailability(
                        @Valid @RequestBody InventoryAvailabilityRequest request) {

                log.info("Checking inventory availability for store: {} and {} SKUs",
                                request.getStoreId(), request.getSkus().size());

                // Validate request size to prevent abuse
                if (request.getSkus().size() > 100) {
                        log.warn("Availability check rejected: too many SKUs requested ({})", request.getSkus().size());
                        return Mono.just(ResponseEntity.badRequest().build());
                }

                return inventoryService.checkInventoryAvailability(request.getStoreId(), request.getSkus())
                                .map(ResponseEntity::ok)
                                .doOnNext(response -> log.info("Availability check completed for store {} with {} SKUs",
                                                request.getStoreId(), request.getSkus().size()));
        }

        /**
         * Check inventory availability for a single SKU in a store
         * Convenience endpoint for single product checks
         */
        @GetMapping("/availability/single")
        public Mono<ResponseEntity<InventoryAvailabilityResponse>> checkSingleInventoryAvailability(
                        @RequestParam Long storeId,
                        @RequestParam String sku) {

                log.info("Checking single inventory availability for store: {} and SKU: {}", storeId, sku);

                return inventoryService.checkSingleInventoryAvailability(storeId, sku)
                                .map(ResponseEntity::ok)
                                .doOnNext(response -> log.info(
                                                "Single availability check completed for store {} and SKU {}", storeId,
                                                sku));
        }

        /**
         * Get all store IDs where a product is available
         * Used by search service for indexing
         */
        @GetMapping("/product/{productId}/stores")
        public Mono<ResponseEntity<List<Long>>> getStoresForProduct(@PathVariable Long productId) {
                log.info("Getting stores for product: {}", productId);

                return inventoryService.getStoresForProduct(productId)
                                .collectList()
                                .map(ResponseEntity::ok)
                                .doOnNext(response -> log.info("Found {} stores for product {}",
                                                response.getBody().size(), productId));
        }

        /**
         * Get store IDs for multiple products (bulk)
         * Returns map of productId -> List<storeId>
         * Used by search service for efficient bulk indexing
         */
        @PostMapping("/products/stores")
        public Mono<ResponseEntity<Map<Long, List<Long>>>> getStoresForProducts(
                        @RequestBody List<Long> productIds) {
                log.info("Getting stores for {} products", productIds.size());

                return inventoryService.getStoresForProducts(productIds)
                                .map(ResponseEntity::ok)
                                .doOnNext(response -> log.info("Returned store mappings for {} products",
                                                response.getBody().size()));
        }

        /**
         * Find nearest store with inventory for requested SKUs
         * Critical API for quick commerce - determines which store can fulfill order
         */
        @PostMapping("/nearest-store")
        public Mono<ResponseEntity<NearestStoreResponse>> findNearestStoreWithInventory(
                        @Valid @RequestBody NearestStoreRequest request) {

                log.info("Finding nearest store for location: ({}, {}) with {} SKUs",
                                request.getLatitude(), request.getLongitude(), request.getSkus().size());

                return inventoryService.findNearestStoreWithInventory(
                                request.getLatitude(),
                                request.getLongitude(),
                                request.getSkus())
                                .map(ResponseEntity::ok)
                                .onErrorReturn(InventoryNotFoundException.class, ResponseEntity.notFound().build())
                                .doOnNext(response -> log.info("Nearest store lookup completed: status={}",
                                                response.getStatusCode()));
        }

        /**
         * Health check endpoint
         */
        @GetMapping("/health")
        public Mono<ResponseEntity<String>> health() {
                return Mono.just(ResponseEntity.ok("Inventory Service is healthy"));
        }
}