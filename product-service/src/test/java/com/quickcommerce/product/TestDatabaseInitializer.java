package com.quickcommerce.product;

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
        return dropExistingTables()
                .then(createCategoriesTable())
                .then(createProductsTable())
                .then(createStoresTable())
                .then(createInventoryItemsTable())
                .then(createStockReservationsTable())
                .then(createInventoryMovementsTable())
                .then(createStockAlertsTable())
                .onErrorResume(e -> {
                    System.err.println("Error during schema initialization: " + e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> dropExistingTables() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("SET FOREIGN_KEY_CHECKS = 0")
                .fetch()
                .rowsUpdated()
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("DROP TABLE IF EXISTS stock_alerts").fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("DROP TABLE IF EXISTS inventory_movements").fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("DROP TABLE IF EXISTS stock_reservations").fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("DROP TABLE IF EXISTS inventory_items").fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("DROP TABLE IF EXISTS products").fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("DROP TABLE IF EXISTS categories").fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("DROP TABLE IF EXISTS stores").fetch().rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("SET FOREIGN_KEY_CHECKS = 1").fetch().rowsUpdated())
                .then();
    }

    public Mono<Void> clearAllData() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("SET FOREIGN_KEY_CHECKS = 0")
                .fetch()
                .rowsUpdated()
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("TRUNCATE TABLE stock_alerts").fetch().rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("TRUNCATE TABLE inventory_movements").fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("TRUNCATE TABLE stock_reservations").fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("TRUNCATE TABLE inventory_items").fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("TRUNCATE TABLE products").fetch().rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("TRUNCATE TABLE categories").fetch().rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient().sql("SET FOREIGN_KEY_CHECKS = 1").fetch().rowsUpdated())
                .then();
    }

    private Mono<Void> createCategoriesTable() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("""
                        CREATE TABLE IF NOT EXISTS categories (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            name VARCHAR(255) NOT NULL,
                            description TEXT,
                            parent_id BIGINT DEFAULT NULL,
                            slug VARCHAR(255) NOT NULL UNIQUE,
                            display_order INT NOT NULL DEFAULT 0,
                            is_active BOOLEAN DEFAULT TRUE,
                            image_url VARCHAR(500),
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
                        )
                        """)
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> createProductsTable() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("""
                        CREATE TABLE IF NOT EXISTS products (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            sku VARCHAR(255) NOT NULL UNIQUE,
                            name VARCHAR(255) NOT NULL,
                            description TEXT,
                            short_description VARCHAR(500),
                            category_id BIGINT NOT NULL,
                            brand VARCHAR(255),
                            base_price DECIMAL(10, 2) NOT NULL,
                            unit_of_measure VARCHAR(50) NOT NULL,
                            package_size VARCHAR(100),
                            images TEXT,
                            tags VARCHAR(500),
                            is_active BOOLEAN DEFAULT TRUE,
                            is_available BOOLEAN DEFAULT TRUE,
                            slug VARCHAR(255) NOT NULL UNIQUE,
                            nutritional_info TEXT,
                            weight_grams INT,
                            barcode VARCHAR(100),
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
                        )
                        """)
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> createStoresTable() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("""
                        CREATE TABLE IF NOT EXISTS stores (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            name VARCHAR(255) NOT NULL,
                            address VARCHAR(500),
                            latitude DECIMAL(10, 8) NOT NULL,
                            longitude DECIMAL(11, 8) NOT NULL,
                            serviceable_radius_km INT DEFAULT 5,
                            is_active BOOLEAN DEFAULT TRUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                        )
                        """)
                .fetch().rowsUpdated()
                .then(r2dbcEntityTemplate.getDatabaseClient()
                        .sql("INSERT INTO stores (id, name, address, latitude, longitude, serviceable_radius_km, is_active) VALUES (1, 'Test Store', 'Test Address', 0.0, 0.0, 5, TRUE)")
                        .fetch().rowsUpdated())
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
                .fetch().rowsUpdated().then();
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
                .fetch().rowsUpdated().then();
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
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> createStockAlertsTable() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("""
                        CREATE TABLE IF NOT EXISTS stock_alerts (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            inventory_item_id BIGINT NOT NULL,
                            alert_type VARCHAR(50) NOT NULL,
                            severity VARCHAR(20) NOT NULL,
                            message VARCHAR(500),
                            is_resolved BOOLEAN DEFAULT FALSE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            resolved_at TIMESTAMP NULL,
                            FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id)
                        )
                        """)
                .fetch().rowsUpdated().then();
    }
}
