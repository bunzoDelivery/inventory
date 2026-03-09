-- V13: Payment integrity improvements
-- 1. Add optimistic-locking version column to customer_orders
-- 2. Add UNIQUE constraint on payment_attempts(order_uuid) to prevent duplicate pushes
-- 3. Add index on customer_orders(airtel_transaction_id) for failsafe scheduler performance
-- 4. Expand airtel_transaction_id from VARCHAR(50) to VARCHAR(100)
-- All operations are idempotent (stored procedure + information_schema guard pattern).

-- ─── Step 1: Add version column to customer_orders ────────────────────────────
DELIMITER //
CREATE PROCEDURE AddVersionColumnIfNotExists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_orders'
          AND column_name = 'version'
    ) THEN
        ALTER TABLE customer_orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;
END //
DELIMITER ;

CALL AddVersionColumnIfNotExists();
DROP PROCEDURE AddVersionColumnIfNotExists;

-- ─── Step 2: Expand airtel_transaction_id to VARCHAR(100) ─────────────────────
DELIMITER //
CREATE PROCEDURE ExpandAirtelTransactionIdIfNeeded()
BEGIN
    DECLARE col_length INT DEFAULT 0;
    SELECT CHARACTER_MAXIMUM_LENGTH INTO col_length
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'customer_orders'
      AND column_name = 'airtel_transaction_id';

    IF col_length IS NOT NULL AND col_length < 100 THEN
        ALTER TABLE customer_orders MODIFY COLUMN airtel_transaction_id VARCHAR(100) NULL;
    END IF;
END //
DELIMITER ;

CALL ExpandAirtelTransactionIdIfNeeded();
DROP PROCEDURE ExpandAirtelTransactionIdIfNeeded;

-- ─── Step 3: Add index on customer_orders(airtel_transaction_id) ───────────────
DELIMITER //
CREATE PROCEDURE AddAirtelTxIdIndexIfNotExists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_orders'
          AND index_name = 'idx_co_airtel_txid'
    ) THEN
        CREATE INDEX idx_co_airtel_txid ON customer_orders(airtel_transaction_id);
    END IF;
END //
DELIMITER ;

CALL AddAirtelTxIdIndexIfNotExists();
DROP PROCEDURE AddAirtelTxIdIndexIfNotExists;

-- ─── Step 4: Add UNIQUE constraint on payment_attempts(order_uuid) ────────────
-- Prevents duplicate STK pushes for the same order (concurrent /pay calls).
DELIMITER //
CREATE PROCEDURE AddPaymentAttemptsUniqueIfNotExists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_attempts'
          AND index_name = 'uq_pa_order_uuid'
    ) THEN
        ALTER TABLE payment_attempts ADD CONSTRAINT uq_pa_order_uuid UNIQUE (order_uuid);
    END IF;
END //
DELIMITER ;

CALL AddPaymentAttemptsUniqueIfNotExists();
DROP PROCEDURE AddPaymentAttemptsUniqueIfNotExists;
