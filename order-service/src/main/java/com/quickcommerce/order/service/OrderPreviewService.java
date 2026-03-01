package com.quickcommerce.order.service;

import com.quickcommerce.order.client.CatalogClient;
import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPreviewService {

    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;

    public Mono<PreviewOrderResponse> preview(PreviewOrderRequest request) {
        List<String> skus = request.getItems().stream()
                .map(PreviewOrderRequest.PreviewItemRequest::getSku)
                .distinct()
                .toList();

        if (skus.isEmpty()) {
            return Mono.just(PreviewOrderResponse.builder()
                    .storeId(request.getStoreId())
                    .totalAmount(BigDecimal.ZERO)
                    .items(List.of())
                    .warnings(List.of())
                    .build());
        }

        return catalogClient.getPrices(skus)
                .collectList()
                .flatMap(productPrices -> {
                    Map<String, BigDecimal> priceMap = productPrices.stream()
                            .collect(Collectors.toMap(ProductPriceResponse::getSku, ProductPriceResponse::getPrice));

                    return inventoryClient.checkAvailability(request.getStoreId(), skus)
                            .defaultIfEmpty(InventoryAvailabilityResponse.builder()
                                    .storeId(request.getStoreId())
                                    .products(List.of())
                                    .build())
                            .map(availability -> {
                                Map<String, InventoryAvailabilityResponse.ProductAvailability> availabilityMap =
                                        availability.getProducts() != null
                                                ? availability.getProducts().stream()
                                                        .collect(Collectors.toMap(
                                                                InventoryAvailabilityResponse.ProductAvailability::getSku,
                                                                p -> p))
                                                : Map.<String, InventoryAvailabilityResponse.ProductAvailability>of();

                                List<PreviewOrderResponse.PreviewItemResponse> items = new ArrayList<>();
                                List<String> warnings = new ArrayList<>();
                                BigDecimal total = BigDecimal.ZERO;

                                for (PreviewOrderRequest.PreviewItemRequest req : request.getItems()) {
                                    BigDecimal price = priceMap.get(req.getSku());
                                    if (price == null) {
                                        warnings.add("Product not found: " + req.getSku());
                                        continue;
                                    }

                                    InventoryAvailabilityResponse.ProductAvailability avail =
                                            availabilityMap.get(req.getSku());
                                    Integer availableQuantity = avail != null && avail.getAvailableStock() != null
                                            ? avail.getAvailableStock() : null;

                                    if (avail != null && (availableQuantity == null || availableQuantity < req.getQty())) {
                                        warnings.add("Insufficient stock for " + req.getSku());
                                    }

                                    BigDecimal subTotal = price.multiply(BigDecimal.valueOf(req.getQty()));
                                    total = total.add(subTotal);

                                    items.add(PreviewOrderResponse.PreviewItemResponse.builder()
                                            .sku(req.getSku())
                                            .qty(req.getQty())
                                            .unitPrice(price)
                                            .subTotal(subTotal)
                                            .availableQuantity(availableQuantity)
                                            .build());
                                }

                                return PreviewOrderResponse.builder()
                                        .storeId(request.getStoreId())
                                        .totalAmount(total)
                                        .items(items)
                                        .warnings(warnings)
                                        .build();
                            });
                })
                .onErrorResume(e -> {
                    log.warn("Preview failed, returning prices only", e);
                    return catalogClient.getPrices(skus)
                            .collectList()
                            .map(productPrices -> {
                                Map<String, BigDecimal> priceMap = productPrices.stream()
                                        .collect(Collectors.toMap(ProductPriceResponse::getSku, ProductPriceResponse::getPrice));

                                List<PreviewOrderResponse.PreviewItemResponse> items = new ArrayList<>();
                                BigDecimal total = BigDecimal.ZERO;

                                for (PreviewOrderRequest.PreviewItemRequest req : request.getItems()) {
                                    BigDecimal price = priceMap.get(req.getSku());
                                    if (price == null) continue;
                                    BigDecimal subTotal = price.multiply(BigDecimal.valueOf(req.getQty()));
                                    total = total.add(subTotal);
                                    items.add(PreviewOrderResponse.PreviewItemResponse.builder()
                                            .sku(req.getSku())
                                            .qty(req.getQty())
                                            .unitPrice(price)
                                            .subTotal(subTotal)
                                            .availableQuantity(null)
                                            .build());
                                }

                                return PreviewOrderResponse.builder()
                                        .storeId(request.getStoreId())
                                        .totalAmount(total)
                                        .items(items)
                                        .warnings(List.of("Stock availability could not be verified"))
                                        .build();
                            });
                });
    }
}
