package com.quickcommerce.search.service;

import com.quickcommerce.search.dto.ProductResult;
import com.quickcommerce.search.model.ProductDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Service for ranking and sorting search results
 * Implements MVP ranking logic
 */
@Slf4j
@Service
public class RankingService {

    /**
     * Ranks search results according to MVP priority rules
     *
     * Priority order:
     * 1. In-stock (already filtered, all results are in-stock)
     * 2. Bestseller flag (descending)
     * 3. Search priority (descending)
     * 4. Order count / popularity (descending)
     * 5. Price (ascending - cheaper first as tie-breaker)
     *
     * @param documents List of product documents to rank
     * @return Sorted list of documents
     */
    public List<ProductDocument> rank(List<ProductDocument> documents) {
        log.debug("Ranking {} documents", documents.size());
        
        documents.sort(Comparator
                // 1. Bestseller first (nulls last)
                .comparing(ProductDocument::getIsBestseller, 
                          Comparator.nullsLast(Comparator.reverseOrder()))
                // 2. Higher priority first (nulls last)
                .thenComparing(ProductDocument::getPriority, 
                              Comparator.nullsLast(Comparator.reverseOrder()))
                // 3. More orders first (popularity, nulls last)
                .thenComparing(ProductDocument::getOrderCount, 
                              Comparator.nullsLast(Comparator.reverseOrder()))
                // 4. Lower price first (tie-breaker, nulls last)
                .thenComparing(ProductDocument::getPrice, 
                              Comparator.nullsLast(Comparator.naturalOrder()))
        );
        
        return documents;
    }
}
