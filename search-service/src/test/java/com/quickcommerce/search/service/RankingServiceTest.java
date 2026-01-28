package com.quickcommerce.search.service;

import com.quickcommerce.search.model.ProductDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingServiceTest {

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService();
    }

    @Test
    void rank_shouldSortByBestsellerFirst() {
        ProductDocument p1 = createProduct(1L, "Normal", false, 0, 100);
        ProductDocument p2 = createProduct(2L, "Bestseller", true, 0, 100);

        List<ProductDocument> results = rankingService.rank(new ArrayList<>(List.of(p1, p2)));

        assertEquals(2L, results.get(0).getId()); // Bestseller first
        assertEquals(1L, results.get(1).getId());
    }

    @Test
    void rank_shouldSortBySearchPrioritySecondly() {
        ProductDocument p1 = createProduct(1L, "Low Priority", false, 10, 100);
        ProductDocument p2 = createProduct(2L, "High Priority", false, 20, 100);

        List<ProductDocument> results = rankingService.rank(new ArrayList<>(List.of(p1, p2)));

        assertEquals(2L, results.get(0).getId()); // Higher priority first
        assertEquals(1L, results.get(1).getId());
    }

    @Test
    void rank_shouldSortByPopularityOrderCountThirdly() {
        ProductDocument p1 = createProduct(1L, "Less Popular", false, 0, 10);
        ProductDocument p2 = createProduct(2L, "More Popular", false, 0, 50);

        List<ProductDocument> results = rankingService.rank(new ArrayList<>(List.of(p1, p2)));

        assertEquals(2L, results.get(0).getId()); // More orders first
        assertEquals(1L, results.get(1).getId());
    }

    @Test
    void rank_shouldSortByPriceAscendingLastly() {
        ProductDocument p1 = createProduct(1L, "Expensive", false, 0, 0);
        p1.setPrice(BigDecimal.valueOf(100.0));

        ProductDocument p2 = createProduct(2L, "Cheap", false, 0, 0);
        p2.setPrice(BigDecimal.valueOf(50.0));

        List<ProductDocument> results = rankingService.rank(new ArrayList<>(List.of(p1, p2)));

        assertEquals(2L, results.get(0).getId()); // Cheaper first
        assertEquals(1L, results.get(1).getId());
    }

    private ProductDocument createProduct(Long id, String name, boolean isBestseller, int searchPriority,
            int orderCount) {
        return ProductDocument.builder()
                .id(id)
                .name(name)
                .isBestseller(isBestseller)
                .searchPriority(searchPriority)
                .orderCount(orderCount)
                .build();
    }
}
