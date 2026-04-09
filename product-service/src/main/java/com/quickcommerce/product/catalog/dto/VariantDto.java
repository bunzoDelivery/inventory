package com.quickcommerce.product.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Minimal variant representation for the Variant Bottom Sheet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantDto {
    private Long productId;
    private String sku;
    private String size;
    private BigDecimal price;
    private Boolean isAvailable;
}
