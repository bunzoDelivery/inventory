package com.quickcommerce.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * R2DBC configuration for reactive database access
 */
@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {

    /**
     * Provides TransactionalOperator for explicit reactive transaction management
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager txManager) {
        return TransactionalOperator.create((R2dbcTransactionManager) txManager);
    }
}
