package com.quickcommerce.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Database configuration for R2DBC with MySQL
 * Spring Boot auto-configuration will handle the ConnectionFactory based on application.yml
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.quickcommerce.inventory.repository")
public class DatabaseConfiguration {
    // Spring Boot will auto-configure the ConnectionFactory from application.yml
    // No additional beans needed for basic setup
}