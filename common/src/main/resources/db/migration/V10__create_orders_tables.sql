CREATE TABLE IF NOT EXISTS customer_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_uuid VARCHAR(36) UNIQUE NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    store_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'ZMW',
    delivery_address VARCHAR(500),
    delivery_lat DECIMAL(10, 7),
    delivery_lng DECIMAL(10, 7),
    delivery_phone VARCHAR(20),
    delivery_notes VARCHAR(255),
    delivery_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    cancelled_reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    shipping_address_id BIGINT,
    idempotency_key VARCHAR(64) UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    sku VARCHAR(50) NOT NULL,
    qty INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    sub_total DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES customer_orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Idempotent index creation (for re-runs after partial failure)
-- Use stored procedure to check and create indexes only if they don't exist
DELIMITER //
CREATE PROCEDURE CreateOrderIndexesIfNotExists()
BEGIN
    -- Check and create idx_orders_customer_id
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
        AND table_name = 'customer_orders' 
        AND index_name = 'idx_orders_customer_id'
    ) THEN
        CREATE INDEX idx_orders_customer_id ON customer_orders(customer_id);
    END IF;

    -- Check and create idx_orders_status
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
        AND table_name = 'customer_orders' 
        AND index_name = 'idx_orders_status'
    ) THEN
        CREATE INDEX idx_orders_status ON customer_orders(status);
    END IF;

    -- Check and create idx_orders_created_at
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
        AND table_name = 'customer_orders' 
        AND index_name = 'idx_orders_created_at'
    ) THEN
        CREATE INDEX idx_orders_created_at ON customer_orders(created_at);
    END IF;

    -- Check and create idx_orders_status_created_at
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
        AND table_name = 'customer_orders' 
        AND index_name = 'idx_orders_status_created_at'
    ) THEN
        CREATE INDEX idx_orders_status_created_at ON customer_orders(status, created_at);
    END IF;
END //
DELIMITER ;

CALL CreateOrderIndexesIfNotExists();
DROP PROCEDURE CreateOrderIndexesIfNotExists;
