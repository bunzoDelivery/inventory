package com.quickcommerce.order.controller;

import com.quickcommerce.order.dto.CreateOrderRequest;
import com.quickcommerce.order.dto.OrderResponse;
import com.quickcommerce.order.dto.ProductPriceResponse;
import com.quickcommerce.order.client.CatalogClient;
import com.quickcommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CatalogClient catalogClient;

    @PostMapping
    public Mono<ResponseEntity<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("Received order creation request");
        return orderService.createOrder(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/preview")
    public Flux<ProductPriceResponse> previewOrder(@RequestBody Map<String, List<String>> request) {
        // Simple preview that just checks prices/stock presence via Catalog
        // Request body expects: { "skus": ["SKU1", "SKU2"] }
        List<String> skus = request.get("skus");
        if (skus == null || skus.isEmpty()) {
            return Flux.empty();
        }
        return catalogClient.getPrices(skus);
    }

    @PostMapping("/{orderUuid}/pay-mock")
    public Mono<ResponseEntity<OrderResponse>> mockPayment(@PathVariable String orderUuid) {
        log.info("Received mock payment for order: {}", orderUuid);
        return orderService.mockPayment(orderUuid)
                .map(ResponseEntity::ok);
    }
}
