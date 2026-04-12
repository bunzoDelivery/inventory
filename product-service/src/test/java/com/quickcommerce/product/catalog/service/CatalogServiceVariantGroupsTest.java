package com.quickcommerce.product.catalog.service;

import com.quickcommerce.common.dto.VariantDto;
import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.catalog.dto.GroupSummary;
import com.quickcommerce.product.catalog.repository.CategoryRepository;
import com.quickcommerce.product.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the variant-groups feature in CatalogService.
 * No Docker / DB required — all dependencies are Mockito mocks.
 *
 * Covers:
 *   - resolveGroupId (static helper)
 *   - getVariantGroups (batch endpoint backing method)
 *   - getAllGroupSummaries (admin listing endpoint backing method)
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceVariantGroupsTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(categoryRepository, productRepository);
    }

    // =========================================================================
    // resolveGroupId — pure static, no mocks needed
    // =========================================================================

    @Nested
    @DisplayName("resolveGroupId")
    class ResolveGroupIdTests {

        @Test
        @DisplayName("Returns explicit groupId trimmed when provided")
        void explicitGroupId_returnsTrimmed() {
            assertThat(CatalogService.resolveGroupId("  amul-taaza-milk  ", "Amul", "Taaza Milk 500ml"))
                    .isEqualTo("amul-taaza-milk");
        }

        @Test
        @DisplayName("Returns explicit groupId as-is when no size in name")
        void explicitGroupId_noAutoGen() {
            assertThat(CatalogService.resolveGroupId("my-custom-group", "Amul", "Taaza Milk 1L"))
                    .isEqualTo("my-custom-group");
        }

        @Test
        @DisplayName("Empty explicit groupId triggers auto-generation")
        void emptyExplicitGroupId_triggersAutoGen() {
            assertThat(CatalogService.resolveGroupId("", "Amul", "Taaza Milk 500ml"))
                    .isEqualTo("amul-taaza-milk");
        }

        @Test
        @DisplayName("Null explicit groupId triggers auto-generation")
        void nullExplicitGroupId_triggersAutoGen() {
            assertThat(CatalogService.resolveGroupId(null, "Amul", "Taaza Milk 500ml"))
                    .isEqualTo("amul-taaza-milk");
        }

        @Test
        @DisplayName("Strips trailing ml — 500ml suffix")
        void autoGen_stripsMillilitres() {
            assertThat(CatalogService.resolveGroupId(null, "Amul", "Taaza Milk 500ml"))
                    .isEqualTo("amul-taaza-milk");
        }

        @Test
        @DisplayName("Strips trailing L — 1 Litre suffix, same group as 500ml")
        void autoGen_stripsLitre_sameGroupAs500ml() {
            String group500ml = CatalogService.resolveGroupId(null, "Amul", "Taaza Milk 500ml");
            String group1L    = CatalogService.resolveGroupId(null, "Amul", "Taaza Milk 1 Litre");
            assertThat(group500ml).isEqualTo(group1L).isEqualTo("amul-taaza-milk");
        }

        @Test
        @DisplayName("Strips trailing kg — 1kg suffix")
        void autoGen_stripsKg() {
            assertThat(CatalogService.resolveGroupId(null, "Aashirvaad", "Atta 1kg"))
                    .isEqualTo("aashirvaad-atta");
        }

        @Test
        @DisplayName("Strips trailing 'Pack of N' — end-anchored")
        void autoGen_stripsPackOf() {
            assertThat(CatalogService.resolveGroupId(null, "Maggi", "2-Minute Noodles Pack of 4"))
                    .isEqualTo("maggi-2-minute-noodles");
        }

        @Test
        @DisplayName("Does NOT strip mid-name number — '2-Minute' preserved")
        void autoGen_preservesMidNameNumber() {
            String groupId = CatalogService.resolveGroupId(null, "Maggi", "2-Minute Noodles 70g");
            assertThat(groupId).contains("2-minute").doesNotEndWith("-2");
        }

        @Test
        @DisplayName("Strips trailing pcs — 12 pcs suffix")
        void autoGen_stripsPcs() {
            assertThat(CatalogService.resolveGroupId(null, "Eggland", "Farm Eggs 12 pcs"))
                    .isEqualTo("eggland-farm-eggs");
        }

        @Test
        @DisplayName("Returns null when brand is null")
        void nullBrand_returnsNull() {
            assertThat(CatalogService.resolveGroupId(null, null, "Taaza Milk 500ml"))
                    .isNull();
        }

        @Test
        @DisplayName("Returns null when brand is blank")
        void blankBrand_returnsNull() {
            assertThat(CatalogService.resolveGroupId(null, "   ", "Taaza Milk 500ml"))
                    .isNull();
        }

        @Test
        @DisplayName("Returns null when name is null")
        void nullName_returnsNull() {
            assertThat(CatalogService.resolveGroupId(null, "Amul", null))
                    .isNull();
        }

        @Test
        @DisplayName("Returns null when name is blank")
        void blankName_returnsNull() {
            assertThat(CatalogService.resolveGroupId(null, "Amul", "  "))
                    .isNull();
        }

        @Test
        @DisplayName("Generated slug is lowercase and hyphen-separated")
        void autoGen_isLowercaseHyphenated() {
            String result = CatalogService.resolveGroupId(null, "Mother Dairy", "Full Cream Milk 1L");
            assertThat(result)
                    .isLowerCase()
                    .matches("[a-z0-9-]+")
                    .doesNotStartWith("-")
                    .doesNotEndWith("-");
        }
    }

    // =========================================================================
    // getVariantGroups
    // =========================================================================

    @Nested
    @DisplayName("getVariantGroups")
    class GetVariantGroupsTests {

        @Test
        @DisplayName("Returns empty map for null input — no repository call")
        void nullInput_returnsEmptyMap() {
            StepVerifier.create(catalogService.getVariantGroups(null))
                    .assertNext(map -> assertThat(map).isEmpty())
                    .verifyComplete();

            verifyNoInteractions(productRepository);
        }

        @Test
        @DisplayName("Returns empty map for empty list — no repository call")
        void emptyList_returnsEmptyMap() {
            StepVerifier.create(catalogService.getVariantGroups(List.of()))
                    .assertNext(map -> assertThat(map).isEmpty())
                    .verifyComplete();

            verifyNoInteractions(productRepository);
        }

        @Test
        @DisplayName("Returns empty map when all IDs are blank — no repository call")
        void allBlankIds_returnsEmptyMap() {
            StepVerifier.create(catalogService.getVariantGroups(List.of("  ", "", "   ")))
                    .assertNext(map -> assertThat(map).isEmpty())
                    .verifyComplete();

            verifyNoInteractions(productRepository);
        }

        @Test
        @DisplayName("Deduplicates IDs before calling repository")
        void duplicateIds_deduplicatedBeforeQuery() {
            when(productRepository.findByGroupIdIn(List.of("amul-taaza-milk")))
                    .thenReturn(Flux.empty());

            catalogService.getVariantGroups(List.of(
                    "amul-taaza-milk", "amul-taaza-milk", "amul-taaza-milk")).block();

            verify(productRepository, times(1))
                    .findByGroupIdIn(List.of("amul-taaza-milk"));
        }

        @Test
        @DisplayName("Single group with two variants — keyed by groupId, correct VariantDto fields")
        void singleGroup_twoVariants_correctMapping() {
            Product p500ml = product(1L, "amul-taaza-milk", "AMK-500", "500 ml", new BigDecimal("15.00"), true);
            Product p1L    = product(2L, "amul-taaza-milk", "AMK-1L",  "1 Litre", new BigDecimal("28.00"), true);

            when(productRepository.findByGroupIdIn(anyList()))
                    .thenReturn(Flux.just(p500ml, p1L));

            StepVerifier.create(catalogService.getVariantGroups(List.of("amul-taaza-milk")))
                    .assertNext(map -> {
                        assertThat(map).containsOnlyKeys("amul-taaza-milk");
                        List<VariantDto> variants = map.get("amul-taaza-milk");
                        assertThat(variants).hasSize(2);

                        VariantDto first = variants.get(0);
                        assertThat(first.getProductId()).isEqualTo(1L);
                        assertThat(first.getSku()).isEqualTo("AMK-500");
                        assertThat(first.getSize()).isEqualTo("500 ml");
                        assertThat(first.getPrice()).isEqualByComparingTo("15.00");
                        assertThat(first.getInStock()).isTrue();

                        VariantDto second = variants.get(1);
                        assertThat(second.getProductId()).isEqualTo(2L);
                        assertThat(second.getSku()).isEqualTo("AMK-1L");
                        assertThat(second.getSize()).isEqualTo("1 Litre");
                        assertThat(second.getPrice()).isEqualByComparingTo("28.00");
                        assertThat(second.getInStock()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Two groups — each keyed correctly in the result map")
        void twoGroups_bothKeyedCorrectly() {
            Product amul500  = product(1L, "amul-taaza-milk", "AMK-500", "500 ml",  new BigDecimal("15.00"), true);
            Product amul1L   = product(2L, "amul-taaza-milk", "AMK-1L",  "1 Litre", new BigDecimal("28.00"), true);
            Product maggi70g = product(3L, "maggi-noodles",   "MAG-70",  "70g",     new BigDecimal("14.00"), true);
            Product maggi4pk = product(4L, "maggi-noodles",   "MAG-4PK", "Pack of 4", new BigDecimal("50.00"), false);

            when(productRepository.findByGroupIdIn(anyList()))
                    .thenReturn(Flux.just(amul500, amul1L, maggi70g, maggi4pk));

            StepVerifier.create(catalogService.getVariantGroups(
                            List.of("amul-taaza-milk", "maggi-noodles")))
                    .assertNext(map -> {
                        assertThat(map).containsOnlyKeys("amul-taaza-milk", "maggi-noodles");
                        assertThat(map.get("amul-taaza-milk")).hasSize(2);
                        assertThat(map.get("maggi-noodles")).hasSize(2);

                        // Out-of-stock variant is still included (inStock reflects product flag)
                        VariantDto outOfStock = map.get("maggi-noodles").stream()
                                .filter(v -> v.getSku().equals("MAG-4PK"))
                                .findFirst().orElseThrow();
                        assertThat(outOfStock.getInStock()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Unknown groupId returns no entry in map — not a null entry")
        void unknownGroupId_noEntryInMap() {
            when(productRepository.findByGroupIdIn(anyList()))
                    .thenReturn(Flux.empty());

            StepVerifier.create(catalogService.getVariantGroups(List.of("nonexistent-group")))
                    .assertNext(map -> assertThat(map).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("inStock maps from Product.isAvailable — false when unavailable")
        void inStock_mapsFromIsAvailable() {
            Product unavailable = product(5L, "some-group", "SKU-X", "1kg", new BigDecimal("99.00"), false);

            when(productRepository.findByGroupIdIn(anyList()))
                    .thenReturn(Flux.just(unavailable));

            StepVerifier.create(catalogService.getVariantGroups(List.of("some-group")))
                    .assertNext(map -> {
                        VariantDto dto = map.get("some-group").get(0);
                        assertThat(dto.getInStock()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("null packageSize maps to null size in VariantDto — no NPE")
        void nullPackageSize_mapsToNullSize() {
            Product p = product(6L, "grp", "SKU-Y", null, new BigDecimal("10.00"), true);

            when(productRepository.findByGroupIdIn(anyList()))
                    .thenReturn(Flux.just(p));

            StepVerifier.create(catalogService.getVariantGroups(List.of("grp")))
                    .assertNext(map -> {
                        VariantDto dto = map.get("grp").get(0);
                        assertThat(dto.getSize()).isNull();
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // getAllGroupSummaries
    // =========================================================================

    @Nested
    @DisplayName("getAllGroupSummaries")
    class GetAllGroupSummariesTests {

        @Test
        @DisplayName("Delegates directly to repository and streams results")
        void delegatesToRepository() {
            GroupSummary s1 = new GroupSummary("amul-taaza-milk", 3L);
            GroupSummary s2 = new GroupSummary("maggi-noodles", 2L);

            when(productRepository.findAllGroupSummaries())
                    .thenReturn(Flux.just(s1, s2));

            StepVerifier.create(catalogService.getAllGroupSummaries())
                    .expectNext(s1, s2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Returns empty Flux when no groups exist")
        void noGroups_returnsEmpty() {
            when(productRepository.findAllGroupSummaries()).thenReturn(Flux.empty());

            StepVerifier.create(catalogService.getAllGroupSummaries())
                    .verifyComplete();
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Product product(Long id, String groupId, String sku, String packageSize,
                             BigDecimal price, Boolean isAvailable) {
        Product p = new Product();
        p.setId(id);
        p.setGroupId(groupId);
        p.setSku(sku);
        p.setName("Product " + sku);
        p.setPackageSize(packageSize);
        p.setBasePrice(price);
        p.setIsAvailable(isAvailable);
        p.setIsActive(true);
        p.setUnitOfMeasure("unit");
        return p;
    }
}
