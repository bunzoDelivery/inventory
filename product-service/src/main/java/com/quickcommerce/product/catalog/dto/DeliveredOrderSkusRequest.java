package com.quickcommerce.product.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Internal payload: when an order is delivered, one increment per distinct SKU in that order.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveredOrderSkusRequest {

    @NotEmpty
    private List<@NotBlank String> skus;
}
