-- Create product-store assortment junction table for multi-store support
-- This table tracks which products are available in which stores
-- Required for search service to filter products by store (storeIds[] in Meilisearch)

CREATE TABLE IF NOT EXISTS product_store_assortment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT 'Reference to product',
    store_id BIGINT NOT NULL COMMENT 'Reference to store/dark store',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Whether product is active in this store',
    local_priority INT DEFAULT 0 COMMENT 'Store-specific ranking priority (0-100)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Foreign keys
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE,
    
    -- Unique constraint: one product can only appear once per store
    UNIQUE KEY uk_product_store (product_id, store_id),
    
    -- Indexes for performance
    INDEX idx_store_active (store_id, is_active),
    INDEX idx_product (product_id),
    INDEX idx_priority (local_priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Product availability by store (assortment management)';
