package com.quickcommerce.order.payment.gateway;

import com.quickcommerce.order.domain.MobileNetwork;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Canonical payment initiation request passed from {@link com.quickcommerce.order.payment.service.PaymentService}
 * to any {@link PaymentGateway} implementation. Gateway adapters translate this into
 * their own vendor-specific wire format.
 */
@Value
@Builder
public class GatewayPaymentRequest {

    /** Our internal order UUID — used as the idempotency key sent to the gateway. */
    String orderUuid;

    /** E.164-compatible MSISDN (e.g. "0971234567" or "+260971234567"). */
    String msisdn;

    BigDecimal amount;
    String currency;

    /** Which mobile network to dial (AIRTEL | MTN). */
    MobileNetwork mobileNetwork;
}
