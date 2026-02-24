package com.quickcommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

    @NotNull(message = "Target status is required")
    @Pattern(regexp = "PACKING|OUT_FOR_DELIVERY|DELIVERED", message = "Status must be PACKING, OUT_FOR_DELIVERY, or DELIVERED")
    private String status;

    private String notes;
}
