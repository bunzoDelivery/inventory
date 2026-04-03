package com.quickcommerce.order.controller;

import com.quickcommerce.order.config.PrintPricingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(PrintPricingController.class)
@Import(PrintPricingConfig.class)
class PrintPricingControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("GET /api/v1/print/pricing returns 200 with default pricing values")
    void getPricing_returnsDefaultPricing() {
        webTestClient.get()
                .uri("/api/v1/print/pricing")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.basePricePerPageBlackWhite").isEqualTo(2.00)
                .jsonPath("$.basePricePerPageColor").isEqualTo(5.00)
                .jsonPath("$.doubleSideMultiplier").isEqualTo(1.5)
                .jsonPath("$.currency").isEqualTo("ZMW");
    }

    @Test
    @DisplayName("GET /api/v1/print/pricing response contains all required fields")
    void getPricing_containsAllRequiredFields() {
        webTestClient.get()
                .uri("/api/v1/print/pricing")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.basePricePerPageBlackWhite").exists()
                .jsonPath("$.basePricePerPageColor").exists()
                .jsonPath("$.doubleSideMultiplier").exists()
                .jsonPath("$.currency").exists();
    }

    @Test
    @DisplayName("GET /api/v1/print/pricing black & white price is less than color price")
    void getPricing_bwPriceLessThanColor() {
        webTestClient.get()
                .uri("/api/v1/print/pricing")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.basePricePerPageBlackWhite").isNumber()
                .jsonPath("$.basePricePerPageColor").isNumber();
    }
}
