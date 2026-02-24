package com.quickcommerce.order.controller;

import com.quickcommerce.order.dto.*;
import com.quickcommerce.order.service.OrderPreviewService;
import com.quickcommerce.order.service.OrderService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderPreviewService orderPreviewService;
    private final RateLimiter orderCreationRateLimiter;

    public OrderController(OrderService orderService,
                           OrderPreviewService orderPreviewService,
                           @Qualifier("orderCreationRateLimiter") RateLimiter orderCreationRateLimiter) {
        this.orderService = orderService;
        this.orderPreviewService = orderPreviewService;
        this.orderCreationRateLimiter = orderCreationRateLimiter;
    }

    // ─── Checkout ──────────────────────────────────────────────────────────────

    @PostMapping("/preview")
    public Mono<ResponseEntity<PreviewOrderResponse>> previewOrder(
            @Valid @RequestBody PreviewOrderRequest request) {
        return orderPreviewService.preview(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        log.info("Order creation request from customer: {}", request.getCustomerId());
        return orderService.createOrder(request, idempotencyKey)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r))
                .transformDeferred(RateLimiterOperator.of(orderCreationRateLimiter));
    }

    // ─── Query ─────────────────────────────────────────────────────────────────

    @GetMapping("/{orderUuid}")
    public Mono<ResponseEntity<OrderResponse>> getOrder(@PathVariable String orderUuid) {
        return orderService.getOrder(orderUuid)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/customer/{customerId}")
    public Flux<OrderResponse> getCustomerOrders(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.getCustomerOrders(customerId, page, Math.min(size, 50));
    }

    @GetMapping("/store/{storeId}")
    public Flux<OrderResponse> getStoreOrders(
            @PathVariable Long storeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.getStoreOrders(storeId, status, page, Math.min(size, 50));
    }

    // ─── Customer Actions ──────────────────────────────────────────────────────

    @PostMapping("/{orderUuid}/cancel")
    public Mono<ResponseEntity<OrderResponse>> cancelOrder(
            @PathVariable String orderUuid,
            @Valid @RequestBody CancelOrderRequest request,
            @RequestHeader("Customer-Id") String customerId) {
        return orderService.cancelOrder(orderUuid, customerId, request.getReason())
                .map(ResponseEntity::ok);
    }

    // ─── Dark Store Operations ─────────────────────────────────────────────────

    @PostMapping("/{orderUuid}/status")
    public Mono<ResponseEntity<OrderResponse>> updateOrderStatus(
            @PathVariable String orderUuid,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            @RequestHeader(value = "Actor-Id", defaultValue = "SYSTEM") String actorId) {
        return orderService.updateStatus(orderUuid, request.getStatus(), actorId, request.getNotes())
                .map(ResponseEntity::ok);
    }

    // ─── Dev / QA Only ─────────────────────────────────────────────────────────

    @PostMapping("/{orderUuid}/pay-mock")
    public Mono<ResponseEntity<OrderResponse>> mockPayment(@PathVariable String orderUuid) {
        log.info("Mock payment for order: {}", orderUuid);
        return orderService.mockPayment(orderUuid)
                .map(ResponseEntity::ok);
    }

    // ─── Exception Handlers ────────────────────────────────────────────────────

    @ExceptionHandler(RequestNotPermitted.class)
    public Mono<ResponseEntity<Map<String, String>>> handleRateLimit(RequestNotPermitted ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Too many requests. Please try again shortly.")));
    }
}
