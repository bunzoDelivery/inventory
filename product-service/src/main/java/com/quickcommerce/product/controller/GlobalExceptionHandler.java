package com.quickcommerce.product.controller;

import com.quickcommerce.product.exception.InsufficientStockException;
import com.quickcommerce.product.exception.InventoryNotFoundException;
import com.quickcommerce.product.exception.InvalidReservationException;
import com.quickcommerce.product.exception.OptimisticLockingException;
import com.quickcommerce.product.exception.ReservationNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for inventory service
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InventoryNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInventoryNotFound(InventoryNotFoundException ex) {
        log.warn("Inventory not found: {}", ex.getMessage());

        Map<String, Object> error = createErrorResponse("INVENTORY_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInsufficientStock(InsufficientStockException ex) {
        log.warn("Insufficient stock: {}", ex.getMessage());

        Map<String, Object> error = createErrorResponse("INSUFFICIENT_STOCK", ex.getMessage(), HttpStatus.BAD_REQUEST);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleReservationNotFound(ReservationNotFoundException ex) {
        log.warn("Reservation not found: {}", ex.getMessage());

        Map<String, Object> error = createErrorResponse("RESERVATION_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
    }

    @ExceptionHandler(InvalidReservationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInvalidReservation(InvalidReservationException ex) {
        log.warn("Invalid reservation: {}", ex.getMessage());

        Map<String, Object> error = createErrorResponse("INVALID_RESERVATION", ex.getMessage(), HttpStatus.BAD_REQUEST);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
    }

    @ExceptionHandler(OptimisticLockingException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleOptimisticLocking(OptimisticLockingException ex) {
        log.warn("Optimistic locking failure: {}", ex.getMessage());

        Map<String, Object> error = createErrorResponse("CONCURRENT_MODIFICATION",
                "The resource was modified by another user. Please try again.", HttpStatus.CONFLICT);
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());

        Map<String, Object> error = createErrorResponse("INVALID_ARGUMENT", ex.getMessage(), HttpStatus.BAD_REQUEST);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        Map<String, Object> error = createErrorResponse("INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }

    private Map<String, Object> createErrorResponse(String code, String message, HttpStatus status) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("status", status.value());
        error.put("timestamp", LocalDateTime.now());
        return error;
    }
}
