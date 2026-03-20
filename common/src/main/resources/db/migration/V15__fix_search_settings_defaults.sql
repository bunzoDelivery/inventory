-- V15: Fix search settings — additive updates for existing envs + seed for fresh envs.
--
-- Uses JSON_ARRAY_APPEND so admin-customised values are never lost.
-- INSERT IGNORE handles fresh databases where no rows exist yet.

-- ============================================
-- 1. Additive fixes for EXISTING environments
-- ============================================

-- searchable_attributes: add categoryName if missing
UPDATE search_settings
SET setting_value = JSON_ARRAY_APPEND(setting_value, '$', 'categoryName'),
    version = version + 1,
    updated_by = 'migration_v15'
WHERE setting_key = 'searchable_attributes'
  AND NOT JSON_CONTAINS(setting_value, '"categoryName"');

-- searchable_attributes: add description if missing
UPDATE search_settings
SET setting_value = JSON_ARRAY_APPEND(setting_value, '$', 'description'),
    version = version + 1,
    updated_by = 'migration_v15'
WHERE setting_key = 'searchable_attributes'
  AND NOT JSON_CONTAINS(setting_value, '"description"');

-- sortable_attributes: replace legacy "priority" with "searchPriority"
UPDATE search_settings
SET setting_value = JSON_REPLACE(
      setting_value,
      REPLACE(JSON_UNQUOTE(JSON_SEARCH(setting_value, 'one', 'priority')), '"', ''),
      'searchPriority'
    ),
    version = version + 1,
    updated_by = 'migration_v15'
WHERE setting_key = 'sortable_attributes'
  AND JSON_CONTAINS(setting_value, '"priority"');

-- sortable_attributes: ensure searchPriority present even if "priority" was never there
UPDATE search_settings
SET setting_value = JSON_ARRAY_APPEND(setting_value, '$', 'searchPriority'),
    version = version + 1,
    updated_by = 'migration_v15'
WHERE setting_key = 'sortable_attributes'
  AND NOT JSON_CONTAINS(setting_value, '"searchPriority"');

-- ============================================
-- 2. Seed defaults for FRESH environments
-- ============================================

INSERT IGNORE INTO search_settings (setting_key, setting_value, description, version, updated_by)
VALUES
  ('searchable_attributes',
   '["name", "brand", "keywords", "categoryName", "description", "barcode"]',
   'Attributes to search in', 0, 'migration_v15'),
  ('filterable_attributes',
   '["storeIds", "isActive", "brand", "categoryId", "isBestseller"]',
   'Attributes to filter by', 0, 'migration_v15'),
  ('ranking_rules',
   '["words", "typo", "proximity", "attribute", "sort", "exactness"]',
   'Default ranking rules', 0, 'migration_v15'),
  ('sortable_attributes',
   '["price", "searchPriority"]',
   'Attributes to sort by', 0, 'migration_v15');
