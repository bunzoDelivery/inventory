package com.quickcommerce.product.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Messaging configuration for RabbitMQ
 * Only enabled when messaging.enabled=true
 * This makes RabbitMQ optional for MVP deployment
 */
@Configuration
@ConditionalOnProperty(name = "messaging.enabled", havingValue = "true", matchIfMissing = false)
public class MessagingConfig {

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }
}
