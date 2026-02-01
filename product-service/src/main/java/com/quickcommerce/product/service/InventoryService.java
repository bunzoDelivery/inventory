package com.quickcommerce.product.service;

import com.quickcommerce.product.config.InventoryProperties;
import com.quickcommerce.product.domain.InventoryItem;
import com.quickcommerce.product.domain.StockMovement;
import com.quickcommerce.product.domain.StockReservation;
import com.quickcommerce.product.dto.AddStockRequest;
import com.quickcommerce.product.dto.InventoryAvailabilityResponse;
import com.quickcommerce.product.dto.ReserveStockRequest;
import com.quickcommerce.product.dto.StockReservationResponse;
import com.quickcommerce.product.event.LowStockAlertEvent;
import com.quickcommerce.product.event.StockMovementEvent;
import com.quickcommerce.product.exception.InsufficientStockException;
import com.quickcommerce.product.exception.InventoryNotFoundException;
import com.quickcommerce.product.exception.InvalidReservationException;
import com.quickcommerce.product.exception.OptimisticLockingException;
import com.quickcommerce.product.exception.ReservationNotFoundException;
import com.quickcommerce.product.domain.Store;
import com.quickcommerce.product.dto.NearestStoreResponse;
import com.quickcommerce.product.repository.InventoryItemRepository;
import com.quickcommerce.product.repository.StockMovementRepository;
import com.quickcommerce.product.repository.StockReservationRepository;
import com.quickcommerce.product.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for inventory management operations
 * Handles stock tracking, reservations, and movements
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

        private final InventoryItemRepository inventoryItemRepository;
        private final StockMovementRepository stockMovementRepository;
        private final StockReservationRepository stockReservationRepository;
        private final StoreRepository storeRepository;
        private final ApplicationEventPublisher eventPublisher;
        private final TransactionalOperator transactionalOperator;
        private final InventoryProperties inventoryProperties;

        /**
         * Get inventory item by SKU (with caching)
         */
        @Cacheable(value = "inventory", key = "#sku", unless = "#result == null")
        public Mono<InventoryItem> getInventoryBySku(String sku) {
                return inventoryItemRepository.findBySku(sku)
                                .switchIfEmpty(Mono.error(new InventoryNotFoundException("SKU not found: " + sku)));
        }

        /**
         * Reserve stock for checkout process
         * Uses atomic check-and-reserve to prevent race conditions and overselling
         */
        public Mono<StockReservationResponse> reserveStock(ReserveStockRequest request) {
                log.info("Reserving stock for SKU: {}, Quantity: {}", 
                        request.getSku(), request.getQuantity());

                return inventoryItemRepository.findBySku(request.getSku())
                                .switchIfEmpty(Mono.error(
                                                new InventoryNotFoundException("SKU not found: " + request.getSku())))
                                .flatMap(item -> reserveStockAtomic(item, request))
                                .as(transactionalOperator::transactional);
        }

        /**
         * Confirm reservation (convert to actual sale)
         */
        public Mono<Void> confirmReservation(String reservationId) {
                log.info("Confirming reservation: {}", reservationId);

                return doConfirmReservation(reservationId)
                                .as(transactionalOperator::transactional);
        }

        /**
         * Internal method for confirming reservation (wrapped in transaction)
         */
        private Mono<Void> doConfirmReservation(String reservationId) {
                return stockReservationRepository.findByReservationId(reservationId)
                                .switchIfEmpty(Mono.error(new ReservationNotFoundException(
                                                "Reservation not found: " + reservationId)))
                                .flatMap(reservation -> {
                                        if (reservation.getStatus() != StockReservation.ReservationStatus.ACTIVE) {
                                                return Mono
                                                                .error(new InvalidReservationException(
                                                                                "Reservation is not active: "
                                                                                                + reservationId));
                                        }

                                        return inventoryItemRepository.findById(reservation.getInventoryItemId())
                                                        .flatMap(item -> {
                                                                int newCurrentStock = item.getCurrentStock()
                                                                                - reservation.getQuantity();
                                                                int newReservedStock = item.getReservedStock()
                                                                                - reservation.getQuantity();

                                                                return updateInventoryStock(item.getId(),
                                                                                newCurrentStock, newReservedStock,
                                                                                item.getVersion())
                                                                                .then(updateReservationStatus(
                                                                                                reservationId,
                                                                                                StockReservation.ReservationStatus.CONFIRMED))
                                                                                .then(createStockMovement(item.getId(),
                                                                                                reservation.getQuantity(),
                                                                                                StockMovement.MovementType.OUTBOUND,
                                                                                                StockMovement.ReferenceType.SALE,
                                                                                                reservation.getOrderId(),
                                                                                                "Order confirmed"))
                                                                                .then(checkLowStockAlert(item))
                                                                                .then(evictInventoryCache(item.getSku()));
                                                        });
                                });
        }

        /**
         * Cancel reservation
         */
        public Mono<Void> cancelReservation(String reservationId) {
                log.info("Cancelling reservation: {}", reservationId);

                return doCancelReservation(reservationId)
                                .as(transactionalOperator::transactional);
        }

        /**
         * Internal method for cancelling reservation (wrapped in transaction)
         */
        private Mono<Void> doCancelReservation(String reservationId) {
                return stockReservationRepository.findByReservationId(reservationId)
                                .switchIfEmpty(Mono.error(new ReservationNotFoundException(
                                                "Reservation not found: " + reservationId)))
                                .flatMap(reservation -> {
                                        return incrementReservedStock(reservation.getInventoryItemId(),
                                                        -reservation.getQuantity())
                                                        .then(updateReservationStatus(reservationId,
                                                                        StockReservation.ReservationStatus.CANCELLED))
                                                        .then(createStockMovement(reservation.getInventoryItemId(),
                                                                        reservation.getQuantity(),
                                                                        StockMovement.MovementType.UNRESERVE,
                                                                        StockMovement.ReferenceType.RESERVATION,
                                                                        reservationId, "Reservation cancelled"))
                                                        .then(inventoryItemRepository.findById(reservation.getInventoryItemId()))
                                                        .flatMap(item -> evictInventoryCache(item.getSku()));
                                });
        }

        /**
         * Add stock to inventory (with upsert - creates item if not exists)
         */
        public Mono<Void> addStock(AddStockRequest request) {
                log.info("Adding stock for SKU: {}, Store: {}, Quantity: {}",
                                request.getSku(), request.getStoreId(), request.getQuantity());

                return doAddStock(request)
                                .as(transactionalOperator::transactional);
        }

        /**
         * Internal method for adding stock (wrapped in transaction)
         */
        private Mono<Void> doAddStock(AddStockRequest request) {
                return inventoryItemRepository.findByStoreIdAndSku(request.getStoreId(), request.getSku())
                                .flatMap(item -> {
                                        // Item exists - update stock
                                        int newStock = item.getCurrentStock() + request.getQuantity();
                                        return updateCurrentStock(item.getId(), newStock, item.getVersion())
                                                        .then(createStockMovement(item.getId(), request.getQuantity(),
                                                                        StockMovement.MovementType.INBOUND,
                                                                        StockMovement.ReferenceType.PURCHASE,
                                                                        request.getReferenceId(),
                                                                        request.getReason()))
                                                        .then(evictInventoryCache(item.getSku()));
                                })
                                .switchIfEmpty(Mono.defer(() -> {
                                        // Item doesn't exist - create new inventory item
                                        log.info("Creating new inventory item for SKU: {} at store: {}",
                                                        request.getSku(), request.getStoreId());

                                        InventoryItem newItem = InventoryItem.builder()
                                                        .sku(request.getSku())
                                                        .productId(request.getProductId())
                                                        .storeId(request.getStoreId())
                                                        .currentStock(request.getQuantity())
                                                        .reservedStock(0)
                                                        .safetyStock(inventoryProperties.getStock().getDefaultSafetyStock())
                                                        .maxStock(inventoryProperties.getStock().getDefaultMaxStock())
                                                        .version(0L)
                                                        .lastUpdated(LocalDateTime.now())
                                                        .build();

                                        return inventoryItemRepository.save(newItem)
                                                        .flatMap(savedItem -> createStockMovement(savedItem.getId(),
                                                                        request.getQuantity(),
                                                                        StockMovement.MovementType.INBOUND,
                                                                        StockMovement.ReferenceType.PURCHASE,
                                                                        request.getReferenceId(),
                                                                        "Initial stock for new item"));
                                }));
        }

        /**
         * Get low stock items for a store
         */
        public Flux<InventoryItem> getLowStockItems(Long storeId) {
                log.debug("Getting low stock items for store: {}", storeId);
                return inventoryItemRepository.findLowStockItems(storeId);
        }

        /**
         * Get items that need replenishment
         */
        public Flux<InventoryItem> getItemsNeedingReplenishment(Long storeId) {
                log.debug("Getting items needing replenishment for store: {}", storeId);
                return inventoryItemRepository.findItemsNeedingReplenishment(storeId);
        }

        /**
         * Process expired reservations (scheduled task)
         * Improved with batch processing and error handling
         */
        @Scheduled(fixedRateString = "${inventory.reservation.cleanup-interval-seconds}000")
        public void processExpiredReservations() {
                LocalDateTime now = LocalDateTime.now();
                int batchSize = inventoryProperties.getReservation().getCleanupBatchSize();

                stockReservationRepository.findExpiredActiveReservations(now)
                                .buffer(batchSize) // Process in batches
                                .flatMap(batch -> {
                                        if (!batch.isEmpty()) {
                                                log.info("Processing {} expired reservations", batch.size());
                                        }
                                        return Flux.fromIterable(batch)
                                                .flatMap(reservation -> 
                                                        doCancelReservation(reservation.getReservationId())
                                                                .onErrorResume(error -> {
                                                                        log.error("Failed to cancel expired reservation: {}", 
                                                                                reservation.getReservationId(), error);
                                                                        // Continue processing other reservations
                                                                        return Mono.empty();
                                                                })
                                                )
                                                .then();
                                })
                                .doOnComplete(() -> log.debug("Expired reservation cleanup completed"))
                                .doOnError(error -> log.error("Fatal error in cleanup task", error))
                                .onErrorResume(error -> {
                                        // Don't let cleanup task failure affect the scheduler
                                        return Mono.empty();
                                })
                                .subscribe();
        }

        // Private helper methods

        /**
         * Atomically reserve stock with race condition prevention
         * This method ensures that stock check and reservation happen atomically at the
         * database level
         */
        private Mono<StockReservationResponse> reserveStockAtomic(InventoryItem item, ReserveStockRequest request) {
                String reservationId = generateReservationId();
                Duration reservationTtl = Duration.ofMinutes(inventoryProperties.getReservation().getTtlMinutes());
                LocalDateTime expiresAt = LocalDateTime.now().plus(reservationTtl);

                StockReservation reservation = StockReservation.builder()
                                .reservationId(reservationId)
                                .inventoryItemId(item.getId())
                                .quantity(request.getQuantity())
                                .customerId(Long.valueOf(request.getCustomerId()))
                                .orderId(request.getOrderId())
                                .expiresAt(expiresAt)
                                .status(StockReservation.ReservationStatus.ACTIVE)
                                .createdAt(LocalDateTime.now())
                                .build();

                // Step 1: Create reservation record
                return stockReservationRepository.save(reservation)
                                // Step 2: Atomically check availability AND reserve (prevents race conditions)
                                .then(inventoryItemRepository.reserveStockAtomic(item.getId(), request.getQuantity()))
                                .flatMap(rowsAffected -> {
                                        if (rowsAffected == 0) {
                                                // Atomic update failed = insufficient stock
                                                // Rollback: delete the reservation record
                                                log.warn("Insufficient stock for SKU: {}. Available: {}, Requested: {}",
                                                                request.getSku(), item.getAvailableStock(),
                                                                request.getQuantity());

                                                return stockReservationRepository.delete(reservation)
                                                                .then(Mono.error(new InsufficientStockException(
                                                                                String.format("Insufficient stock for SKU: %s. Available: %d, Requested: %d",
                                                                                                request.getSku(),
                                                                                                item.getAvailableStock(),
                                                                                                request.getQuantity()))));
                                        }

                                        // Success! Stock was atomically reserved
                                        log.info("Successfully reserved {} units of SKU: {} (expires in {} min)",
                                                        request.getQuantity(), request.getSku(), 
                                                        inventoryProperties.getReservation().getTtlMinutes());

                                        // Step 3: Create audit trail
                                        return createStockMovement(item.getId(), request.getQuantity(),
                                                        StockMovement.MovementType.RESERVE,
                                                        StockMovement.ReferenceType.RESERVATION,
                                                        reservationId, "Stock reservation")
                                                        .then(evictInventoryCache(item.getSku()))
                                                        .then(Mono.just(StockReservationResponse.fromDomain(reservation,
                                                                        item.getSku())));
                                });
        }

        private Mono<Void> incrementReservedStock(Long itemId, Integer increment) {
                return inventoryItemRepository.incrementReservedStock(itemId, increment)
                                .filter(updated -> updated > 0)
                                .switchIfEmpty(Mono.error(new RuntimeException("Failed to update reserved stock")))
                                .then();
        }

        private Mono<Void> updateCurrentStock(Long itemId, Integer newCurrentStock, Long version) {
                return inventoryItemRepository.updateCurrentStockWithVersion(itemId, newCurrentStock, version)
                                .filter(updated -> updated > 0)
                                .switchIfEmpty(Mono.error(
                                                new OptimisticLockingException("Concurrent modification detected")))
                                .then();
        }

        private Mono<Void> updateInventoryStock(Long itemId, Integer newCurrentStock, Integer newReservedStock,
                        Long version) {
                return inventoryItemRepository
                                .updateStockWithVersion(itemId, newCurrentStock, newReservedStock, version)
                                .filter(updated -> updated > 0)
                                .switchIfEmpty(Mono.error(
                                                new OptimisticLockingException("Concurrent modification detected")))
                                .then();
        }

        private Mono<Void> createStockMovement(Long itemId, Integer quantity, StockMovement.MovementType type,
                        StockMovement.ReferenceType refType, String refId, String reason) {
                StockMovement movement = StockMovement.builder()
                                .inventoryItemId(itemId)
                                .movementType(type)
                                .quantity(quantity)
                                .referenceType(refType)
                                .referenceId(refId)
                                .reason(reason)
                                .createdAt(LocalDateTime.now())
                                .build();

                return stockMovementRepository.save(movement)
                                .then(Mono.fromRunnable(
                                                () -> eventPublisher.publishEvent(new StockMovementEvent(movement))))
                                .then();
        }

        private Mono<Void> updateReservationStatus(String reservationId, StockReservation.ReservationStatus status) {
                return stockReservationRepository.updateStatus(reservationId, status)
                                .then();
        }

        private Mono<Void> checkLowStockAlert(InventoryItem item) {
                if (item.isLowStock()) {
                        eventPublisher.publishEvent(new LowStockAlertEvent(item));
                }
                return Mono.empty();
        }

        private String generateReservationId() {
                return "RES_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        }

        /**
         * Evict inventory cache for a SKU
         */
        @CacheEvict(value = "inventory", key = "#sku")
        private Mono<Void> evictInventoryCache(String sku) {
                log.debug("Evicting inventory cache for SKU: {}", sku);
                return Mono.empty();
        }

        /**
         * Check inventory availability for multiple SKUs in a store
         * This is a scalable API that can handle bulk availability checks
         */
        public Mono<InventoryAvailabilityResponse> checkInventoryAvailability(Long storeId, List<String> skus) {
                log.info("Checking inventory availability for store: {} and SKUs: {}", storeId, skus);

                if (skus == null || skus.isEmpty()) {
                        return Mono.just(InventoryAvailabilityResponse.builder()
                                        .storeId(storeId)
                                        .products(List.of())
                                        .build());
                }

                return inventoryItemRepository.findByStoreIdAndSkuIn(storeId, skus)
                                .collectList()
                                .map(items -> {
                                        log.debug("Found {} items for store {} and SKUs {}", items.size(), storeId,
                                                        skus);
                                        return InventoryAvailabilityResponse.fromInventoryItems(storeId, items);
                                })
                                .doOnNext(response -> log.info(
                                                "Availability check completed for store {} with {} products",
                                                storeId, response.getProducts().size()));
        }

        /**
         * Check inventory availability for a single SKU in a store
         * Convenience method for single product checks
         */
        public Mono<InventoryAvailabilityResponse> checkSingleInventoryAvailability(Long storeId, String sku) {
                log.info("Checking inventory availability for store: {} and SKU: {}", storeId, sku);

                return inventoryItemRepository.findByStoreIdAndSku(storeId, sku)
                                .map(item -> InventoryAvailabilityResponse.fromInventoryItems(storeId, List.of(item)))
                                .switchIfEmpty(Mono.just(InventoryAvailabilityResponse.builder()
                                                .storeId(storeId)
                                                .products(List.of())
                                                .build()))
                                .doOnNext(response -> log.info(
                                                "Single availability check completed for store {} and SKU {}", storeId,
                                                sku));
        }

        /**
         * Find nearest store with inventory for requested SKUs
         * This is the key API for quick commerce - determines which store can fulfill
         * the order
         *
         * @param latitude  Customer's latitude
         * @param longitude Customer's longitude
         * @param skus      List of SKUs to check availability
         * @return Nearest store with inventory availability and delivery estimate
         */
        public Mono<NearestStoreResponse> findNearestStoreWithInventory(Double latitude, Double longitude,
                        List<String> skus) {
                log.info("Finding nearest store for location: ({}, {}) with {} SKUs", latitude, longitude, skus.size());

                return storeRepository.findNearestStore(latitude, longitude)
                                .switchIfEmpty(Mono.error(
                                                new InventoryNotFoundException("No stores available in your area")))
                                .flatMap(store -> {
                                        log.debug("Nearest store found: {} at distance: {} km",
                                                        store.getName(),
                                                        store.calculateDistanceKm(latitude, longitude));

                                        // Check if location is serviceable
                                        if (!store.isLocationServiceable(latitude, longitude)) {
                                                return Mono.error(new InventoryNotFoundException(
                                                                String.format("Location is outside serviceable area. Nearest store is %.2f km away (max: %d km)",
                                                                                store.calculateDistanceKm(latitude,
                                                                                                longitude),
                                                                                store.getServiceableRadiusKm())));
                                        }

                                        // Check inventory availability for requested SKUs
                                        return checkInventoryAvailability(store.getId(), skus)
                                                        .map(availability -> NearestStoreResponse.fromStore(store,
                                                                        latitude, longitude, availability))
                                                        .doOnNext(response -> log.info(
                                                                        "Nearest store response: storeId={}, distance={} km, ETA={} min, items={}",
                                                                        response.getStoreId(), response.getDistanceKm(),
                                                                        response.getEstimatedDeliveryMinutes(),
                                                                        response.getInventoryAvailability()
                                                                                        .getProducts().size()));
                                });
        }
}
