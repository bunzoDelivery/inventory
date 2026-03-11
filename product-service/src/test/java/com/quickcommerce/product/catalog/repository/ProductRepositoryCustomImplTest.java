package com.quickcommerce.product.catalog.repository;

import com.quickcommerce.product.BaseContainerTest;
import com.quickcommerce.product.catalog.domain.Category;
import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.catalog.dto.ProductSortOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ProductRepositoryCustomImpl (R2dbcEntityTemplate + Criteria API).
 * Runs against a real MySQL instance via Testcontainers — verifies actual SQL sort and filter behaviour.
 */
class ProductRepositoryCustomImplTest extends BaseContainerTest {

    private Long categoryId;

    /**
     * Seeds 5 products across 2 brands with distinct prices before each test:
     *   Amul Milk   55  (Amul)
     *   Tata Curd   65  (Tata)
     *   Tata Paneer 120 (Tata)
     *   Amul Butter 280 (Amul)
     *   Amul Ghee   650 (Amul)
     */
    @BeforeEach
    void seedProducts() {
        Category cat = createTestCategory("Dairy", "dairy-custom-impl");
        categoryId = cat.getId();

        saveWithBrand(createTestProduct("CI-001", "Amul Milk",   categoryId, BigDecimal.valueOf(55)),  "Amul");
        saveWithBrand(createTestProduct("CI-002", "Tata Curd",   categoryId, BigDecimal.valueOf(65)),  "Tata");
        saveWithBrand(createTestProduct("CI-003", "Tata Paneer", categoryId, BigDecimal.valueOf(120)), "Tata");
        saveWithBrand(createTestProduct("CI-004", "Amul Butter", categoryId, BigDecimal.valueOf(280)), "Amul");
        saveWithBrand(createTestProduct("CI-005", "Amul Ghee",   categoryId, BigDecimal.valueOf(650)), "Amul");
    }

    private void saveWithBrand(Product product, String brand) {
        product.setBrand(brand);
        productRepository.save(product).block();
    }

    // ─── sort tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PRICE_ASC returns all 5 products sorted cheapest first")
    void priceAsc_returnsAllProductsCheapestFirst() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, ProductSortOption.PRICE_ASC, null, 10, 0)
                        .map(Product::getBasePrice)
                        .collectList())
                .assertNext(prices -> assertThat(prices)
                        .isSortedAccordingTo(BigDecimal::compareTo)
                        .hasSize(5))
                .verifyComplete();
    }

    @Test
    @DisplayName("PRICE_DESC returns all 5 products most expensive first")
    void priceDesc_returnsAllProductsMostExpensiveFirst() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, ProductSortOption.PRICE_DESC, null, 10, 0)
                        .map(Product::getBasePrice)
                        .collectList())
                .assertNext(prices -> assertThat(prices)
                        .isSortedAccordingTo((a, b) -> b.compareTo(a))
                        .hasSize(5))
                .verifyComplete();
    }

    @Test
    @DisplayName("No sort returns products in default name order")
    void noSort_returnsProductsInNameOrder() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, null, null, 10, 0)
                        .map(Product::getName)
                        .collectList())
                .assertNext(names -> {
                    assertThat(names).hasSize(5);
                    assertThat(names).isSortedAccordingTo(String::compareTo);
                })
                .verifyComplete();
    }

    // ─── brand filter tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("Brand filter returns only products matching that brand")
    void brandFilter_returnsOnlyMatchingBrand() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, null, "Amul", 10, 0)
                        .collectList())
                .assertNext(products -> {
                    assertThat(products).hasSize(3);
                    assertThat(products).allSatisfy(p -> assertThat(p.getBrand()).isEqualTo("Amul"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Brand filter + PRICE_ASC returns only that brand sorted cheapest first")
    void brandFilter_withPriceAsc_returnsSortedFilteredProducts() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, ProductSortOption.PRICE_ASC, "Amul", 10, 0)
                        .collectList())
                .assertNext(products -> {
                    assertThat(products).hasSize(3);
                    List<BigDecimal> prices = products.stream().map(Product::getBasePrice).toList();
                    assertThat(prices).isSortedAccordingTo(BigDecimal::compareTo);
                    assertThat(prices.get(0)).isEqualByComparingTo("55");   // Amul Milk
                    assertThat(prices.get(2)).isEqualByComparingTo("650");  // Amul Ghee
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Brand filter + PRICE_DESC returns only that brand sorted most expensive first")
    void brandFilter_withPriceDesc_returnsSortedFilteredProducts() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, ProductSortOption.PRICE_DESC, "Tata", 10, 0)
                        .collectList())
                .assertNext(products -> {
                    assertThat(products).hasSize(2);
                    assertThat(products.get(0).getBasePrice()).isEqualByComparingTo("120"); // Tata Paneer
                    assertThat(products.get(1).getBasePrice()).isEqualByComparingTo("65");  // Tata Curd
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Unknown brand returns empty result")
    void unknownBrand_returnsEmpty() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, null, "NonExistentBrand", 10, 0))
                .verifyComplete();
    }

    // ─── count tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Count without brand returns total product count in category")
    void countNoBrand_returnsTotalCount() {
        StepVerifier.create(productRepository.countByCategoryWithFilters(categoryId, null))
                .expectNext(5L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Count with brand filter returns only matching brand count")
    void countWithBrand_returnsFilteredCount() {
        StepVerifier.create(productRepository.countByCategoryWithFilters(categoryId, "Amul"))
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Count with unknown brand returns zero")
    void countUnknownBrand_returnsZero() {
        StepVerifier.create(productRepository.countByCategoryWithFilters(categoryId, "Unknown"))
                .expectNext(0L)
                .verifyComplete();
    }

    // ─── pagination tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Pagination limit=2 returns first 2 products only")
    void pagination_limit2_returnsFirstTwoProducts() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, ProductSortOption.PRICE_ASC, null, 2, 0)
                        .collectList())
                .assertNext(products -> assertThat(products).hasSize(2))
                .verifyComplete();
    }

    @Test
    @DisplayName("Pagination offset=3 with PRICE_ASC skips first 3 cheapest products")
    void pagination_offset3_skipsFirstThree() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, ProductSortOption.PRICE_ASC, null, 10, 3)
                        .collectList())
                .assertNext(products -> {
                    assertThat(products).hasSize(2);
                    assertThat(products.get(0).getBasePrice()).isEqualByComparingTo("280"); // Amul Butter
                    assertThat(products.get(1).getBasePrice()).isEqualByComparingTo("650"); // Amul Ghee
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Pagination with brand filter + limit=1 returns single product")
    void pagination_brandFilter_limit1_returnsSingleProduct() {
        StepVerifier.create(
                productRepository.findByCategoryWithFilters(categoryId, ProductSortOption.PRICE_ASC, "Tata", 1, 0)
                        .collectList())
                .assertNext(products -> {
                    assertThat(products).hasSize(1);
                    assertThat(products.get(0).getBasePrice()).isEqualByComparingTo("65"); // Tata Curd (cheapest Tata)
                })
                .verifyComplete();
    }
}
