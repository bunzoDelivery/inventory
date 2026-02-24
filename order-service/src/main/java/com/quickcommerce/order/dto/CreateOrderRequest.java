package com.quickcommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "Store ID is required")
    private Long storeId;

    @NotNull(message = "Customer ID is required")
    @NotBlank(message = "Customer ID cannot be blank")
    private String customerId;

    @NotEmpty(message = "Order must contain at least one item")
    @Size(max = 50, message = "Cannot order more than 50 distinct SKUs at once")
    @Valid
    private List<OrderItemRequest> items;

    @NotNull(message = "Payment method is required")
    @Pattern(regexp = "COD|AIRTEL_MONEY|MTN_MONEY", message = "Payment method must be COD, AIRTEL_MONEY, or MTN_MONEY")
    private String paymentMethod;

    @NotNull(message = "Delivery details are required")
    @Valid
    private DeliveryRequest delivery;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotNull(message = "SKU is required")
        @NotBlank(message = "SKU cannot be blank")
        private String sku;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 100, message = "Cannot order more than 100 units of a single item")
        private Integer quantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryRequest {

        @NotNull(message = "Delivery latitude is required")
        @DecimalMin(value = "-90.0", message = "Invalid latitude")
        @DecimalMax(value = "90.0", message = "Invalid latitude")
        private Double latitude;

        @NotNull(message = "Delivery longitude is required")
        @DecimalMin(value = "-180.0", message = "Invalid longitude")
        @DecimalMax(value = "180.0", message = "Invalid longitude")
        private Double longitude;

        @NotNull(message = "Delivery address is required")
        @NotBlank(message = "Delivery address cannot be blank")
        @Size(max = 500, message = "Address too long")
        private String address;

        @NotNull(message = "Contact phone number is required")
        @Pattern(regexp = "^(\\+260|0)[79]\\d{8}$", message = "Must be a valid Zambian mobile number (e.g. 0977123456)")
        private String phone;

        @Size(max = 255, message = "Delivery notes too long")
        private String notes;
    }
}
