package com.quickcommerce.inventory.container.db;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Customized extension of Testcontainer's default MySQL container
 * 
 * This is accessed as a singleton, allowing:
 * 1. Multiple test classes to use the same container, reducing spin up time
 * 2. Test configuration to fetch the configuration values for the same container at runtime
 */
public class MySQLTestContainer extends MySQLContainer<MySQLTestContainer> {

    private static final String IMAGE_VERSION = "mysql:8.0";
    public static final String DEFAULT_ROOT_USERNAME = "root";
    public static final String DEFAULT_USERNAME = "test";
    public static final String DEFAULT_PASSWORD = "test";
    public static final String DEFAULT_DATABASE = "test_inventory";

    protected MySQLTestContainer() {
        super(DockerImageName.parse(IMAGE_VERSION));
        withDatabaseName(DEFAULT_DATABASE);
        withUsername(DEFAULT_USERNAME);
        withPassword(DEFAULT_PASSWORD);
        withEnv("MYSQL_ROOT_PASSWORD", DEFAULT_PASSWORD);
        withReuse(true);
        withCommand("--default-authentication-plugin=mysql_native_password");
        waitingFor(org.testcontainers.containers.wait.strategy.Wait
                .forLogMessage(".*ready for connections.*", 1));
    }

    private static MySQLTestContainer instance;
    private boolean hasStarted = false;

    public static MySQLTestContainer getInstance() {
        if (instance == null) {
            instance = new MySQLTestContainer();
        }
        return instance;
    }

    @Override
    public void start() {
        super.start();

        // Set system properties for Spring configuration
        System.setProperty("DB_HOST", getHost());
        System.setProperty("DB_PORT", String.valueOf(getFirstMappedPort()));
        System.setProperty("DB_NAME", DEFAULT_DATABASE);
        System.setProperty("DB_USERNAME", DEFAULT_USERNAME);
        System.setProperty("DB_PASSWORD", DEFAULT_PASSWORD);

        // Set R2DBC URL for Spring Boot
        System.setProperty("spring.r2dbc.url", getR2dbcUrl());
        System.setProperty("spring.r2dbc.username", getUsername());
        System.setProperty("spring.r2dbc.password", getPassword());

        hasStarted = true;
    }

    @Override
    public void stop() {
        // Keep container running to be re-used across tests
    }

    public String getR2dbcUrl() {
        return String.format("r2dbc:mysql://%s:%d/%s", getHost(), getFirstMappedPort(), DEFAULT_DATABASE);
    }

    public boolean hasStarted() {
        return hasStarted;
    }
}
