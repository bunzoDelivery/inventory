package com.quickcommerce.order.client;

import com.quickcommerce.order.dto.ProductListRequest;
import com.quickcommerce.order.dto.ProductPriceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class CatalogClient {

    private final WebClient webClient;
    private final String productServiceUrl;

    public CatalogClient(WebClient webClient, @Value("${client.product-service.url}") String productServiceUrl) {
        this.webClient = webClient;
        this.productServiceUrl = productServiceUrl;
    }

    public Flux<ProductPriceResponse> getPrices(List<String> skus) {
        return webClient.post()
                .uri(productServiceUrl + "/api/v1/catalog/products/skus")
                .bodyValue(new ProductListRequest(skus))
                .retrieve()
                .bodyToFlux(ProductPriceResponse.class);
    }
}
