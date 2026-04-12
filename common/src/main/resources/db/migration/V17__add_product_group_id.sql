-- Add group_id to connect variant products (idempotent)
DROP PROCEDURE IF EXISTS add_product_group_id;

DELIMITER $$
CREATE PROCEDURE add_product_group_id()
BEGIN
    -- Add column only if it does not already exist
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name   = 'products'
          AND column_name  = 'group_id'
    ) THEN
        ALTER TABLE products
            ADD COLUMN group_id VARCHAR(255)
                COMMENT 'Used to group variant products together (e.g. sizes)';
    END IF;

    -- Add index only if it does not already exist
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'products'
          AND index_name   = 'idx_group_id'
    ) THEN
        CREATE INDEX idx_group_id ON products(group_id);
    END IF;
END$$
DELIMITER ;

CALL add_product_group_id();
DROP PROCEDURE IF EXISTS add_product_group_id;
