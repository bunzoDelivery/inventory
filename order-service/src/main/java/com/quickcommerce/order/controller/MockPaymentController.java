package com.quickcommerce.order.controller;

import com.quickcommerce.order.dto.OrderResponse;
import com.quickcommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Dev/QA-only endpoints that simulate payment events without calling a real provider.
 * Removed entirely from production builds via @Profile.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Profile({"mock-airtel", "mock-pawapay"})
public class MockPaymentController {

    private final OrderService orderService;

    @PostMapping("/api/v1/orders/{orderUuid}/pay-mock")
    public Mono<ResponseEntity<OrderResponse>> mockPayment(@PathVariable String orderUuid) {
        log.info("[MOCK] Simulating payment confirmation for order={}", orderUuid);
        return orderService.mockPayment(orderUuid)
                .map(ResponseEntity::ok);
    }
}
