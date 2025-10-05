-- Initial schema for inventory management system
-- Tables: inventory_items, stock_reservations, stock_movements (inventory_movements), stock_alerts

-- ============================================
-- TABLE: inventory_items
-- Purpose: Track stock levels for each SKU in each store
-- ============================================
CREATE TABLE IF NOT EXISTS inventory_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(255) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL COMMENT 'Foreign key to products table (will be created in catalog module)',
    store_id BIGINT NOT NULL COMMENT 'Foreign key to stores table',
    current_stock INT NOT NULL DEFAULT 0 COMMENT 'Total physical stock available',
    reserved_stock INT NOT NULL DEFAULT 0 COMMENT 'Stock reserved for pending orders',
    safety_stock INT NOT NULL DEFAULT 0 COMMENT 'Minimum stock threshold for alerts',
    max_stock INT COMMENT 'Maximum stock capacity',
    unit_cost DECIMAL(10, 2) COMMENT 'Cost per unit',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_sku (sku),
    INDEX idx_product (product_id),
    INDEX idx_store (store_id),
    INDEX idx_store_sku (store_id, sku),
    INDEX idx_low_stock (current_stock, safety_stock),
    INDEX idx_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Inventory stock tracking';

-- ============================================
-- TABLE: stock_reservations
-- Purpose: Track stock reservations during checkout process
-- TTL: 15 minutes (enforced by application)
-- ============================================
CREATE TABLE IF NOT EXISTS stock_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id VARCHAR(255) NOT NULL UNIQUE COMMENT 'Business key for reservation',
    inventory_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    customer_id BIGINT NOT NULL,
    order_id VARCHAR(255) NOT NULL COMMENT 'Associated order ID',
    expires_at TIMESTAMP NOT NULL COMMENT 'Reservation expiry time (15 min TTL)',
    status VARCHAR(50) NOT NULL COMMENT 'ACTIVE, CONFIRMED, CANCELLED, EXPIRED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
    INDEX idx_reservation_id (reservation_id),
    INDEX idx_inventory_item (inventory_item_id),
    INDEX idx_customer (customer_id),
    INDEX idx_order (order_id),
    INDEX idx_status (status),
    INDEX idx_expires_at (expires_at),
    INDEX idx_status_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Stock reservation tracking';

-- ============================================
-- TABLE: inventory_movements (stock_movements)
-- Purpose: Audit trail for all stock changes
-- ============================================
CREATE TABLE IF NOT EXISTS inventory_movements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_item_id BIGINT NOT NULL,
    movement_type VARCHAR(50) NOT NULL COMMENT 'INBOUND, OUTBOUND, RESERVE, UNRESERVE, ADJUSTMENT',
    quantity INT NOT NULL,
    reference_type VARCHAR(50) COMMENT 'PURCHASE, SALE, RESERVATION, TRANSFER, ADJUSTMENT',
    reference_id VARCHAR(255) COMMENT 'Reference to external entity (order_id, reservation_id, etc)',
    reason VARCHAR(255) COMMENT 'Human-readable reason for movement',
    created_by VARCHAR(255) COMMENT 'User or system that created movement',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
    INDEX idx_inventory_item (inventory_item_id),
    INDEX idx_movement_type (movement_type),
    INDEX idx_reference (reference_type, reference_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Inventory movement audit trail';

-- ============================================
-- TABLE: stock_alerts
-- Purpose: Track low stock alerts and notifications
-- ============================================
CREATE TABLE IF NOT EXISTS stock_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_item_id BIGINT NOT NULL,
    alert_type VARCHAR(50) NOT NULL COMMENT 'LOW_STOCK, OUT_OF_STOCK, OVERSTOCKED',
    threshold_value INT COMMENT 'Threshold that triggered alert',
    current_value INT COMMENT 'Current stock value',
    severity VARCHAR(20) NOT NULL COMMENT 'INFO, WARNING, CRITICAL',
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
    INDEX idx_inventory_item (inventory_item_id),
    INDEX idx_alert_type (alert_type),
    INDEX idx_severity (severity),
    INDEX idx_resolved (is_resolved),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Stock alert tracking';

-- ============================================
-- Initial Data (Optional - for development)
-- ============================================
-- Note: Store data will be inserted by V2 migration
-- Sample inventory items can be inserted here for development/testing

-- Example: INSERT INTO inventory_items (sku, product_id, store_id, current_stock, reserved_stock, safety_stock)
-- VALUES ('SKU001', 101, 1, 100, 0, 10);
