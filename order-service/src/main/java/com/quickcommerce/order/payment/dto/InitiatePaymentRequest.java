package com.quickcommerce.order.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request body for POST /api/v1/orders/{uuid}/pay
 * Sent by frontend after the order is created (PENDING_PAYMENT).
 */
@Data
public class InitiatePaymentRequest {

    @NotBlank(message = "Payment phone is required")
    @Pattern(regexp = "^(097|077)\\d{7}$", message = "Payment phone must be a valid Zambian Airtel/MTN number (e.g. 0971234567)")
    private String paymentPhone;
}
