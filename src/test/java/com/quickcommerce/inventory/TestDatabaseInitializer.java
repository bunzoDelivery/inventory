package com.quickcommerce.inventory;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TestDatabaseInitializer {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public TestDatabaseInitializer(R2dbcEntityTemplate r2dbcEntityTemplate) {
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
    }

    public Mono<Void> initializeSchema() {
        System.out.println("Starting database schema initialization...");
        return dropExistingTables()
                .doOnSuccess(v -> System.out.println("Dropped existing tables"))
                .then(createInventoryItemsTable())
                .doOnSuccess(v -> System.out.println("Inventory items table created"))
                .then(createStockReservationsTable())
                .doOnSuccess(v -> System.out.println("Stock reservations table created"))
                .then(createInventoryMovementsTable())
                .doOnSuccess(v -> System.out.println("Inventory movements table created"))
                .doOnSuccess(v -> System.out.println("Database schema initialized successfully"))
                .doOnError(e -> {
                    System.err.println("Failed to initialize database schema: " + e.getMessage());
                    e.printStackTrace();
                })
                .onErrorResume(e -> {
                    System.err.println("Error during schema initialization, continuing anyway");
                    return Mono.empty();
                });
    }

    private Mono<Void> dropExistingTables() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("DROP TABLE IF EXISTS inventory_movements")
                .fetch()
                .rowsUpdated()
                .then(r2dbcEntityTemplate.getDatabaseClient()
                        .sql("DROP TABLE IF EXISTS stock_reservations")
                        .fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient()
                        .sql("DROP TABLE IF EXISTS inventory_items")
                        .fetch()
                        .rowsUpdated())
                .then();
    }

    private Mono<Void> createInventoryItemsTable() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("""
                        CREATE TABLE IF NOT EXISTS inventory_items (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            sku VARCHAR(255) NOT NULL UNIQUE,
                            product_id BIGINT NOT NULL,
                            store_id BIGINT NOT NULL,
                            current_stock INT NOT NULL DEFAULT 0,
                            reserved_stock INT NOT NULL DEFAULT 0,
                            safety_stock INT NOT NULL DEFAULT 0,
                            max_stock INT DEFAULT NULL,
                            unit_cost DECIMAL(10, 2) DEFAULT NULL,
                            version BIGINT NOT NULL DEFAULT 0,
                            last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                        )
                        """)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> createStockReservationsTable() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("""
                        CREATE TABLE IF NOT EXISTS stock_reservations (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            reservation_id VARCHAR(255) NOT NULL UNIQUE,
                            inventory_item_id BIGINT NOT NULL,
                            quantity INT NOT NULL,
                            customer_id BIGINT NOT NULL,
                            order_id VARCHAR(255) NOT NULL,
                            expires_at TIMESTAMP NOT NULL,
                            status VARCHAR(50) NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> createInventoryMovementsTable() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("""
                        CREATE TABLE IF NOT EXISTS inventory_movements (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            inventory_item_id BIGINT NOT NULL,
                            movement_type VARCHAR(50) NOT NULL,
                            quantity INT NOT NULL,
                            reference_type VARCHAR(50),
                            reference_id VARCHAR(255),
                            reason VARCHAR(255),
                            created_by VARCHAR(255),
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """)
                .fetch()
                .rowsUpdated()
                .then();
    }
}
