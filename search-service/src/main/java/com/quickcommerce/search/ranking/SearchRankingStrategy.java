package com.quickcommerce.search.ranking;

import com.quickcommerce.search.model.ProductDocument;

import java.util.List;

/**
 * Pluggable search result ordering. Default: relevance-first composite — swap implementation
 * (e.g. pure lexical, ML reranker) via a Spring {@code @Bean} if needed.
 */
public interface SearchRankingStrategy {

    /**
     * @param documents Meilisearch hit order; may be mutated and returned (same as legacy {@code RankingService}).
     */
    List<ProductDocument> rank(List<ProductDocument> documents);
}
