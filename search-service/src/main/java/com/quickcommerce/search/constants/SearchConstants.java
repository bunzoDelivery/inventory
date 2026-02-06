package com.quickcommerce.search.constants;

/**
 * Constants for search service
 */
public final class SearchConstants {

    private SearchConstants() {
        // Utility class
    }

    /**
     * API path constants
     */
    public static final class Paths {
        public static final String SEARCH_BASE = "/search";
        public static final String ADMIN_BASE = "/admin/search";
        public static final String HEALTH = "/actuator/health";
        public static final String METRICS = "/actuator/metrics";
    }

    /**
     * Configuration keys
     */
    public static final class ConfigKeys {
        public static final String RANKING_RULES = "ranking_rules";
        public static final String SEARCHABLE_ATTRIBUTES = "searchable_attributes";
        public static final String FILTERABLE_ATTRIBUTES = "filterable_attributes";
        public static final String SORTABLE_ATTRIBUTES = "sortable_attributes";
        public static final String STOP_WORDS = "stop_words";
    }

    /**
     * Circuit breaker names
     */
    public static final class CircuitBreakers {
        public static final String INVENTORY_SERVICE = "inventoryService";
        public static final String CATALOG_SERVICE = "catalogService";
    }

    /**
     * Rate limiter names
     */
    public static final class RateLimiters {
        public static final String SEARCH_ENDPOINT = "searchEndpoint";
    }

    /**
     * Cache names
     */
    public static final class Caches {
        public static final String SEARCH_RESULTS = "searchResults";
        public static final String SYNONYMS = "synonyms";
        public static final String SETTINGS = "settings";
    }

    /**
     * Default values
     */
    public static final class Defaults {
        public static final int PAGE_SIZE = 20;
        public static final int PAGE = 1;
        public static final int MAX_RESULT_LIMIT = 100;
        public static final int CANDIDATE_LIMIT = 80;
    }
}
