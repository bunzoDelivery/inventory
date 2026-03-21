package com.quickcommerce.product.catalog.controller;

import com.quickcommerce.product.catalog.dto.DeliveredOrderSkusRequest;
import com.quickcommerce.product.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Internal APIs for other services (e.g. order-service on DELIVERED).
 * Not authenticated in MVP — restrict at network layer / add service auth later.
 */
@RestController
@RequestMapping("/api/v1/internal/catalog/products")
@RequiredArgsConstructor
@Slf4j
public class InternalCatalogController {

    private final CatalogService catalogService;

    /**
     * Record that a delivered order contained these SKUs (distinct); increments {@code order_count} per SKU.
     */
    @PostMapping("/order-delivered")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> orderDelivered(@Valid @RequestBody DeliveredOrderSkusRequest request) {
        return catalogService.incrementOrderCountsForDeliveredOrder(request.getSkus());
    }
}
