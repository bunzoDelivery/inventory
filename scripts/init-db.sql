-- Database initialization script for Inventory Service
-- This script creates the necessary tables and indexes

USE quickcommerce;

-- Core inventory table
CREATE TABLE IF NOT EXISTS inventory_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    product_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL DEFAULT 1, -- Lusaka store
    current_stock INT NOT NULL DEFAULT 0,
    reserved_stock INT NOT NULL DEFAULT 0,
    safety_stock INT NOT NULL DEFAULT 0,
    max_stock INT NOT NULL DEFAULT 0,
    unit_cost DECIMAL(10,2),
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0, -- For optimistic locking
    INDEX idx_sku (sku),
    INDEX idx_product_id (product_id),
    INDEX idx_store_id (store_id),
    INDEX idx_current_stock (current_stock),
    INDEX idx_low_stock (current_stock, safety_stock)
);

-- Stock movements audit trail
CREATE TABLE IF NOT EXISTS inventory_movements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_item_id BIGINT NOT NULL,
    movement_type ENUM('INBOUND', 'OUTBOUND', 'RESERVE', 'UNRESERVE', 'ADJUSTMENT') NOT NULL,
    quantity INT NOT NULL,
    reference_type ENUM('PURCHASE', 'SALE', 'RETURN', 'ADJUSTMENT', 'RESERVATION') NOT NULL,
    reference_id VARCHAR(100),
    reason VARCHAR(255),
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE CASCADE,
    INDEX idx_inventory_item_id (inventory_item_id),
    INDEX idx_movement_type (movement_type),
    INDEX idx_created_at (created_at),
    INDEX idx_reference_id (reference_id)
);

-- Stock reservations for checkout
CREATE TABLE IF NOT EXISTS stock_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id VARCHAR(100) UNIQUE NOT NULL,
    inventory_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    customer_id BIGINT,
    order_id VARCHAR(100),
    expires_at TIMESTAMP NOT NULL,
    status ENUM('ACTIVE', 'CONFIRMED', 'EXPIRED', 'CANCELLED') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE CASCADE,
    INDEX idx_reservation_id (reservation_id),
    INDEX idx_inventory_item_id (inventory_item_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_status (status),
    INDEX idx_customer_id (customer_id),
    INDEX idx_order_id (order_id)
);

-- Low stock alerts configuration
CREATE TABLE IF NOT EXISTS stock_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_item_id BIGINT NOT NULL,
    alert_threshold INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE CASCADE,
    UNIQUE KEY unique_item_alert (inventory_item_id),
    INDEX idx_is_active (is_active)
);

-- Insert sample data for testing
INSERT INTO inventory_items (sku, product_id, store_id, current_stock, reserved_stock, safety_stock, max_stock, unit_cost) VALUES
('SKU001', 1001, 1, 50, 0, 10, 100, 15.99),
('SKU002', 1002, 1, 25, 0, 5, 50, 8.50),
('SKU003', 1003, 1, 100, 0, 20, 200, 12.75),
('SKU004', 1004, 1, 5, 0, 15, 30, 25.00),
('SKU005', 1005, 1, 75, 0, 10, 150, 6.99),
('SKU006', 1006, 1, 0, 0, 5, 25, 18.50),
('SKU007', 1007, 1, 30, 0, 8, 60, 9.99),
('SKU008', 1008, 1, 200, 0, 25, 300, 4.50),
('SKU009', 1009, 1, 15, 0, 12, 40, 22.75),
('SKU010', 1010, 1, 80, 0, 15, 120, 14.25);

-- Insert sample stock alerts
INSERT INTO stock_alerts (inventory_item_id, alert_threshold, is_active) VALUES
(1, 10, TRUE),
(2, 5, TRUE),
(3, 20, TRUE),
(4, 15, TRUE),
(5, 10, TRUE),
(6, 5, TRUE),
(7, 8, TRUE),
(8, 25, TRUE),
(9, 12, TRUE),
(10, 15, TRUE);

-- Insert sample stock movements
INSERT INTO inventory_movements (inventory_item_id, movement_type, quantity, reference_type, reference_id, reason, created_at) VALUES
(1, 'INBOUND', 50, 'PURCHASE', 'PO-001', 'Initial stock', NOW() - INTERVAL 1 DAY),
(2, 'INBOUND', 25, 'PURCHASE', 'PO-001', 'Initial stock', NOW() - INTERVAL 1 DAY),
(3, 'INBOUND', 100, 'PURCHASE', 'PO-001', 'Initial stock', NOW() - INTERVAL 1 DAY),
(4, 'INBOUND', 20, 'PURCHASE', 'PO-002', 'Initial stock', NOW() - INTERVAL 1 DAY),
(5, 'INBOUND', 75, 'PURCHASE', 'PO-002', 'Initial stock', NOW() - INTERVAL 1 DAY),
(6, 'INBOUND', 10, 'PURCHASE', 'PO-003', 'Initial stock', NOW() - INTERVAL 1 DAY),
(7, 'INBOUND', 30, 'PURCHASE', 'PO-003', 'Initial stock', NOW() - INTERVAL 1 DAY),
(8, 'INBOUND', 200, 'PURCHASE', 'PO-004', 'Initial stock', NOW() - INTERVAL 1 DAY),
(9, 'INBOUND', 15, 'PURCHASE', 'PO-004', 'Initial stock', NOW() - INTERVAL 1 DAY),
(10, 'INBOUND', 80, 'PURCHASE', 'PO-005', 'Initial stock', NOW() - INTERVAL 1 DAY);

-- Update stock for SKU004 to trigger low stock alert
UPDATE inventory_items SET current_stock = 5 WHERE sku = 'SKU004';

-- Create a stored procedure for cleaning up expired reservations
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS CleanupExpiredReservations()
BEGIN
    UPDATE stock_reservations 
    SET status = 'EXPIRED' 
    WHERE status = 'ACTIVE' AND expires_at < NOW();
END //
DELIMITER ;

-- Create an event scheduler for automatic cleanup (if enabled)
-- SET GLOBAL event_scheduler = ON;

-- CREATE EVENT IF NOT EXISTS cleanup_expired_reservations
-- ON SCHEDULE EVERY 1 MINUTE
-- DO
--   CALL CleanupExpiredReservations();
