package com.quickcommerce.order.client;

import com.quickcommerce.order.dto.InventoryAvailabilityRequest;
import com.quickcommerce.order.dto.InventoryAvailabilityResponse;
import com.quickcommerce.order.dto.ReserveStockRequest;
import com.quickcommerce.order.dto.StockReservationResponse;
import com.quickcommerce.order.dto.ReserveStockRequest;
import com.quickcommerce.order.dto.StockReservationResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class InventoryClient {

    private final WebClient webClient;
    private final String inventoryServiceUrl;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public InventoryClient(WebClient webClient,
                          @Value("${client.inventory-service.url}") String inventoryServiceUrl,
                          @Qualifier("productServiceCircuitBreaker") CircuitBreaker circuitBreaker,
                          @Qualifier("productServiceRetry") Retry retry) {
        this.webClient = webClient;
        this.inventoryServiceUrl = inventoryServiceUrl;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public Flux<StockReservationResponse> reserveStock(ReserveStockRequest request) {
        return webClient.post()
                .uri(inventoryServiceUrl + "/api/v1/inventory/reserve")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(StockReservationResponse.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry));
    }

    public Mono<Void> confirmReservation(String orderId) {
        return webClient.post()
                .uri(inventoryServiceUrl + "/api/v1/inventory/reservations/order/" + orderId + "/confirm")
                .retrieve()
                .bodyToMono(Void.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry));
    }

    public Mono<Void> cancelOrderReservations(String orderId) {
        return webClient.post()
                .uri(inventoryServiceUrl + "/api/v1/inventory/reservations/order/" + orderId + "/cancel")
                .retrieve()
                .bodyToMono(Void.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry));
    }

    public Mono<InventoryAvailabilityResponse> checkAvailability(Long storeId, List<String> skus) {
        return webClient.post()
                .uri(inventoryServiceUrl + "/api/v1/inventory/availability")
                .bodyValue(InventoryAvailabilityRequest.builder()
                        .storeId(storeId)
                        .skus(skus)
                        .build())
                .retrieve()
                .bodyToMono(InventoryAvailabilityResponse.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry));
    }
}
