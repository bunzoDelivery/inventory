package com.quickcommerce.catalog;

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
        System.out.println("Starting catalog database schema initialization...");
        return dropExistingTables()
                .doOnSuccess(v -> System.out.println("Dropped existing tables"))
                .then(createCategoriesTable())
                .doOnSuccess(v -> System.out.println("Categories table created"))
                .then(createProductsTable())
                .doOnSuccess(v -> System.out.println("Products table created"))
                .doOnSuccess(v -> System.out.println("Catalog database schema initialized successfully"))
                .doOnError(e -> {
                    System.err.println("Failed to initialize catalog database schema: " + e.getMessage());
                    e.printStackTrace();
                })
                .onErrorResume(e -> {
                    System.err.println("Error during catalog schema initialization, continuing anyway");
                    return Mono.empty();
                });
    }

    private Mono<Void> dropExistingTables() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("SET FOREIGN_KEY_CHECKS = 0")
                .fetch()
                .rowsUpdated()
                .then(r2dbcEntityTemplate.getDatabaseClient()
                        .sql("DROP TABLE IF EXISTS products")
                        .fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient()
                        .sql("DROP TABLE IF EXISTS categories")
                        .fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient()
                        .sql("SET FOREIGN_KEY_CHECKS = 1")
                        .fetch()
                        .rowsUpdated())
                .then();
    }

    public Mono<Void> clearAllData() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql("SET FOREIGN_KEY_CHECKS = 0")
                .fetch()
                .rowsUpdated()
                .then(r2dbcEntityTemplate.getDatabaseClient()
                        .sql("TRUNCATE TABLE products")
                        .fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient()
                        .sql("TRUNCATE TABLE categories")
                        .fetch()
                        .rowsUpdated())
                .then(r2dbcEntityTemplate.getDatabaseClient()
                        .sql("SET FOREIGN_KEY_CHECKS = 1")
                        .fetch()
                        .rowsUpdated())
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
                            INDEX idx_parent_id (parent_id),
                            INDEX idx_slug (slug),
                            FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
                        )
                        """)
                .fetch()
                .rowsUpdated()
                .then();
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
                            FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
                            INDEX idx_sku (sku),
                            INDEX idx_category_id (category_id),
                            INDEX idx_slug (slug),
                            FULLTEXT INDEX ft_search (name, description, tags)
                        )
                        """)
                .fetch()
                .rowsUpdated()
                .then();
    }
}
