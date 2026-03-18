-- V14: Payment gateway abstraction
-- Rename vendor-specific column names to generic equivalents so any payment
-- gateway (PawaPay, Airtel Direct, future providers) can be swapped via config.
--
-- Changes:
--   customer_orders:   airtel_transaction_id → gateway_transaction_id
--   payment_attempts:  airtel_ref            → gateway_ref
--   payment_attempts:  ADD gateway_used      (which adapter processed this attempt)
--   payment_attempts:  ADD mobile_network    (AIRTEL | MTN — which network was dialled)
-- All operations use the idempotent stored-procedure + information_schema guard pattern.

-- ─── 1. Rename airtel_transaction_id → gateway_transaction_id ─────────────────
DELIMITER //
CREATE PROCEDURE RenameAirtelTxIdColumn()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name   = 'customer_orders'
          AND column_name  = 'airtel_transaction_id'
    ) THEN
        ALTER TABLE customer_orders
            RENAME COLUMN airtel_transaction_id TO gateway_transaction_id;
    END IF;
END //
DELIMITER ;
CALL RenameAirtelTxIdColumn();
DROP PROCEDURE RenameAirtelTxIdColumn;

-- ─── 2. Rename airtel_ref → gateway_ref in payment_attempts ───────────────────
DELIMITER //
CREATE PROCEDURE RenameAirtelRefColumn()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name   = 'payment_attempts'
          AND column_name  = 'airtel_ref'
    ) THEN
        ALTER TABLE payment_attempts
            RENAME COLUMN airtel_ref TO gateway_ref;
    END IF;
END //
DELIMITER ;
CALL RenameAirtelRefColumn();
DROP PROCEDURE RenameAirtelRefColumn;

-- ─── 3. Add gateway_used to payment_attempts ──────────────────────────────────
DELIMITER //
CREATE PROCEDURE AddGatewayUsedColumn()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name   = 'payment_attempts'
          AND column_name  = 'gateway_used'
    ) THEN
        ALTER TABLE payment_attempts
            ADD COLUMN gateway_used VARCHAR(20) NOT NULL DEFAULT 'AIRTEL_DIRECT'
            COMMENT 'Which payment gateway adapter processed this attempt: PAWAPAY | AIRTEL_DIRECT';
    END IF;
END //
DELIMITER ;
CALL AddGatewayUsedColumn();
DROP PROCEDURE AddGatewayUsedColumn;

-- ─── 4. Add mobile_network to payment_attempts ────────────────────────────────
DELIMITER //
CREATE PROCEDURE AddMobileNetworkColumn()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name   = 'payment_attempts'
          AND column_name  = 'mobile_network'
    ) THEN
        ALTER TABLE payment_attempts
            ADD COLUMN mobile_network VARCHAR(10) NULL
            COMMENT 'Mobile network operator dialled: AIRTEL | MTN';
    END IF;
END //
DELIMITER ;
CALL AddMobileNetworkColumn();
DROP PROCEDURE AddMobileNetworkColumn;

-- ─── 5. Rename index idx_co_airtel_txid → idx_co_gateway_txid ─────────────────
DELIMITER //
CREATE PROCEDURE RenameAirtelTxIdIndex()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'customer_orders'
          AND index_name   = 'idx_co_airtel_txid'
    ) THEN
        ALTER TABLE customer_orders
            RENAME INDEX idx_co_airtel_txid TO idx_co_gateway_txid;
    END IF;

    -- Create the index under its new name if it never existed under either name
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'customer_orders'
          AND index_name   = 'idx_co_gateway_txid'
    ) THEN
        CREATE INDEX idx_co_gateway_txid ON customer_orders(gateway_transaction_id);
    END IF;
END //
DELIMITER ;
CALL RenameAirtelTxIdIndex();
DROP PROCEDURE RenameAirtelTxIdIndex;

-- ─── 6. Rename index idx_pa_airtel_ref → idx_pa_gateway_ref ──────────────────
DELIMITER //
CREATE PROCEDURE RenameAirtelRefIndex()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'payment_attempts'
          AND index_name   = 'idx_pa_airtel_ref'
    ) THEN
        ALTER TABLE payment_attempts
            RENAME INDEX idx_pa_airtel_ref TO idx_pa_gateway_ref;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'payment_attempts'
          AND index_name   = 'idx_pa_gateway_ref'
    ) THEN
        CREATE INDEX idx_pa_gateway_ref ON payment_attempts(gateway_ref);
    END IF;
END //
DELIMITER ;
CALL RenameAirtelRefIndex();
DROP PROCEDURE RenameAirtelRefIndex;

-- ─── 7. Add index on payment_attempts(gateway_used, status) for failsafe query ─
-- The GenericPaymentFailsafeScheduler queries: status='INITIATED' AND initiated_at < cutoff
-- gateway_used is included for fast per-gateway routing in high-volume scenarios.
DELIMITER //
CREATE PROCEDURE AddFailsafeIndexIfNotExists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'payment_attempts'
          AND index_name   = 'idx_pa_status_initiated_at'
    ) THEN
        CREATE INDEX idx_pa_status_initiated_at ON payment_attempts(status, initiated_at);
    END IF;
END //
DELIMITER ;
CALL AddFailsafeIndexIfNotExists();
DROP PROCEDURE AddFailsafeIndexIfNotExists;
