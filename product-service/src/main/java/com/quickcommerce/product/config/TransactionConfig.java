package com.quickcommerce.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Transaction configuration for R2DBC
 * Provides TransactionalOperator for explicit transaction management in reactive code
 */
@Configuration
public class TransactionConfig {

    /**
     * Create TransactionalOperator bean for explicit transaction management
     * This is the recommended way to handle transactions in reactive R2DBC code
     * 
     * Usage:
     * <pre>
     * return someReactiveOperation()
     *     .as(transactionalOperator::transactional);
     * </pre>
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager txManager) {
        return TransactionalOperator.create(txManager);
    }
}
