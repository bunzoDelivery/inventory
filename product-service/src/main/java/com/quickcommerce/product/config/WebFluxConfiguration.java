package com.quickcommerce.product.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.server.WebFilter;

import java.util.UUID;

/**
 * WebFlux configuration for Product Service
 */
@Configuration
@Slf4j
public class WebFluxConfiguration {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                        .build())
                .build();
    }

    /**
     * Request logging filter with MDC support for correlation tracking
     */
    @Bean
    public WebFilter requestLoggingFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestId = UUID.randomUUID().toString();
            
            // Add correlation ID to MDC for logging
            MDC.put("requestId", requestId);
            MDC.put("method", request.getMethod().toString());
            MDC.put("path", request.getPath().value());
            
            log.info("Incoming request: {} {} from {}",
                request.getMethod(),
                request.getPath(),
                request.getRemoteAddress());
            
            long startTime = System.currentTimeMillis();
            
            return chain.filter(exchange)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null 
                        ? exchange.getResponse().getStatusCode().value() 
                        : 0;
                    
                    log.info("Request completed: {} in {}ms with status {}",
                        requestId, duration, statusCode);
                    
                    // Clear MDC to prevent memory leaks
                    MDC.clear();
                });
        };
    }
}
