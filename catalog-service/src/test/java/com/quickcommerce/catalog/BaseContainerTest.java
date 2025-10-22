package com.quickcommerce.catalog;

import com.quickcommerce.catalog.container.MySQLTestContainer;
import com.quickcommerce.catalog.domain.Category;
import com.quickcommerce.catalog.domain.Product;
import com.quickcommerce.catalog.repository.CategoryRepository;
import com.quickcommerce.catalog.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Base class for all containerized tests.
 */
@Testcontainers
@ContextConfiguration(initializers = BaseContainerTest.ContainerInitializer.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

    @Container
    public static MySQLTestContainer mysqlContainer = MySQLTestContainer.getInstance();

    @BeforeEach
    void setUp() {
        // Initialize database schema and clean up before each test
        testDatabaseInitializer.initializeSchema().block();
        testDatabaseInitializer.clearAllData().block();
    }

    /**
     * Create test category
     */
    protected Category createTestCategory(String name, String slug, Long parentId, Integer displayOrder) {
        Category category = new Category();
        category.setName(name);
        category.setDescription("Test " + name);
        category.setParentId(parentId);
        category.setSlug(slug);
        category.setDisplayOrder(displayOrder);
        category.setIsActive(true);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());

        return categoryRepository.save(category).block();
    }

    /**
     * Create test product
     */
    protected Product createTestProduct(String sku, String name, Long categoryId, BigDecimal price) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription("Test " + name);
        product.setShortDescription("Test product");
        product.setCategoryId(categoryId);
        product.setBrand("Test Brand");
        product.setBasePrice(price);
        product.setUnitOfMeasure("piece");
        product.setPackageSize("1 piece");
        product.setTags("test,product");
        product.setIsActive(true);
        product.setIsAvailable(true);
        product.setSlug(sku.toLowerCase());
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        return productRepository.save(product).block();
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
