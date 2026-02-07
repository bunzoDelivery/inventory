package com.quickcommerce.product.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductListRequest {

    @NotEmpty(message = "SKU list cannot be empty")
    @Size(min = 1, max = 100, message = "SKU list must contain between 1 and 100 items")
    private List<String> skus;
}
