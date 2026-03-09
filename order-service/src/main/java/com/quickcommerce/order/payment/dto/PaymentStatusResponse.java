package com.quickcommerce.order.payment.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response for:
 * POST /api/v1/orders/{uuid}/pay — after STK push initiated
 * GET /api/v1/orders/{uuid}/payment-status — frontend polling
 */
@Data
@Builder
public class PaymentStatusResponse {

    private String orderUuid;

    /** Current order status (e.g. PENDING_PAYMENT, CONFIRMED, CANCELLED) */
    private String orderStatus;

    /** Current payment status (e.g. PENDING, PAID, COD_PENDING) */
    private String paymentStatus;

    /** Masked payment phone e.g. "097****567" */
    private String paymentPhone;

    /**
     * Status of the STK push itself:
     * PUSH_SENT — prompt sent to customer's phone, waiting for PIN
     * CONFIRMED — payment successful
     * FAILED — payment rejected or timed out
     */
    private String pushStatus;

    private String message;
}
