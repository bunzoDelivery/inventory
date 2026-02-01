package com.quickcommerce.product;

import com.quickcommerce.product.config.TestSecurityConfiguration;
import com.quickcommerce.product.container.MySQLTestContainer;
import com.quickcommerce.product.catalog.domain.Category;
import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.catalog.repository.CategoryRepository;
import com.quickcommerce.product.catalog.repository.ProductRepository;
import com.quickcommerce.product.domain.InventoryItem;
import com.quickcommerce.product.repository.InventoryItemRepository;
import lombok.extern.slf4j.Slf4j;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Base class for all containerized tests in Product Service.
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
    protected CategoryRepository categoryRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected InventoryItemRepository inventoryItemRepository;

    @Container
    public static MySQLTestContainer mysqlContainer = MySQLTestContainer.getInstance();

    @BeforeEach
    void setUp() {
        testDatabaseInitializer.initializeSchema().block();
        testDatabaseInitializer.clearAllData().block();
    }

    protected Category createTestCategory(String name, String slug) {
        Category category = new Category();
        category.setName(name);
        category.setDescription("Test " + name);
        category.setSlug(slug);
        category.setDisplayOrder(0);
        category.setIsActive(true);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());

        return categoryRepository.save(category).block();
    }

    protected Product createTestProduct(String sku, String name, Long categoryId, BigDecimal price) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription("Test " + name);
        product.setCategoryId(categoryId);
        product.setBasePrice(price);
        product.setUnitOfMeasure("piece");
        product.setIsActive(true);
        product.setIsAvailable(true);
        product.setSlug(sku.toLowerCase());
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        return productRepository.save(product).block();
    }

    protected void createTestInventoryItem(String sku, Long productId, Long storeId, Integer currentStock) {
        InventoryItem item = new InventoryItem();
        item.setSku(sku);
        item.setProductId(productId);
        item.setStoreId(storeId);
        item.setCurrentStock(currentStock);
        item.setReservedStock(0);
        item.setSafetyStock(10);
        inventoryItemRepository.save(item).block();
    }

    public static class ContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            if (!mysqlContainer.hasStarted()) {
                mysqlContainer.start();
            }
        }
    }
}
