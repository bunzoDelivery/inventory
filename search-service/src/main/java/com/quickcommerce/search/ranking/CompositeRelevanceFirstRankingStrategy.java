package com.quickcommerce.search.ranking;

import com.quickcommerce.search.config.SearchProperties;
import com.quickcommerce.search.model.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Blends Meilisearch relevance with catalog business fields. Formula and weights live here in one place;
 * external tuning via {@link SearchProperties.Ranking} (YAML {@code search.ranking.*}).
 *
 * <p>Relevance: {@code _rankingScore} when present; otherwise a position-based fallback preserving hit order.
 * Business: normalized order_count, search_priority (0–100), bestseller — combined with configurable sub-weights.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeRelevanceFirstRankingStrategy implements SearchRankingStrategy {

    private final SearchProperties searchProperties;

    @Override
    public List<ProductDocument> rank(List<ProductDocument> documents) {
        if (documents == null) {
            return List.of();
        }
        if (documents.size() <= 1) {
            return documents;
        }

        SearchProperties.Ranking cfg = searchProperties.getRanking();
        double[] rb = normalizePair(cfg.getRelevanceWeight(), cfg.getBusinessWeight());
        double relPortion = rb[0];
        double busPortion = rb[1];

        double[] bus = normalizeTriple(
                cfg.getOrderCountWeight(),
                cfg.getSearchPriorityWeight(),
                cfg.getBestsellerWeight());

        RankingContext ctx = RankingContext.fromDocuments(documents);
        int n = documents.size();

        record Scored(ProductDocument doc, int originalIndex, double score) {}

        List<Scored> scored = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ProductDocument doc = documents.get(i);
            double relevance = relevanceScore(doc, i, n);
            double business = businessScore(doc, ctx, bus[0], bus[1], bus[2]);
            double finalScore = relPortion * relevance + busPortion * business;
            scored.add(new Scored(doc, i, finalScore));
            if (log.isDebugEnabled() && i < 5) {
                log.debug("rank sku={} rel={} bus={} final={}", doc.getSku(), relevance, business, finalScore);
            }
        }

        // After composite score: cheaper first when tied, then preserve Meilisearch hit order
        scored.sort(Comparator.comparingDouble(Scored::score)
                .reversed()
                .thenComparing(s -> s.doc().getPrice(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(Scored::originalIndex));

        List<ProductDocument> out = new ArrayList<>(n);
        for (Scored s : scored) {
            out.add(s.doc());
        }
        return out;
    }

    private static double relevanceScore(ProductDocument doc, int indexInHitList, int hitCount) {
        if (doc.getRankingScore() != null) {
            return clamp01(doc.getRankingScore());
        }
        if (hitCount <= 0) {
            return 0.0;
        }
        // Preserve Meilisearch order: earlier hits score higher when API did not return _rankingScore
        return 1.0 - (indexInHitList / (double) hitCount);
    }

    private static double businessScore(ProductDocument doc, RankingContext ctx,
            double wOrder, double wPri, double wBest) {
        double maxOrder = Math.max(1, ctx.maxOrderCount());
        double normOrder = doc.getOrderCount() == null
                ? 0.0
                : Math.min(1.0, doc.getOrderCount() / maxOrder);

        double priRaw = doc.getSearchPriority() == null
                ? 0.0
                : Math.min(100.0, Math.max(0.0, doc.getSearchPriority()));
        double maxP = ctx.maxSearchPriority();
        double denom = maxP <= 0 ? 100.0 : (double) maxP;
        double normPri = Math.min(1.0, priRaw / denom);

        double best = Boolean.TRUE.equals(doc.getIsBestseller()) ? 1.0 : 0.0;

        return wOrder * normOrder + wPri * normPri + wBest * best;
    }

    private static double[] normalizePair(double a, double b) {
        double sum = a + b;
        if (sum <= 0) {
            return new double[] { 0.5, 0.5 };
        }
        return new double[] { a / sum, b / sum };
    }

    private static double[] normalizeTriple(double a, double b, double c) {
        double sum = a + b + c;
        if (sum <= 0) {
            return new double[] { 1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0 };
        }
        return new double[] { a / sum, b / sum, c / sum };
    }

    private static double clamp01(Double v) {
        if (v == null || v.isNaN()) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, v));
    }
}
