package com.quickcommerce.inventory.controller;

import com.quickcommerce.inventory.domain.InventoryItem;
import com.quickcommerce.inventory.dto.AddStockRequest;
import com.quickcommerce.inventory.dto.InventoryItemResponse;
import com.quickcommerce.inventory.dto.ReserveStockRequest;
import com.quickcommerce.inventory.dto.StockReservationResponse;
import com.quickcommerce.inventory.exception.InsufficientStockException;
import com.quickcommerce.inventory.exception.InventoryNotFoundException;
import com.quickcommerce.inventory.exception.InvalidReservationException;
import com.quickcommerce.inventory.exception.ReservationNotFoundException;
import com.quickcommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
     * Reserve stock for checkout
     */
    @PostMapping("/reserve")
    public Mono<ResponseEntity<StockReservationResponse>> reserveStock(
            @Valid @RequestBody ReserveStockRequest request) {
        log.info("Reserving stock: {}", request);

        return inventoryService.reserveStock(request)
                .map(ResponseEntity::ok)
                .onErrorReturn(InsufficientStockException.class,
                        ResponseEntity.badRequest().build())
                .onErrorReturn(InventoryNotFoundException.class,
                        ResponseEntity.notFound().build())
                .doOnNext(response -> log.info("Stock reservation result: {}", response.getStatusCode()));
    }

    /**
     * Confirm reservation (convert to sale)
     */
    @PostMapping("/reservations/{reservationId}/confirm")
    public Mono<Void> confirmReservation(@PathVariable String reservationId) {
        log.info("Confirming reservation: {}", reservationId);

        return inventoryService.confirmReservation(reservationId)
                .doOnSuccess(result -> log.info("Reservation confirmed successfully: {}", reservationId))
                .doOnError(error -> log.error("Failed to confirm reservation: {}", reservationId, error));
    }

    /**
     * Cancel reservation
     */
    @PostMapping("/reservations/{reservationId}/cancel")
    public Mono<Void> cancelReservation(@PathVariable String reservationId) {
        log.info("Cancelling reservation: {}", reservationId);

        return inventoryService.cancelReservation(reservationId)
                .doOnSuccess(result -> log.info("Reservation cancelled successfully: {}", reservationId))
                .doOnError(error -> log.error("Failed to cancel reservation: {}", reservationId, error));
    }

    /**
     * Add stock to inventory
     */
    @PostMapping("/stock/add")
    public Mono<Void> addStock(@Valid @RequestBody AddStockRequest request) {
        log.info("Adding stock: {}", request);

        return inventoryService.addStock(request)
                .doOnSuccess(result -> log.info("Stock added successfully for SKU: {}", request.getSku()))
                .doOnError(error -> log.error("Failed to add stock for SKU: {}", request.getSku(), error));
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
     * Health check endpoint
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("Inventory Service is healthy"));
    }
}