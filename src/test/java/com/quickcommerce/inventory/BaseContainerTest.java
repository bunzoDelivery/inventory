package com.quickcommerce.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.quickcommerce.inventory.config.TestSecurityConfiguration;
import com.quickcommerce.inventory.domain.InventoryItem;
import com.quickcommerce.inventory.repository.InventoryItemRepository;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import com.quickcommerce.inventory.container.db.MySQLTestContainer;

import lombok.extern.slf4j.Slf4j;

/**
 * Base class for all containerized tests.
 * 
 * @Testcontainers is used to activate automatic startup of containers
 * @ActiveProfiles sets the test profile
 * @SpringBootTest is used to set up Spring, including test context and DI.
 */
@Testcontainers
@ContextConfiguration(initializers = BaseContainerTest.ContainerInitializer.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestSecurityConfiguration.class)
@Slf4j
public abstract class BaseContainerTest {

    @Autowired
    protected R2dbcEntityTemplate r2dbcEntityTemplate;

    @Autowired
    protected TestDatabaseInitializer testDatabaseInitializer;

    @Autowired
    protected InventoryItemRepository inventoryItemRepository;

    @Container
    public static MySQLTestContainer mysqlContainer = MySQLTestContainer.getInstance();

    @BeforeEach
    void setUp() {
        // Initialize database schema and clean up before each test
        testDatabaseInitializer.initializeSchema().block();
        cleanupDatabase();
    }

    /**
     * Clean up test database
     */
    public void cleanupDatabase() {
        try {
            r2dbcEntityTemplate.getDatabaseClient()
                    .sql("DELETE FROM stock_reservations")
                    .fetch()
                    .rowsUpdated()
                    .then(r2dbcEntityTemplate.getDatabaseClient()
                            .sql("DELETE FROM inventory_movements")
                            .fetch()
                            .rowsUpdated())
                    .then(r2dbcEntityTemplate.getDatabaseClient()
                            .sql("DELETE FROM inventory_items")
                            .fetch()
                            .rowsUpdated())
                    .block();
        } catch (Exception e) {
            log.warn("Failed to cleanup database: {}", e.getMessage());
        }
    }

    /**
     * Create test inventory item
     */
    public void createTestInventoryItem(String sku, Long productId, Long storeId, Integer currentStock,
            Integer safetyStock) {
        InventoryItem item = new InventoryItem();
        item.setSku(sku);
        item.setProductId(productId);
        item.setStoreId(storeId);
        item.setCurrentStock(currentStock);
        item.setReservedStock(0);
        item.setSafetyStock(safetyStock);
        // Don't set version - let Spring Data handle it for new entities

        inventoryItemRepository.save(item).block();
    }

    /**
     * Initialize database schema for tests
     */
    public void setupBaseContainerTest() {
        // This method is called once to set up the container test environment
        log.info("Setting up base container test environment");
    }

    public static class ContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            // Start MySQL container if not already started
            if (!mysqlContainer.hasStarted()) {
                mysqlContainer.start();
            }
        }
    }
}
