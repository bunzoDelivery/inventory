package com.quickcommerce.order.client;

import com.quickcommerce.order.dto.ReserveStockRequest;
import com.quickcommerce.order.dto.StockReservationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class InventoryClient {

    private final WebClient webClient;
    private final String inventoryServiceUrl;

    public InventoryClient(WebClient webClient, @Value("${client.inventory-service.url}") String inventoryServiceUrl) {
        this.webClient = webClient;
        this.inventoryServiceUrl = inventoryServiceUrl;
    }

    public Flux<StockReservationResponse> reserveStock(ReserveStockRequest request) {
        return webClient.post()
                .uri(inventoryServiceUrl + "/api/v1/inventory/reserve")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(StockReservationResponse.class);
    }

    public Mono<Void> confirmReservation(String orderId) {
        return webClient.post()
                .uri(inventoryServiceUrl + "/api/v1/inventory/reservations/order/" + orderId + "/confirm")
                .retrieve()
                .bodyToMono(Void.class);
    }
}
