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
    @Pattern(regexp = "^((\\+91|0)?[6-9]\\d{9}|(\\+260|0)?[79]\\d{8})$", 
             message = "Payment phone must be a valid Indian (10 digits, starts with 6-9) or Zambian mobile number (e.g. 9876543210, 0971234567, +260977123456)")
    private String paymentPhone;
}
