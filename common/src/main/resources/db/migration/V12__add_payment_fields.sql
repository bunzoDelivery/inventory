-- V11: Add Airtel Money payment fields to customer_orders and create payment_attempts audit table
-- Idempotent: safe to run manually even if flyway_schema_history entry is missing

-- ─── Step 1: Idempotently add columns to customer_orders ─────────────────────
DELIMITER //
CREATE PROCEDURE AddPaymentColumnsIfNotExists()
BEGIN
    -- Add payment_phone column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_orders'
          AND column_name = 'payment_phone'
    ) THEN
        ALTER TABLE customer_orders ADD COLUMN payment_phone VARCHAR(20) NULL;
    END IF;

    -- Add airtel_transaction_id column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_orders'
          AND column_name = 'airtel_transaction_id'
    ) THEN
        ALTER TABLE customer_orders ADD COLUMN airtel_transaction_id VARCHAR(50) NULL;
    END IF;
END //
DELIMITER ;

CALL AddPaymentColumnsIfNotExists();
DROP PROCEDURE AddPaymentColumnsIfNotExists;

-- ─── Step 2: Create payment_attempts table ────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_attempts (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_uuid     VARCHAR(36)  NOT NULL,
    payment_phone  VARCHAR(20)  NOT NULL,
    airtel_ref     VARCHAR(50)  NULL,
    status         VARCHAR(20)  NOT NULL,   -- INITIATED, SUCCESS, FAILED, TIMEOUT
    failure_reason VARCHAR(255) NULL,
    initiated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at    TIMESTAMP    NULL,
    raw_webhook    TEXT         NULL        -- raw Airtel webhook body for reconciliation
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Step 3: Idempotent indexes on payment_attempts ──────────────────────────
DELIMITER //
CREATE PROCEDURE AddPaymentAttemptsIndexesIfNotExists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_attempts'
          AND index_name = 'idx_pa_order_uuid'
    ) THEN
        CREATE INDEX idx_pa_order_uuid ON payment_attempts(order_uuid);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_attempts'
          AND index_name = 'idx_pa_airtel_ref'
    ) THEN
        CREATE INDEX idx_pa_airtel_ref ON payment_attempts(airtel_ref);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_attempts'
          AND index_name = 'idx_pa_status'
    ) THEN
        CREATE INDEX idx_pa_status ON payment_attempts(status);
    END IF;
END //
DELIMITER ;

CALL AddPaymentAttemptsIndexesIfNotExists();
DROP PROCEDURE AddPaymentAttemptsIndexesIfNotExists;
