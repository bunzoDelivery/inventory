package com.quickcommerce.order.controller;

import com.quickcommerce.order.dto.CreateOrderRequest;
import com.quickcommerce.order.dto.OrderResponse;
import com.quickcommerce.order.dto.PreviewOrderRequest;
import com.quickcommerce.order.dto.PreviewOrderResponse;
import com.quickcommerce.order.service.OrderPreviewService;
import com.quickcommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderPreviewService orderPreviewService;

    @PostMapping
    public Mono<ResponseEntity<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        log.info("Received order creation request");
        return orderService.createOrder(request, idempotencyKey)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/preview")
    public Mono<ResponseEntity<PreviewOrderResponse>> previewOrder(@Valid @RequestBody PreviewOrderRequest request) {
        return orderPreviewService.preview(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{orderUuid}/pay-mock")
    public Mono<ResponseEntity<OrderResponse>> mockPayment(@PathVariable String orderUuid) {
        log.info("Received mock payment for order: {}", orderUuid);
        return orderService.mockPayment(orderUuid)
                .map(ResponseEntity::ok);
    }
}
