package com.quickcommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintPricingResponse {

    private BigDecimal basePricePerPageBlackWhite;
    private BigDecimal basePricePerPageColor;
    private BigDecimal doubleSideMultiplier;
    private String currency;
}
