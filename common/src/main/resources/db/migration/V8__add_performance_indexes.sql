-- Add Performance Indexes for Quick Commerce Product Service
-- This migration adds composite and covering indexes for frequently accessed queries

-- ============================================
-- INVENTORY INDEXES
-- ============================================

-- Composite index for store + SKU lookups with stock info
-- Covers: findByStoreIdAndSku, findByStoreIdAndSkuIn queries
CREATE INDEX idx_inventory_store_sku_stock 
ON inventory_items(store_id, sku, current_stock);

-- Index for low stock queries by store
-- Covers: findLowStockItems query (current_stock <= safety_stock)
CREATE INDEX idx_inventory_low_stock 
ON inventory_items(store_id, current_stock, safety_stock);

-- ============================================
-- RESERVATION INDEXES
-- ============================================

-- Index for active reservation cleanup (scheduled task)
-- Filtered index for better performance on the cleanup query
CREATE INDEX idx_reservations_active_expires 
ON stock_reservations(status, expires_at);

-- Additional index for reservation lookup by ID
CREATE INDEX idx_reservations_item_status
ON stock_reservations(inventory_item_id, status);

-- ============================================
-- PRODUCT INDEXES
-- ============================================

-- Composite index for active/available products
-- Covers: product listing and search queries
CREATE INDEX idx_products_active_available 
ON products(is_active, is_available, name);

-- Index for popular products (bestsellers, trending)
-- Covers: ORDER BY order_count, view_count queries
CREATE INDEX idx_products_popularity 
ON products(order_count DESC, view_count DESC);

-- Index for product search priority
CREATE INDEX idx_products_search_priority
ON products(search_priority DESC, is_bestseller DESC);

-- Index for product by category lookups
CREATE INDEX idx_products_category_active
ON products(category_id, is_active, is_available);

-- ============================================
-- CATEGORY INDEXES  
-- ============================================

-- Index for parent-child category relationships
CREATE INDEX idx_categories_parent_order
ON categories(parent_id, display_order, is_active);

-- ============================================
-- STOCK MOVEMENT INDEXES (AUDIT)
-- ============================================

-- Index for audit trail queries
CREATE INDEX idx_movements_item_created
ON inventory_movements(inventory_item_id, created_at DESC);

-- Index for movement type analysis
CREATE INDEX idx_movements_type_created
ON inventory_movements(movement_type, created_at DESC);
