-- Add performance indexes for search tables
-- This migration adds indexes for search_synonyms and search_settings tables

-- ============================================
-- SEARCH SYNONYMS INDEXES
-- ============================================

-- Index for active synonym lookups (most common query)
CREATE INDEX idx_search_synonyms_active 
ON search_synonyms(is_active, term);

-- Index for audit tracking (updated_at queries)
CREATE INDEX idx_search_synonyms_updated
ON search_synonyms(updated_at DESC);

-- ============================================
-- SEARCH SETTINGS INDEXES
-- ============================================

-- Index for audit tracking on settings
CREATE INDEX idx_search_settings_updated
ON search_settings(updated_at DESC);

-- Index for version-based optimistic locking queries
CREATE INDEX idx_search_settings_version
ON search_settings(version);
