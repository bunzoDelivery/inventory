-- Change products.images from TEXT to JSON for structured r2Key storage
-- Idempotent: safe to re-run if flyway_schema_history entry is missing

DELIMITER //
CREATE PROCEDURE MigrateImagesToJson()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name   = 'products'
          AND column_name  = 'images'
          AND data_type   != 'json'
    ) THEN
        ALTER TABLE products MODIFY COLUMN images JSON;
    END IF;
END //
DELIMITER ;

CALL MigrateImagesToJson();
DROP PROCEDURE MigrateImagesToJson;
