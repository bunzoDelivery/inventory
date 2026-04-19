package com.quickcommerce.order.service;

import com.quickcommerce.order.client.CatalogClient;
import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.config.PrintPricingConfig;
import com.quickcommerce.order.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPreviewServiceTest {

    @Mock private CatalogClient catalogClient;
    @Mock private InventoryClient inventoryClient;

    private PrintPricingConfig printPricingConfig;
    private OrderPreviewService service;

    @BeforeEach
    void setUp() {
        printPricingConfig = new PrintPricingConfig();
        printPricingConfig.setBasePriceBw(new BigDecimal("2.00"));
        printPricingConfig.setBasePriceColor(new BigDecimal("5.00"));
        printPricingConfig.setDoubleSideMultiplier(new BigDecimal("1.5"));

        service = new OrderPreviewService(catalogClient, inventoryClient, printPricingConfig);
    }

    @Test
    @DisplayName("Print-only cart: computes pricing from config, no catalog/inventory calls")
    void printOnlyCart_computesPricingFromConfig() {
        PreviewOrderRequest request = PreviewOrderRequest.builder()
                .storeId(1L)
                .printItems(List.of(
                        PreviewOrderRequest.PrintItemRequest.builder()
                                .label("doc.pdf")
                                .pages(10)
                                .colorMode("BLACK_WHITE")
                                .sides("DOUBLE")
                                .copies(2)
                                .build()))
                .build();

        StepVerifier.create(service.preview(request))
                .assertNext(response -> {
                    assertThat(response.getSummary().getRegularItemsTotal()).isEqualByComparingTo("0");
                    assertThat(response.getSummary().getPrintItemsTotal()).isEqualByComparingTo("60.00");
                    assertThat(response.getSummary().getGrandTotal()).isEqualByComparingTo("60.00");
                    assertThat(response.getSummary().getCurrency()).isEqualTo("ZMW");

                    assertThat(response.getRegularItems()).isEmpty();
                    assertThat(response.getPrintItems()).hasSize(1);

                    PreviewOrderResponse.PrintItemResponse printItem = response.getPrintItems().get(0);
                    assertThat(printItem.getLabel()).isEqualTo("doc.pdf");
                    assertThat(printItem.getBasePricePerPage()).isEqualByComparingTo("2.00");
                    assertThat(printItem.getSideMultiplier()).isEqualByComparingTo("1.5");
                    assertThat(printItem.getSubTotal()).isEqualByComparingTo("60.00");
                })
                .verifyComplete();

        verifyNoInteractions(catalogClient, inventoryClient);
    }

    @Test
    @DisplayName("Print item with COLOR and SINGLE side uses correct rates")
    void printItem_colorSingle_usesCorrectRates() {
        PreviewOrderRequest request = PreviewOrderRequest.builder()
                .storeId(1L)
                .printItems(List.of(
                        PreviewOrderRequest.PrintItemRequest.builder()
                                .label("slides.pdf")
                                .pages(5)
                                .colorMode("COLOR")
                                .sides("SINGLE")
                                .copies(1)
                                .build()))
                .build();

        StepVerifier.create(service.preview(request))
                .assertNext(response -> {
                    PreviewOrderResponse.PrintItemResponse item = response.getPrintItems().get(0);
                    assertThat(item.getBasePricePerPage()).isEqualByComparingTo("5.00");
                    assertThat(item.getSideMultiplier()).isEqualByComparingTo("1");
                    assertThat(item.getSubTotal()).isEqualByComparingTo("25.00");

                    assertThat(response.getSummary().getPrintItemsTotal()).isEqualByComparingTo("25.00");
                    assertThat(response.getSummary().getGrandTotal()).isEqualByComparingTo("25.00");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Mixed cart: regular items from catalog + print items from config")
    void mixedCart_combinesTotals() {
        when(catalogClient.getPrices(anyList()))
                .thenReturn(Flux.just(
                        ProductPriceResponse.builder().sku("MILK").basePrice(new BigDecimal("12.50")).build(),
                        ProductPriceResponse.builder().sku("BREAD").basePrice(new BigDecimal("8.00")).build()));

        when(inventoryClient.checkAvailability(any(), anyList()))
                .thenReturn(Mono.just(InventoryAvailabilityResponse.builder()
                        .storeId(1L)
                        .products(List.of(
                                InventoryAvailabilityResponse.ProductAvailability.builder()
                                        .sku("MILK").availableStock(50).build(),
                                InventoryAvailabilityResponse.ProductAvailability.builder()
                                        .sku("BREAD").availableStock(30).build()))
                        .build()));

        PreviewOrderRequest request = PreviewOrderRequest.builder()
                .storeId(1L)
                .items(List.of(
                        PreviewOrderRequest.PreviewItemRequest.builder().sku("MILK").qty(2).build(),
                        PreviewOrderRequest.PreviewItemRequest.builder().sku("BREAD").qty(1).build()))
                .printItems(List.of(
                        PreviewOrderRequest.PrintItemRequest.builder()
                                .label("hw.pdf").pages(4).colorMode("BLACK_WHITE")
                                .sides("SINGLE").copies(1).build()))
                .build();

        StepVerifier.create(service.preview(request))
                .assertNext(response -> {
                    assertThat(response.getSummary().getRegularItemsTotal()).isEqualByComparingTo("33.00");
                    assertThat(response.getSummary().getPrintItemsTotal()).isEqualByComparingTo("8.00");
                    assertThat(response.getSummary().getGrandTotal()).isEqualByComparingTo("41.00");

                    assertThat(response.getRegularItems()).hasSize(2);
                    assertThat(response.getPrintItems()).hasSize(1);

                    assertThat(response.getRegularItems().get(0).getAvailableQuantity()).isEqualTo(50);
                    assertThat(response.getWarnings()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Regular-only cart: no print items, backward compatible")
    void regularOnlyCart_noPrintItems() {
        when(catalogClient.getPrices(anyList()))
                .thenReturn(Flux.just(
                        ProductPriceResponse.builder().sku("MILK").basePrice(new BigDecimal("12.50")).build()));

        when(inventoryClient.checkAvailability(any(), anyList()))
                .thenReturn(Mono.just(InventoryAvailabilityResponse.builder()
                        .storeId(1L)
                        .products(List.of(
                                InventoryAvailabilityResponse.ProductAvailability.builder()
                                        .sku("MILK").availableStock(10).build()))
                        .build()));

        PreviewOrderRequest request = PreviewOrderRequest.builder()
                .storeId(1L)
                .items(List.of(
                        PreviewOrderRequest.PreviewItemRequest.builder().sku("MILK").qty(1).build()))
                .build();

        StepVerifier.create(service.preview(request))
                .assertNext(response -> {
                    assertThat(response.getSummary().getRegularItemsTotal()).isEqualByComparingTo("12.50");
                    assertThat(response.getSummary().getPrintItemsTotal()).isEqualByComparingTo("0");
                    assertThat(response.getSummary().getGrandTotal()).isEqualByComparingTo("12.50");

                    assertThat(response.getRegularItems()).hasSize(1);
                    assertThat(response.getPrintItems()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Empty cart: both arrays absent returns zero totals")
    void emptyCart_returnsZeroTotals() {
        PreviewOrderRequest request = PreviewOrderRequest.builder()
                .storeId(1L)
                .build();

        StepVerifier.create(service.preview(request))
                .assertNext(response -> {
                    assertThat(response.getSummary().getGrandTotal()).isEqualByComparingTo("0");
                    assertThat(response.getRegularItems()).isEmpty();
                    assertThat(response.getPrintItems()).isEmpty();
                })
                .verifyComplete();

        verifyNoInteractions(catalogClient, inventoryClient);
    }

    @Test
    @DisplayName("Multiple print items are summed correctly")
    void multiplePrintItems_summedCorrectly() {
        PreviewOrderRequest request = PreviewOrderRequest.builder()
                .storeId(1L)
                .printItems(List.of(
                        PreviewOrderRequest.PrintItemRequest.builder()
                                .label("a.pdf").pages(10).colorMode("BLACK_WHITE")
                                .sides("SINGLE").copies(1).build(),
                        PreviewOrderRequest.PrintItemRequest.builder()
                                .label("b.pdf").pages(5).colorMode("COLOR")
                                .sides("DOUBLE").copies(2).build()))
                .build();

        StepVerifier.create(service.preview(request))
                .assertNext(response -> {
                    // a.pdf: 2.00 * 10 * 1 * 1.0 = 20.00
                    // b.pdf: 5.00 * 5 * 2 * 1.5 = 75.00
                    assertThat(response.getPrintItems()).hasSize(2);
                    assertThat(response.getPrintItems().get(0).getSubTotal()).isEqualByComparingTo("20.00");
                    assertThat(response.getPrintItems().get(1).getSubTotal()).isEqualByComparingTo("75.00");
                    assertThat(response.getSummary().getPrintItemsTotal()).isEqualByComparingTo("95.00");
                    assertThat(response.getSummary().getGrandTotal()).isEqualByComparingTo("95.00");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Inventory failure: regular items degrade, print items unaffected")
    void inventoryFailure_printItemsUnaffected() {
        when(catalogClient.getPrices(anyList()))
                .thenReturn(Flux.just(
                        ProductPriceResponse.builder().sku("MILK").basePrice(new BigDecimal("12.50")).build()));

        when(inventoryClient.checkAvailability(any(), anyList()))
                .thenReturn(Mono.error(new RuntimeException("Inventory service down")));

        PreviewOrderRequest request = PreviewOrderRequest.builder()
                .storeId(1L)
                .items(List.of(
                        PreviewOrderRequest.PreviewItemRequest.builder().sku("MILK").qty(1).build()))
                .printItems(List.of(
                        PreviewOrderRequest.PrintItemRequest.builder()
                                .label("doc.pdf").pages(10).colorMode("COLOR")
                                .sides("SINGLE").copies(1).build()))
                .build();

        StepVerifier.create(service.preview(request))
                .assertNext(response -> {
                    assertThat(response.getRegularItems()).hasSize(1);
                    assertThat(response.getRegularItems().get(0).getAvailableQuantity()).isNull();

                    assertThat(response.getPrintItems()).hasSize(1);
                    assertThat(response.getPrintItems().get(0).getSubTotal()).isEqualByComparingTo("50.00");

                    assertThat(response.getSummary().getRegularItemsTotal()).isEqualByComparingTo("12.50");
                    assertThat(response.getSummary().getPrintItemsTotal()).isEqualByComparingTo("50.00");
                    assertThat(response.getSummary().getGrandTotal()).isEqualByComparingTo("62.50");

                    assertThat(response.getWarnings()).contains("Stock availability could not be verified");
                })
                .verifyComplete();
    }
}
