package com.quickcommerce.order.controller;

import com.quickcommerce.order.dto.PagedOrderResponse;
import com.quickcommerce.order.exception.GlobalExceptionHandler;
import com.quickcommerce.order.service.OrderPreviewService;
import com.quickcommerce.order.service.OrderService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Controller-slice tests verifying that @Min/@Max validation on page/size
 * request parameters is correctly enforced and returns 400 Bad Request.
 */
@WebFluxTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderPreviewService orderPreviewService;

    @MockBean(name = "orderCreationRateLimiter")
    private RateLimiter orderCreationRateLimiter;

    private static final PagedOrderResponse EMPTY_PAGE = PagedOrderResponse.builder()
            .content(List.of())
            .meta(PagedOrderResponse.PageMeta.builder()
                    .page(0).size(20).totalElements(0).totalPages(0)
                    .first(true).last(true).build())
            .build();

    @Test
    @DisplayName("GET /customer/{id}?pageNum=-1 returns 400")
    void customerOrders_negativePage_returns400() {
        webTestClient.get()
                .uri("/api/v1/orders/customer/CUST_001?pageNum=-1&pageSize=10")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("GET /customer/{id}?pageSize=0 returns 400")
    void customerOrders_zeroSize_returns400() {
        webTestClient.get()
                .uri("/api/v1/orders/customer/CUST_001?pageNum=0&pageSize=0")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("GET /customer/{id}?pageSize=51 returns 400")
    void customerOrders_oversizedPage_returns400() {
        webTestClient.get()
                .uri("/api/v1/orders/customer/CUST_001?pageNum=0&pageSize=51")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("GET /store/{id}?pageNum=-1 returns 400")
    void storeOrders_negativePage_returns400() {
        webTestClient.get()
                .uri("/api/v1/orders/store/1?pageNum=-1&pageSize=10")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("GET /store/{id}?pageSize=51 returns 400")
    void storeOrders_oversizedPage_returns400() {
        webTestClient.get()
                .uri("/api/v1/orders/store/1?pageNum=0&pageSize=51")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("GET /customer/{id} with valid params returns 200 with pagination metadata")
    void customerOrders_validParams_returns200WithMeta() {
        when(orderService.getCustomerOrders(anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.just(EMPTY_PAGE));

        webTestClient.get()
                .uri("/api/v1/orders/customer/CUST_001?pageNum=0&pageSize=20")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.meta.page").isEqualTo(0)
                .jsonPath("$.meta.size").isEqualTo(20)
                .jsonPath("$.meta.first").isEqualTo(true)
                .jsonPath("$.meta.last").isEqualTo(true)
                .jsonPath("$.content").isArray();
    }

    @Test
    @DisplayName("GET /store/{id} with valid params and status filter returns 200 with pagination metadata")
    void storeOrders_validParams_returns200WithMeta() {
        when(orderService.getStoreOrders(any(), anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.just(EMPTY_PAGE));

        webTestClient.get()
                .uri("/api/v1/orders/store/1?status=CONFIRMED&pageNum=0&pageSize=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.meta").exists()
                .jsonPath("$.content").isArray();
    }
}
