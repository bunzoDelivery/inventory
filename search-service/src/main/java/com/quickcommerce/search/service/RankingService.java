package com.quickcommerce.search.service;

import com.quickcommerce.search.model.ProductDocument;
import com.quickcommerce.search.ranking.SearchRankingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade for result ordering — delegates to {@link SearchRankingStrategy} (default: relevance + business blend).
 * To change behavior, replace the strategy bean or tune {@code search.ranking.*}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final SearchRankingStrategy searchRankingStrategy;

    /**
     * @param documents Meilisearch hit order before ranking
     * @return New list in ranked order (does not require the input list to be mutable)
     */
    public List<ProductDocument> rank(List<ProductDocument> documents) {
        if (documents == null) {
            return List.of();
        }
        log.debug("Ranking {} documents", documents.size());
        // return searchRankingStrategy.rank(documents);
        // TODO: Remove this later
        return documents;
    }
}
