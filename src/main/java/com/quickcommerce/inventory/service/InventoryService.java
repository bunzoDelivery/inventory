package com.quickcommerce.inventory.service;

import com.quickcommerce.inventory.domain.InventoryItem;
import com.quickcommerce.inventory.domain.StockMovement;
import com.quickcommerce.inventory.domain.StockReservation;
import com.quickcommerce.inventory.dto.AddStockRequest;
import com.quickcommerce.inventory.dto.InventoryAvailabilityResponse;
import com.quickcommerce.inventory.dto.ReserveStockRequest;
import com.quickcommerce.inventory.dto.StockReservationResponse;
import com.quickcommerce.inventory.event.LowStockAlertEvent;
import com.quickcommerce.inventory.event.StockMovementEvent;
import com.quickcommerce.inventory.exception.InsufficientStockException;
import com.quickcommerce.inventory.exception.InventoryNotFoundException;
import com.quickcommerce.inventory.exception.InvalidReservationException;
import com.quickcommerce.inventory.exception.OptimisticLockingException;
import com.quickcommerce.inventory.exception.ReservationNotFoundException;
import com.quickcommerce.inventory.repository.InventoryItemRepository;
import com.quickcommerce.inventory.repository.StockMovementRepository;
import com.quickcommerce.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockReservationRepository stockReservationRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

    /**
     * Get inventory item by SKU
     */
    public Mono<InventoryItem> getInventoryBySku(String sku) {
        return inventoryItemRepository.findBySku(sku)
                .switchIfEmpty(Mono.error(new InventoryNotFoundException("SKU not found: " + sku)));
    }

    /**
     * Reserve stock for checkout process
     */
    public Mono<StockReservationResponse> reserveStock(ReserveStockRequest request) {
        log.info("Reserving stock for SKU: {}, Quantity: {}, Customer: {}, Order: {}",
                request.getSku(), request.getQuantity(), request.getCustomerId(), request.getOrderId());

        return inventoryItemRepository.findBySku(request.getSku())
                .switchIfEmpty(Mono.error(new InventoryNotFoundException("SKU not found: " + request.getSku())))
                .flatMap(item -> {
                    if (!item.isAvailableForReservation(request.getQuantity())) {
                        return Mono.error(new InsufficientStockException(
                                String.format("Insufficient stock for SKU: %s. Available: %d, Requested: %d",
                                        request.getSku(), item.getAvailableStock(), request.getQuantity())));
                    }

                    return reserveStockInternal(item, request);
                });
    }

    /**
     * Confirm reservation (convert to actual sale)
     */
    public Mono<Void> confirmReservation(String reservationId) {
        log.info("Confirming reservation: {}", reservationId);

        return stockReservationRepository.findByReservationId(reservationId)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException("Reservation not found: " + reservationId)))
                .flatMap(reservation -> {
                    if (reservation.getStatus() != StockReservation.ReservationStatus.ACTIVE) {
                        return Mono
                                .error(new InvalidReservationException("Reservation is not active: " + reservationId));
                    }

                    return inventoryItemRepository.findById(reservation.getInventoryItemId())
                            .flatMap(item -> {
                                int newCurrentStock = item.getCurrentStock() - reservation.getQuantity();
                                int newReservedStock = item.getReservedStock() - reservation.getQuantity();

                                return updateInventoryStock(item.getId(), newCurrentStock, newReservedStock,
                                        item.getVersion())
                                        .then(updateReservationStatus(reservationId,
                                                StockReservation.ReservationStatus.CONFIRMED))
                                        .then(createStockMovement(item.getId(), reservation.getQuantity(),
                                                StockMovement.MovementType.OUTBOUND, StockMovement.ReferenceType.SALE,
                                                reservation.getOrderId(), "Order confirmed"))
                                        .then(checkLowStockAlert(item));
                            });
                });
    }

    /**
     * Cancel reservation
     */
    public Mono<Void> cancelReservation(String reservationId) {
        log.info("Cancelling reservation: {}", reservationId);

        return stockReservationRepository.findByReservationId(reservationId)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException("Reservation not found: " + reservationId)))
                .flatMap(reservation -> {
                    return incrementReservedStock(reservation.getInventoryItemId(), -reservation.getQuantity())
                            .then(updateReservationStatus(reservationId,
                                    StockReservation.ReservationStatus.CANCELLED))
                            .then(createStockMovement(reservation.getInventoryItemId(), reservation.getQuantity(),
                                    StockMovement.MovementType.UNRESERVE,
                                    StockMovement.ReferenceType.RESERVATION,
                                    reservationId, "Reservation cancelled"));
                });
    }

    /**
     * Add stock to inventory
     */
    public Mono<Void> addStock(AddStockRequest request) {
        log.info("Adding stock for SKU: {}, Quantity: {}, Reason: {}",
                request.getSku(), request.getQuantity(), request.getReason());

        return inventoryItemRepository.findBySku(request.getSku())
                .switchIfEmpty(Mono.error(new InventoryNotFoundException("SKU not found: " + request.getSku())))
                .flatMap(item -> {
                    int newStock = item.getCurrentStock() + request.getQuantity();

                    return updateCurrentStock(item.getId(), newStock, item.getVersion())
                            .then(createStockMovement(item.getId(), request.getQuantity(),
                                    StockMovement.MovementType.INBOUND,
                                    StockMovement.ReferenceType.PURCHASE, request.getReferenceId(),
                                    request.getReason()));
                });
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
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void processExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();

        stockReservationRepository.findByStatusAndExpiresAtBefore(
                StockReservation.ReservationStatus.ACTIVE, now)
                .flatMap(reservation -> cancelReservation(reservation.getReservationId()))
                .subscribe(
                        result -> log.debug("Processed expired reservation"),
                        error -> log.error("Error processing expired reservation", error));
    }

    // Private helper methods

    private Mono<StockReservationResponse> reserveStockInternal(InventoryItem item, ReserveStockRequest request) {
        String reservationId = generateReservationId();
        LocalDateTime expiresAt = LocalDateTime.now().plus(RESERVATION_TTL);

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

        return stockReservationRepository.save(reservation)
                .then(incrementReservedStock(item.getId(), request.getQuantity()))
                .then(createStockMovement(item.getId(), request.getQuantity(), StockMovement.MovementType.RESERVE,
                        StockMovement.ReferenceType.RESERVATION, reservationId, "Stock reservation"))
                .then(Mono.just(StockReservationResponse.fromDomain(reservation, item.getSku())));
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
                .switchIfEmpty(Mono.error(new OptimisticLockingException("Concurrent modification detected")))
                .then();
    }

    private Mono<Void> updateInventoryStock(Long itemId, Integer newCurrentStock, Integer newReservedStock,
            Long version) {
        return inventoryItemRepository.updateStockWithVersion(itemId, newCurrentStock, newReservedStock, version)
                .filter(updated -> updated > 0)
                .switchIfEmpty(Mono.error(new OptimisticLockingException("Concurrent modification detected")))
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
                .then(Mono.fromRunnable(() -> eventPublisher.publishEvent(new StockMovementEvent(movement))))
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
                    log.debug("Found {} items for store {} and SKUs {}", items.size(), storeId, skus);
                    return InventoryAvailabilityResponse.fromInventoryItems(storeId, items);
                })
                .doOnNext(response -> log.info("Availability check completed for store {} with {} products",
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
                .doOnNext(response -> log.info("Single availability check completed for store {} and SKU {}", storeId,
                        sku));
    }
}
