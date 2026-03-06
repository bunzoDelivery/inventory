package com.quickcommerce.order.payment.client;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request sent to Airtel to initiate a USSD STK push.
 * Maps to Airtel Africa Collections API: POST /merchant/v1/payments/
 */
@Data
@Builder
public class AirtelPushRequest {

    /**
     * Customer's Airtel MSISDN (e.g. "260971234567" — international format without
     * +)
     */
    private String msisdn;

    /**
     * Order UUID used as Airtel reference — guarantees idempotency (Airtel won't
     * charge twice for same reference)
     */
    private String reference;

    /** Amount to collect in ZMW */
    private BigDecimal amount;

    /** ISO country code e.g. "ZM" */
    private String country;

    /** ISO currency code e.g. "ZMW" */
    private String currency;
}
