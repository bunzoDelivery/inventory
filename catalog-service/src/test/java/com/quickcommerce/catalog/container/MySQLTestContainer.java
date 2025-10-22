package com.quickcommerce.catalog.container;

import org.testcontainers.containers.MySQLContainer;

/**
 * Singleton MySQL test container for catalog service integration tests
 */
public class MySQLTestContainer extends MySQLContainer<MySQLTestContainer> {

    private static final String IMAGE_VERSION = "mysql:8.0";
    private static MySQLTestContainer container;
    private boolean started = false;

    private MySQLTestContainer() {
        super(IMAGE_VERSION);
    }

    public static MySQLTestContainer getInstance() {
        if (container == null) {
            container = new MySQLTestContainer()
                    .withDatabaseName("test_catalog")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand("--default-authentication-plugin=mysql_native_password")
                    .withReuse(true);
        }
        return container;
    }

    @Override
    public void start() {
        super.start();
        started = true;
        System.setProperty("DB_HOST", container.getHost());
        System.setProperty("DB_PORT", container.getMappedPort(3306).toString());
        System.setProperty("DB_NAME", "test_catalog");
        System.setProperty("DB_USERNAME", "test");
        System.setProperty("DB_PASSWORD", "test");
    }

    @Override
    public void stop() {
        // Do nothing, JVM handles shut down
    }

    public boolean hasStarted() {
        return started;
    }
}
