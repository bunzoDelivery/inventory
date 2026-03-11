package com.quickcommerce.product.catalog.service;

import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.catalog.dto.PagedProductResponse;
import com.quickcommerce.product.catalog.dto.ProductSortOption;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CatalogService pagination logic.
 * Uses Mockito mocks — no Docker / DB required.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServicePaginationTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(categoryRepository, productRepository);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private Product product(long id, String name, long categoryId) {
        return product(id, name, categoryId, BigDecimal.TEN, null);
    }

    private Product product(long id, String name, long categoryId, BigDecimal price, String brand) {
        Product p = new Product();
        p.setId(id);
        p.setSku("SKU-" + id);
        p.setName(name);
        p.setCategoryId(categoryId);
        p.setBasePrice(price);
        p.setBrand(brand);
        p.setUnitOfMeasure("piece");
        p.setIsActive(true);
        p.setIsAvailable(true);
        p.setSlug("slug-" + id);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private List<Product> products(int count, long categoryId) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> product(i, "Product " + i, categoryId))
                .toList();
    }

    // ─── tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("First page returns exactly pageSize items and correct meta")
    void firstPage_returnsCorrectItemsAndMeta() {
        long categoryId = 12L;
        int pageNum = 0, pageSize = 5;
        List<Product> page = products(5, categoryId);

        when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(0L)))
                .thenReturn(Flux.fromIterable(page));
        when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                .thenReturn(Mono.just(10L));

        StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(5);
                    PagedProductResponse.PageMeta meta = response.getMeta();
                    assertThat(meta.getPage()).isEqualTo(0);
                    assertThat(meta.getSize()).isEqualTo(5);
                    assertThat(meta.getTotalElements()).isEqualTo(10);
                    assertThat(meta.getTotalPages()).isEqualTo(2);
                    assertThat(meta.isFirst()).isTrue();
                    assertThat(meta.isLast()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Second page returns correct items and last=true")
    void secondPage_returnsCorrectItemsAndLastTrue() {
        long categoryId = 12L;
        int pageNum = 1, pageSize = 5;
        List<Product> page = products(5, categoryId);

        when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(5L)))
                .thenReturn(Flux.fromIterable(page));
        when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                .thenReturn(Mono.just(10L));

        StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                .assertNext(response -> {
                    PagedProductResponse.PageMeta meta = response.getMeta();
                    assertThat(meta.getPage()).isEqualTo(1);
                    assertThat(meta.isFirst()).isFalse();
                    assertThat(meta.isLast()).isTrue();
                    assertThat(meta.getTotalPages()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Empty category returns zero items, totalPages=0, first=true, last=true")
    void emptyCategory_returnsEmptyResponseWithCorrectMeta() {
        long categoryId = 99L;
        int pageNum = 0, pageSize = 5;

        when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(0L)))
                .thenReturn(Flux.empty());
        when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                .thenReturn(Mono.just(0L));

        StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                .assertNext(response -> {
                    assertThat(response.getContent()).isEmpty();
                    PagedProductResponse.PageMeta meta = response.getMeta();
                    assertThat(meta.getTotalElements()).isEqualTo(0);
                    assertThat(meta.getTotalPages()).isEqualTo(0);
                    assertThat(meta.isFirst()).isTrue();
                    assertThat(meta.isLast()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Last page with partial results (11 items, pageSize=5, page=2) returns 1 item and last=true")
    void lastPagePartialResults_returnsPartialItemsAndLastTrue() {
        long categoryId = 5L;
        int pageNum = 2, pageSize = 5;
        List<Product> lastPage = products(1, categoryId);

        when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(10L)))
                .thenReturn(Flux.fromIterable(lastPage));
        when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                .thenReturn(Mono.just(11L));

        StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    PagedProductResponse.PageMeta meta = response.getMeta();
                    assertThat(meta.getTotalPages()).isEqualTo(3);
                    assertThat(meta.isLast()).isTrue();
                    assertThat(meta.isFirst()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Out-of-range page returns empty content with last=true")
    void outOfRangePage_returnsEmptyContentLastTrue() {
        long categoryId = 12L;
        int pageNum = 100, pageSize = 5;

        when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(500L)))
                .thenReturn(Flux.empty());
        when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                .thenReturn(Mono.just(10L));

        StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                .assertNext(response -> {
                    assertThat(response.getContent()).isEmpty();
                    PagedProductResponse.PageMeta meta = response.getMeta();
                    assertThat(meta.isLast()).isTrue();
                    assertThat(meta.isFirst()).isFalse();
                    assertThat(meta.getPage()).isEqualTo(100);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Exactly pageSize items (boundary): single page, first=true, last=true")
    void exactlyPageSizeItems_singlePage() {
        long categoryId = 7L;
        int pageNum = 0, pageSize = 5;
        List<Product> page = products(5, categoryId);

        when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(0L)))
                .thenReturn(Flux.fromIterable(page));
        when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                .thenReturn(Mono.just(5L));

        StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(5);
                    PagedProductResponse.PageMeta meta = response.getMeta();
                    assertThat(meta.getTotalPages()).isEqualTo(1);
                    assertThat(meta.isFirst()).isTrue();
                    assertThat(meta.isLast()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Single item category, pageSize=20: returns 1 item, first=true, last=true")
    void singleItemCategory() {
        long categoryId = 3L;
        int pageNum = 0, pageSize = 20;
        List<Product> page = products(1, categoryId);

        when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(0L)))
                .thenReturn(Flux.fromIterable(page));
        when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    PagedProductResponse.PageMeta meta = response.getMeta();
                    assertThat(meta.getTotalPages()).isEqualTo(1);
                    assertThat(meta.isFirst()).isTrue();
                    assertThat(meta.isLast()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Large pageNum does not overflow offset (long arithmetic)")
    void largePageNum_noIntegerOverflow() {
        long categoryId = 1L;
        int pageNum = 50_000_000, pageSize = 50;
        long expectedOffset = (long) pageNum * pageSize; // 2_500_000_000L — overflows int

        when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(expectedOffset)))
                .thenReturn(Flux.empty());
        when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                .thenReturn(Mono.just(10L));

        StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                .assertNext(response -> assertThat(response.getContent()).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("meta.size always reflects requested pageSize, not actual items returned")
    void metaSize_reflectsRequestedPageSize() {
        long categoryId = 8L;
        int pageNum = 1, pageSize = 10;
        List<Product> lastPage = products(3, categoryId);

        when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(10L)))
                .thenReturn(Flux.fromIterable(lastPage));
        when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                .thenReturn(Mono.just(13L));

        StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(3);
                    assertThat(response.getMeta().getSize()).isEqualTo(10); // requested, not actual
                })
                .verifyComplete();
    }

    // ─── sort & filter tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductsByCategory with sort and filter")
    class SortAndFilterTests {

        @Test
        @DisplayName("No sort, no brand — delegates with null/null and returns correct paged response")
        void noSortNoBrand_delegatesWithNulls() {
            long categoryId = 10L;
            int pageNum = 0, pageSize = 5;
            List<Product> page = products(3, categoryId);

            when(productRepository.findByCategoryWithFilters(eq(categoryId), isNull(), isNull(), eq(pageSize), eq(0L)))
                    .thenReturn(Flux.fromIterable(page));
            when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                    .thenReturn(Mono.just(3L));

            StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, null))
                    .assertNext(response -> {
                        assertThat(response.getContent()).hasSize(3);
                        assertThat(response.getMeta().getTotalElements()).isEqualTo(3);
                        assertThat(response.getMeta().isFirst()).isTrue();
                        assertThat(response.getMeta().isLast()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("PRICE_ASC sort, no brand — delegates with PRICE_ASC and null brand")
        void priceAscSort_noBrand_delegatesCorrectly() {
            long categoryId = 11L;
            int pageNum = 0, pageSize = 5;
            List<Product> page = List.of(
                    product(1, "Cheap", categoryId, BigDecimal.valueOf(10), null),
                    product(2, "Expensive", categoryId, BigDecimal.valueOf(100), null)
            );

            when(productRepository.findByCategoryWithFilters(
                    eq(categoryId), eq(ProductSortOption.PRICE_ASC), isNull(), eq(pageSize), eq(0L)))
                    .thenReturn(Flux.fromIterable(page));
            when(productRepository.countByCategoryWithFilters(eq(categoryId), isNull()))
                    .thenReturn(Mono.just(2L));

            StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, ProductSortOption.PRICE_ASC, null))
                    .assertNext(response -> {
                        assertThat(response.getContent()).hasSize(2);
                        assertThat(response.getContent().get(0).getBasePrice()).isEqualByComparingTo("10");
                        assertThat(response.getContent().get(1).getBasePrice()).isEqualByComparingTo("100");
                        assertThat(response.getMeta().getTotalPages()).isEqualTo(1);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("PRICE_DESC sort with brand filter — delegates with both params")
        void priceDescSort_withBrand_delegatesBothParams() {
            long categoryId = 12L;
            int pageNum = 0, pageSize = 5;
            List<Product> page = List.of(
                    product(3, "Amul Ghee", categoryId, BigDecimal.valueOf(650), "Amul"),
                    product(1, "Amul Milk", categoryId, BigDecimal.valueOf(65), "Amul")
            );

            when(productRepository.findByCategoryWithFilters(
                    eq(categoryId), eq(ProductSortOption.PRICE_DESC), eq("Amul"), eq(pageSize), eq(0L)))
                    .thenReturn(Flux.fromIterable(page));
            when(productRepository.countByCategoryWithFilters(eq(categoryId), eq("Amul")))
                    .thenReturn(Mono.just(2L));

            StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, ProductSortOption.PRICE_DESC, "Amul"))
                    .assertNext(response -> {
                        assertThat(response.getContent()).hasSize(2);
                        assertThat(response.getContent().get(0).getBasePrice()).isEqualByComparingTo("650");
                        assertThat(response.getContent().get(1).getBasePrice()).isEqualByComparingTo("65");
                        assertThat(response.getMeta().getTotalElements()).isEqualTo(2);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Brand filter only, no sort — delegates with null sort and brand value")
        void brandFilterOnly_noSort_delegatesNullSort() {
            long categoryId = 13L;
            int pageNum = 0, pageSize = 10;
            List<Product> page = List.of(
                    product(4, "Tata Paneer", categoryId, BigDecimal.valueOf(120), "Tata"),
                    product(5, "Tata Curd",   categoryId, BigDecimal.valueOf(55),  "Tata")
            );

            when(productRepository.findByCategoryWithFilters(
                    eq(categoryId), isNull(), eq("Tata"), eq(pageSize), eq(0L)))
                    .thenReturn(Flux.fromIterable(page));
            when(productRepository.countByCategoryWithFilters(eq(categoryId), eq("Tata")))
                    .thenReturn(Mono.just(2L));

            StepVerifier.create(catalogService.getProductsByCategory(categoryId, pageNum, pageSize, null, "Tata"))
                    .assertNext(response -> {
                        assertThat(response.getContent()).hasSize(2);
                        assertThat(response.getContent())
                                .allSatisfy(p -> assertThat(p.getBrand()).isEqualTo("Tata"));
                        assertThat(response.getMeta().getTotalElements()).isEqualTo(2);
                    })
                    .verifyComplete();
        }
    }
}
