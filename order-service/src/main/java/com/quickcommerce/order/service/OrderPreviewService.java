package com.quickcommerce.order.service;

import com.quickcommerce.order.client.CatalogClient;
import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.config.PrintPricingConfig;
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
    private final PrintPricingConfig printPricingConfig;

    private static final String CURRENCY = "ZMW";

    public Mono<PreviewOrderResponse> preview(PreviewOrderRequest request) {
        if (!request.hasRegularItems() && !request.hasPrintItems()) {
            return Mono.just(emptyResponse(request.getStoreId()));
        }

        List<PreviewOrderResponse.PrintItemResponse> printItemResponses = computePrintItems(request);
        BigDecimal printTotal = printItemResponses.stream()
                .map(PreviewOrderResponse.PrintItemResponse::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!request.hasRegularItems()) {
            return Mono.just(buildResponse(
                    request.getStoreId(), List.of(), BigDecimal.ZERO,
                    printItemResponses, printTotal, List.of()));
        }

        List<String> skus = request.getItems().stream()
                .map(PreviewOrderRequest.PreviewItemRequest::getSku)
                .distinct()
                .toList();

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

                                List<PreviewOrderResponse.RegularItemResponse> regularItems = new ArrayList<>();
                                List<String> warnings = new ArrayList<>();
                                BigDecimal regularTotal = BigDecimal.ZERO;

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
                                    regularTotal = regularTotal.add(subTotal);

                                    regularItems.add(PreviewOrderResponse.RegularItemResponse.builder()
                                            .sku(req.getSku())
                                            .qty(req.getQty())
                                            .unitPrice(price)
                                            .subTotal(subTotal)
                                            .availableQuantity(availableQuantity)
                                            .build());
                                }

                                return buildResponse(request.getStoreId(), regularItems, regularTotal,
                                        printItemResponses, printTotal, warnings);
                            });
                })
                .onErrorResume(e -> {
                    log.warn("Preview failed for regular items, returning prices only", e);
                    return catalogClient.getPrices(skus)
                            .collectList()
                            .map(productPrices -> {
                                Map<String, BigDecimal> priceMap = productPrices.stream()
                                        .collect(Collectors.toMap(ProductPriceResponse::getSku, ProductPriceResponse::getPrice));

                                List<PreviewOrderResponse.RegularItemResponse> regularItems = new ArrayList<>();
                                BigDecimal regularTotal = BigDecimal.ZERO;

                                for (PreviewOrderRequest.PreviewItemRequest req : request.getItems()) {
                                    BigDecimal price = priceMap.get(req.getSku());
                                    if (price == null) continue;
                                    BigDecimal subTotal = price.multiply(BigDecimal.valueOf(req.getQty()));
                                    regularTotal = regularTotal.add(subTotal);
                                    regularItems.add(PreviewOrderResponse.RegularItemResponse.builder()
                                            .sku(req.getSku())
                                            .qty(req.getQty())
                                            .unitPrice(price)
                                            .subTotal(subTotal)
                                            .availableQuantity(null)
                                            .build());
                                }

                                return buildResponse(request.getStoreId(), regularItems, regularTotal,
                                        printItemResponses, printTotal,
                                        List.of("Stock availability could not be verified"));
                            });
                });
    }

    private List<PreviewOrderResponse.PrintItemResponse> computePrintItems(PreviewOrderRequest request) {
        if (!request.hasPrintItems()) {
            return List.of();
        }

        List<PreviewOrderResponse.PrintItemResponse> results = new ArrayList<>();

        for (PreviewOrderRequest.PrintItemRequest item : request.getPrintItems()) {
            BigDecimal basePricePerPage = "COLOR".equals(item.getColorMode())
                    ? printPricingConfig.getBasePriceColor()
                    : printPricingConfig.getBasePriceBw();

            BigDecimal sideMultiplier = "DOUBLE".equals(item.getSides())
                    ? printPricingConfig.getDoubleSideMultiplier()
                    : BigDecimal.ONE;

            int copies = item.getCopies() != null ? item.getCopies() : 1;

            BigDecimal subTotal = basePricePerPage
                    .multiply(BigDecimal.valueOf(item.getPages()))
                    .multiply(BigDecimal.valueOf(copies))
                    .multiply(sideMultiplier);

            results.add(PreviewOrderResponse.PrintItemResponse.builder()
                    .label(item.getLabel())
                    .pages(item.getPages())
                    .colorMode(item.getColorMode())
                    .sides(item.getSides())
                    .copies(copies)
                    .basePricePerPage(basePricePerPage)
                    .sideMultiplier(sideMultiplier)
                    .subTotal(subTotal)
                    .build());
        }

        return results;
    }

    private PreviewOrderResponse buildResponse(Long storeId,
                                                List<PreviewOrderResponse.RegularItemResponse> regularItems,
                                                BigDecimal regularTotal,
                                                List<PreviewOrderResponse.PrintItemResponse> printItems,
                                                BigDecimal printTotal,
                                                List<String> warnings) {
        return PreviewOrderResponse.builder()
                .storeId(storeId)
                .summary(PreviewOrderResponse.OrderSummary.builder()
                        .regularItemsTotal(regularTotal)
                        .printItemsTotal(printTotal)
                        .grandTotal(regularTotal.add(printTotal))
                        .currency(CURRENCY)
                        .build())
                .regularItems(regularItems)
                .printItems(printItems)
                .warnings(warnings)
                .build();
    }

    private PreviewOrderResponse emptyResponse(Long storeId) {
        return buildResponse(storeId, List.of(), BigDecimal.ZERO, List.of(), BigDecimal.ZERO, List.of());
    }
}
