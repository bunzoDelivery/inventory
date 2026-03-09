package com.quickcommerce.order.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void productNotFoundException_shouldReturn400() {
        var ex = new ProductNotFoundException("Product not found: SKU123");
        var response = handler.handleProductNotFound(ex).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Product not found: SKU123");
    }

    @Test
    void orderNotFoundException_shouldReturn404() {
        var ex = new OrderNotFoundException("Order not found: ORDER123");
        var response = handler.handleOrderNotFound(ex).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Order not found: ORDER123");
    }

    @Test
    void invalidOrderStateException_shouldReturn400() {
        var ex = new InvalidOrderStateException("Cannot cancel confirmed order");
        var response = handler.handleInvalidOrderState(ex).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Cannot cancel confirmed order");
    }

    @Test
    void insufficientStockException_shouldReturn409() {
        var ex = new InsufficientStockException("Not enough stock available");
        var response = handler.handleInsufficientStock(ex).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Not enough stock available");
    }

    @Test
    void paymentGatewayException_shouldReturn502() {
        var ex = new PaymentGatewayException("Airtel API unavailable");
        var response = handler.handlePaymentGateway(ex).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsEntry("error", "Airtel API unavailable");
    }

    @Test
    void serviceUnavailableException_shouldReturn503() {
        var ex = new ServiceUnavailableException("Database connection failed");
        var response = handler.handleServiceUnavailable(ex).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("error", "Service temporarily unavailable. Please try again later.");
    }

    @Test
    void genericRuntimeException_shouldReturn500() {
        var ex = new RuntimeException("Unexpected error");
        var response = handler.handleRuntimeException(ex).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred");
    }
}
