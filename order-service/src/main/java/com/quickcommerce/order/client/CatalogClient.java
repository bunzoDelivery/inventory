package com.quickcommerce.order.client;

import com.quickcommerce.order.dto.ProductListRequest;
import com.quickcommerce.order.dto.ProductPriceResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class CatalogClient {

    private final WebClient webClient;
    private final String productServiceUrl;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public CatalogClient(WebClient webClient,
                        @Value("${client.product-service.url}") String productServiceUrl,
                        @org.springframework.beans.factory.annotation.Qualifier("productServiceCircuitBreaker") CircuitBreaker circuitBreaker,
                        @org.springframework.beans.factory.annotation.Qualifier("productServiceRetry") Retry retry) {
        this.webClient = webClient;
        this.productServiceUrl = productServiceUrl;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public Flux<ProductPriceResponse> getPrices(List<String> skus) {
        return webClient.post()
                .uri(productServiceUrl + "/api/v1/catalog/products/skus")
                .bodyValue(new ProductListRequest(skus))
                .retrieve()
                .bodyToFlux(ProductPriceResponse.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry));
    }
}
