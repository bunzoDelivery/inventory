package com.quickcommerce.product.catalog.service;

import com.quickcommerce.common.dto.VariantDto;
import com.quickcommerce.product.BaseContainerTest;
import com.quickcommerce.product.catalog.domain.Category;
import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.catalog.dto.CreateProductRequest;
import com.quickcommerce.product.catalog.dto.GroupSummary;
import com.quickcommerce.product.catalog.dto.ProductResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the variant-groups feature.
 * Uses a real MySQL testcontainer via BaseContainerTest.
 *
 * Covers:
 *  - groupId auto-generation and explicit override on createProduct
 *  - getVariantGroups with real DB data (price-sorted variants)
 *  - getAllGroupSummaries correctness
 */
class CatalogServiceGroupsIntegrationTest extends BaseContainerTest {

    @Autowired
    private CatalogService catalogService;

    // =========================================================================
    // createProduct — groupId handling
    // =========================================================================

    @Nested
    @DisplayName("createProduct — groupId")
    class CreateProductGroupIdTests {

        @Test
        @DisplayName("Auto-generates groupId from brand + name when not provided")
        void autoGeneratesGroupId_whenNotProvided() {
            Category cat = createTestCategory("Dairy", "dairy-" + System.nanoTime());

            CreateProductRequest req = request("AMUL-500-" + System.nanoTime(),
                    "Taaza Milk 500ml", "Amul", null, cat.getId(), new BigDecimal("15.00"));

            StepVerifier.create(catalogService.createProduct(req))
                    .assertNext(product -> {
                        assertThat(product.getGroupId()).isEqualTo("amul-taaza-milk");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Two variants with same brand+base name auto-generate the same groupId")
        void twoVariants_sameBaseNameAutoGroup() {
            Category cat = createTestCategory("Dairy2", "dairy2-" + System.nanoTime());
            String suffix = String.valueOf(System.nanoTime());

            CreateProductRequest req500ml = request("AMUL-500-" + suffix,
                    "Taaza Milk 500ml", "Amul", null, cat.getId(), new BigDecimal("15.00"));
            req500ml.setSlug("amul-taaza-milk-500-" + suffix);

            CreateProductRequest req1L = request("AMUL-1L-" + suffix,
                    "Taaza Milk 1 Litre", "Amul", null, cat.getId(), new BigDecimal("28.00"));
            req1L.setSlug("amul-taaza-milk-1l-" + suffix);

            ProductResponse created500ml = catalogService.createProduct(req500ml).block();
            ProductResponse created1L    = catalogService.createProduct(req1L).block();

            assertThat(created500ml.getGroupId())
                    .isEqualTo(created1L.getGroupId())
                    .isEqualTo("amul-taaza-milk");
        }

        @Test
        @DisplayName("Explicit groupId overrides auto-generation")
        void explicitGroupId_overridesAutoGen() {
            Category cat = createTestCategory("Dairy3", "dairy3-" + System.nanoTime());

            CreateProductRequest req = request("CUSTOM-SKU-" + System.nanoTime(),
                    "Taaza Milk 500ml", "Amul", "my-custom-group", cat.getId(), new BigDecimal("15.00"));

            StepVerifier.create(catalogService.createProduct(req))
                    .assertNext(product -> {
                        assertThat(product.getGroupId()).isEqualTo("my-custom-group");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("groupId is null when brand is null and no explicit groupId provided")
        void nullBrand_groupIdIsNull() {
            Category cat = createTestCategory("Misc", "misc-" + System.nanoTime());

            CreateProductRequest req = request("NO-BRAND-" + System.nanoTime(),
                    "Generic Product", null, null, cat.getId(), new BigDecimal("50.00"));

            StepVerifier.create(catalogService.createProduct(req))
                    .assertNext(product -> assertThat(product.getGroupId()).isNull())
                    .verifyComplete();
        }
    }

    // =========================================================================
    // getVariantGroups — real DB
    // =========================================================================

    @Nested
    @DisplayName("getVariantGroups — integration")
    class GetVariantGroupsIntegrationTests {

        @Test
        @DisplayName("Returns variants sorted by price ascending")
        void returns_variantsSortedByPriceAsc() {
            String groupId = "amul-milk-sort-" + System.nanoTime();
            Category cat = createTestCategory("Dairy4", "dairy4-" + System.nanoTime());

            // Insert in reverse price order to verify DB-level sort
            createProductWithGroup("SKU-1L-" + groupId,  "Milk 1L",   groupId, cat.getId(), new BigDecimal("28.00"), true);
            createProductWithGroup("SKU-2L-" + groupId,  "Milk 2L",   groupId, cat.getId(), new BigDecimal("52.00"), true);
            createProductWithGroup("SKU-500-" + groupId, "Milk 500ml", groupId, cat.getId(), new BigDecimal("15.00"), true);

            StepVerifier.create(catalogService.getVariantGroups(List.of(groupId)))
                    .assertNext(map -> {
                        List<VariantDto> variants = map.get(groupId);
                        assertThat(variants).hasSize(3);
                        assertThat(variants.get(0).getPrice()).isEqualByComparingTo("15.00");
                        assertThat(variants.get(1).getPrice()).isEqualByComparingTo("28.00");
                        assertThat(variants.get(2).getPrice()).isEqualByComparingTo("52.00");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Returns empty map for unknown groupId")
        void unknownGroupId_returnsEmptyMap() {
            StepVerifier.create(catalogService.getVariantGroups(List.of("group-that-does-not-exist")))
                    .assertNext(map -> assertThat(map).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Returns empty map for empty input — no DB call attempted")
        void emptyInput_returnsEmptyMap() {
            StepVerifier.create(catalogService.getVariantGroups(List.of()))
                    .assertNext(map -> assertThat(map).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Fetches multiple groups in a single call")
        void multipleGroups_fetchedTogether() {
            String group1 = "milk-group-" + System.nanoTime();
            String group2 = "noodle-group-" + System.nanoTime();
            Category cat = createTestCategory("Mixed", "mixed-" + System.nanoTime());

            createProductWithGroup("MK-500-" + group1, "Milk 500ml",  group1, cat.getId(), new BigDecimal("15.00"), true);
            createProductWithGroup("MK-1L-" + group1,  "Milk 1L",     group1, cat.getId(), new BigDecimal("28.00"), true);
            createProductWithGroup("ND-70-" + group2,  "Noodles 70g", group2, cat.getId(), new BigDecimal("14.00"), true);
            createProductWithGroup("ND-4PK-" + group2, "Noodles 4pk", group2, cat.getId(), new BigDecimal("50.00"), true);

            StepVerifier.create(catalogService.getVariantGroups(List.of(group1, group2)))
                    .assertNext(map -> {
                        assertThat(map).containsOnlyKeys(group1, group2);
                        assertThat(map.get(group1)).hasSize(2);
                        assertThat(map.get(group2)).hasSize(2);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Excludes inactive products from variant group results")
        void inactiveProducts_excludedFromResults() {
            String groupId = "inactive-group-" + System.nanoTime();
            Category cat = createTestCategory("Active", "active-" + System.nanoTime());

            createProductWithGroup("ACT-" + groupId,  "Active Variant",   groupId, cat.getId(), new BigDecimal("20.00"), true);
            // inactive product — is_active = false
            createProductWithGroupActive("INACT-" + groupId, "Inactive Variant", groupId, cat.getId(), new BigDecimal("10.00"), true, false);

            StepVerifier.create(catalogService.getVariantGroups(List.of(groupId)))
                    .assertNext(map -> {
                        List<VariantDto> variants = map.get(groupId);
                        assertThat(variants).hasSize(1);
                        assertThat(variants.get(0).getSku()).isEqualTo("ACT-" + groupId);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("inStock reflects product.isAvailable — false when out of stock")
        void inStock_reflectsIsAvailable() {
            String groupId = "oos-group-" + System.nanoTime();
            Category cat = createTestCategory("Stock", "stock-" + System.nanoTime());

            createProductWithGroup("IN-STOCK-" + groupId, "In Stock",  groupId, cat.getId(), new BigDecimal("15.00"), true);
            createProductWithGroup("OOS-" + groupId,      "Out Stock", groupId, cat.getId(), new BigDecimal("28.00"), false);

            StepVerifier.create(catalogService.getVariantGroups(List.of(groupId)))
                    .assertNext(map -> {
                        List<VariantDto> variants = map.get(groupId);
                        assertThat(variants).hasSize(2);
                        // Sorted by price: 15 (inStock=true), 28 (inStock=false)
                        assertThat(variants.get(0).getInStock()).isTrue();
                        assertThat(variants.get(1).getInStock()).isFalse();
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // getAllGroupSummaries — real DB
    // =========================================================================

    @Nested
    @DisplayName("getAllGroupSummaries — integration")
    class GetAllGroupSummariesIntegrationTests {

        @Test
        @DisplayName("Returns each groupId with correct variant count")
        void returnsCorrectVariantCounts() {
            String group1 = "summary-g1-" + System.nanoTime();
            String group2 = "summary-g2-" + System.nanoTime();
            Category cat = createTestCategory("Summary", "summary-" + System.nanoTime());

            createProductWithGroup("SUM-A-" + group1, "Prod A", group1, cat.getId(), new BigDecimal("10.00"), true);
            createProductWithGroup("SUM-B-" + group1, "Prod B", group1, cat.getId(), new BigDecimal("20.00"), true);
            createProductWithGroup("SUM-C-" + group1, "Prod C", group1, cat.getId(), new BigDecimal("30.00"), true);
            createProductWithGroup("SUM-D-" + group2, "Prod D", group2, cat.getId(), new BigDecimal("10.00"), true);

            StepVerifier.create(catalogService.getAllGroupSummaries()
                            .filter(s -> s.getGroupId().equals(group1) || s.getGroupId().equals(group2))
                            .collectList())
                    .assertNext(summaries -> {
                        Map<String, Long> byId = new java.util.HashMap<>();
                        summaries.forEach(s -> byId.put(s.getGroupId(), s.getVariantCount()));
                        assertThat(byId.get(group1)).isEqualTo(3L);
                        assertThat(byId.get(group2)).isEqualTo(1L);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Products without groupId are not included in summaries")
        void productsWithoutGroupId_notIncluded() {
            Category cat = createTestCategory("NoGroup", "nogroup-" + System.nanoTime());
            // product with no groupId
            createTestProduct("NO-GRP-" + System.nanoTime(), "Ungrouped Product", cat.getId(), new BigDecimal("10.00"));

            StepVerifier.create(catalogService.getAllGroupSummaries()
                            .filter(s -> s.getGroupId() == null))
                    .verifyComplete(); // null groupId entries should never appear
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private CreateProductRequest request(String sku, String name, String brand,
                                          String groupId, Long categoryId, BigDecimal price) {
        CreateProductRequest req = new CreateProductRequest();
        req.setSku(sku);
        req.setName(name);
        req.setBrand(brand);
        req.setGroupId(groupId);
        req.setCategoryId(categoryId);
        req.setBasePrice(price);
        req.setUnitOfMeasure("unit");
        req.setSlug(sku.toLowerCase().replaceAll("[^a-z0-9]", "-"));
        req.setIsActive(true);
        req.setIsAvailable(true);
        return req;
    }

    /** Creates a product with groupId, active=true */
    private Product createProductWithGroup(String sku, String name, String groupId,
                                            Long categoryId, BigDecimal price, Boolean isAvailable) {
        return createProductWithGroupActive(sku, name, groupId, categoryId, price, isAvailable, true);
    }

    /** Creates a product with full control over isActive flag */
    private Product createProductWithGroupActive(String sku, String name, String groupId,
                                                  Long categoryId, BigDecimal price,
                                                  Boolean isAvailable, Boolean isActive) {
        Product p = new Product();
        p.setSku(sku);
        p.setName(name);
        p.setGroupId(groupId);
        p.setCategoryId(categoryId);
        p.setBasePrice(price);
        p.setIsAvailable(isAvailable);
        p.setIsActive(isActive);
        p.setUnitOfMeasure("unit");
        p.setSlug(sku.toLowerCase().replaceAll("[^a-z0-9]", "-"));
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(p).block();
    }
}
