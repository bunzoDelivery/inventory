-- Create stores table for dark store management with geospatial support
CREATE TABLE IF NOT EXISTS stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    latitude DECIMAL(10, 8) NOT NULL COMMENT 'Store GPS latitude',
    longitude DECIMAL(11, 8) NOT NULL COMMENT 'Store GPS longitude',
    serviceable_radius_km INT DEFAULT 5 COMMENT 'Delivery radius in kilometers',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_active (is_active),
    INDEX idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



-- Add foreign key constraint to inventory_items table
-- Safely add foreign key constraint only if it doesn't exist
DROP PROCEDURE IF EXISTS AddInventoryStoreFK;
DELIMITER //
CREATE PROCEDURE AddInventoryStoreFK()
BEGIN
    IF NOT EXISTS (
        SELECT NULL
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'inventory_items'
        AND CONSTRAINT_NAME = 'fk_inventory_store'
    ) THEN
        ALTER TABLE inventory_items
        ADD CONSTRAINT fk_inventory_store
        FOREIGN KEY (store_id) REFERENCES stores(id);
    END IF;
END //
DELIMITER ;

CALL AddInventoryStoreFK();
DROP PROCEDURE AddInventoryStoreFK;
