package com.quickcommerce.product.container;

import org.testcontainers.containers.MySQLContainer;

/**
 * Singleton MySQL Test Container for reuse across tests
 */
public class MySQLTestContainer extends MySQLContainer<MySQLTestContainer> {
    private static final String IMAGE_VERSION = "mysql:8.0";
    private static MySQLTestContainer container;

    private MySQLTestContainer() {
        super(IMAGE_VERSION);
    }

    public static MySQLTestContainer getInstance() {
        if (container == null) {
            container = new MySQLTestContainer()
                    .withDatabaseName("inventory")
                    .withUsername("root")
                    .withPassword("root");
        }
        return container;
    }

    @Override
    public void start() {
        super.start();
        System.setProperty("spring.r2dbc.url", getR2dbcUrl());
        System.setProperty("spring.r2dbc.username", getUsername());
        System.setProperty("spring.r2dbc.password", getPassword());
        System.setProperty("spring.flyway.url", getJdbcUrl());
        System.setProperty("spring.flyway.user", getUsername());
        System.setProperty("spring.flyway.password", getPassword());
    }

    @Override
    public void stop() {
        // Do nothing, let JVM handle shutdown
    }

    public String getR2dbcUrl() {
        return String.format("r2dbc:mysql://%s:%d/%s",
                getHost(),
                getMappedPort(MYSQL_PORT),
                getDatabaseName());
    }

    public boolean hasStarted() {
        return container != null && container.isRunning();
    }
}
