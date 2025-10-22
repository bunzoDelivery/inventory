package com.quickcommerce.inventory;

import com.quickcommerce.inventory.container.db.MySQLTestContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SimpleContainerTest {

    @Container
    public static MySQLTestContainer mysqlContainer = MySQLTestContainer.getInstance();

    @Test
    public void testContainerStarts() {
        assertThat(mysqlContainer.isRunning()).isTrue();
        assertThat(mysqlContainer.getDatabaseName()).isEqualTo("test_inventory");
        System.out.println("MySQL Container is running on: " + mysqlContainer.getHost() + ":"
                + mysqlContainer.getFirstMappedPort());
    }
}
