package com.quickcommerce.search.ranking;

import com.quickcommerce.search.model.ProductDocument;

import java.util.List;

/**
 * Per-query stats for normalizing business signals (max within current result set).
 */
public record RankingContext(int maxOrderCount, int maxSearchPriority) {

    public static RankingContext fromDocuments(List<ProductDocument> docs) {
        int maxOrder = 0;
        int maxPri = 0;
        for (ProductDocument d : docs) {
            if (d.getOrderCount() != null) {
                maxOrder = Math.max(maxOrder, d.getOrderCount());
            }
            if (d.getSearchPriority() != null) {
                maxPri = Math.max(maxPri, d.getSearchPriority());
            }
        }
        return new RankingContext(maxOrder, maxPri);
    }
}
