package com.quickcommerce.order.payment.dto;

import com.quickcommerce.order.domain.MobileNetwork;
import com.quickcommerce.order.util.ValidPhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for POST /api/v1/orders/{uuid}/pay
 * Sent by frontend after the order is created (PENDING_PAYMENT).
 */
@Data
public class InitiatePaymentRequest {

    @NotBlank(message = "Payment phone is required")
    @ValidPhone
    private String paymentPhone;

    /**
     * Which mobile network the payment phone belongs to.
     * AIRTEL — Airtel Zambia (097x / 077x)
     * MTN    — MTN Zambia (076x / 096x)
     *
     * PawaPay routes to both; Airtel Direct only supports AIRTEL.
     */
    @NotNull(message = "Mobile network is required (AIRTEL or MTN)")
    private MobileNetwork mobileNetwork;
}
