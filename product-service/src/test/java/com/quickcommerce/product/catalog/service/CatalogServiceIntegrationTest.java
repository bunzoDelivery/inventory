package com.quickcommerce.product.catalog.service;

import com.quickcommerce.product.BaseContainerTest;
import com.quickcommerce.product.catalog.domain.Category;
import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.catalog.dto.CreateProductRequest;
import com.quickcommerce.product.catalog.dto.ProductResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CatalogService in Product Service
 */
class CatalogServiceIntegrationTest extends BaseContainerTest {

    @Autowired
    private CatalogService catalogService;

    @Test
    @DisplayName("Should create product successfully")
    void shouldCreateProduct() {
        Category category = createTestCategory("Electronics", "electronics");

        CreateProductRequest request = new CreateProductRequest();
        request.setSku("NEW-PROD-001");
        request.setName("New Product");
        request.setDescription("New Product Description");
        request.setCategoryId(category.getId());
        request.setBasePrice(BigDecimal.valueOf(100));
        request.setUnitOfMeasure("unit");
        request.setSlug("new-product");

        var result = catalogService.createProduct(request);

        StepVerifier.create(result)
                .assertNext(product -> {
                    assertThat(product.getSku()).isEqualTo("NEW-PROD-001");
                    assertThat(product.getName()).isEqualTo("New Product");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get product by SKU")
    void shouldGetProductBySku() {
        Category category = createTestCategory("Electronics", "electronics");
        createTestProduct("ELEC-001", "Laptop", category.getId(), BigDecimal.valueOf(999.99));

        var result = catalogService.getProductBySku("ELEC-001");

        StepVerifier.create(result)
                .assertNext(product -> {
                    assertThat(product.getSku()).isEqualTo("ELEC-001");
                    assertThat(product.getName()).isEqualTo("Laptop");
                })
                .verifyComplete();
    }
}
