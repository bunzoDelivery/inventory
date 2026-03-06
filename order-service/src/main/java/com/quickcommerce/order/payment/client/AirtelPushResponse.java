package com.quickcommerce.order.payment.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from Airtel after initiating a USSD push.
 * Airtel returns a transaction ID that we store on the Order for status
 * polling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirtelPushResponse {

    /**
     * Airtel-assigned transaction ID. Store this on the Order.
     * Used for: (1) matching incoming webhooks, (2) failsafe status polling.
     */
    private String airtelTransactionId;

    /**
     * Immediate status from Airtel after push initiation.
     * Typically "DP_INITIATED" — means push was sent, waiting for customer PIN.
     */
    private String status;

    /** Human-readable message from Airtel */
    private String message;
}
