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
ALTER TABLE inventory_items
ADD CONSTRAINT fk_inventory_store
FOREIGN KEY (store_id) REFERENCES stores(id);
