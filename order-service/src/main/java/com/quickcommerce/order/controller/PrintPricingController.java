package com.quickcommerce.order.controller;

import com.quickcommerce.order.config.PrintPricingConfig;
import com.quickcommerce.order.dto.PrintPricingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/print")
@RequiredArgsConstructor
public class PrintPricingController {

    private final PrintPricingConfig pricingConfig;

    @GetMapping("/pricing")
    public Mono<ResponseEntity<PrintPricingResponse>> getPricing() {
        PrintPricingResponse response = PrintPricingResponse.builder()
                .basePricePerPageBlackWhite(pricingConfig.getBasePriceBw())
                .basePricePerPageColor(pricingConfig.getBasePriceColor())
                .doubleSideMultiplier(pricingConfig.getDoubleSideMultiplier())
                .currency("ZMW")
                .build();

        return Mono.just(ResponseEntity.ok(response));
    }
}
