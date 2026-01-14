-- Add search-specific columns to products table
-- These columns support Meilisearch ranking and keyword matching

-- Add new columns for search functionality
ALTER TABLE products
ADD COLUMN search_keywords TEXT COMMENT 'Additional search keywords: Hindi terms, synonyms, misspellings (comma-separated)',
ADD COLUMN search_priority INT DEFAULT 0 COMMENT 'Global search ranking priority (0-100, higher = better)',
ADD COLUMN is_bestseller BOOLEAN DEFAULT FALSE COMMENT 'Bestseller flag for ranking boost',
ADD COLUMN view_count INT DEFAULT 0 COMMENT 'Product view count (passive popularity signal)',
ADD COLUMN order_count INT DEFAULT 0 COMMENT 'Times ordered (strong popularity signal)',
ADD COLUMN last_ordered_at TIMESTAMP NULL COMMENT 'Last order timestamp (for trending/recency)';

-- Add indexes for search performance
CREATE INDEX idx_search_priority ON products(search_priority DESC);
CREATE INDEX idx_bestseller ON products(is_bestseller);
CREATE INDEX idx_popularity ON products(order_count DESC, view_count DESC);
CREATE INDEX idx_last_ordered ON products(last_ordered_at);

-- Add full-text index for enhanced search_keywords
-- This complements the existing full-text index on name, description, tags
ALTER TABLE products
ADD FULLTEXT INDEX ft_search_keywords (search_keywords);
