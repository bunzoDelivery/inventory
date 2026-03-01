-- Create table for global search settings (ranking rules, attributes)
CREATE TABLE IF NOT EXISTS search_settings (
    setting_key VARCHAR(50) NOT NULL,
    setting_value JSON NOT NULL,
    description VARCHAR(255),
    version INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create table for search synonyms
CREATE TABLE IF NOT EXISTS search_synonyms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    term VARCHAR(255) NOT NULL,
    synonyms JSON NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    CONSTRAINT uk_search_synonym_term UNIQUE (term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for searching inside the JSON array (MySQL 8.0+ syntax)
CREATE INDEX idx_synonym_lookup ON search_synonyms( (CAST(synonyms AS CHAR(255) ARRAY)) );
