package com.quickcommerce.order.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "print.pricing")
public class PrintPricingConfig {

    private BigDecimal basePriceBw = new BigDecimal("2.00");
    private BigDecimal basePriceColor = new BigDecimal("5.00");
    private BigDecimal doubleSideMultiplier = new BigDecimal("1.5");
}
