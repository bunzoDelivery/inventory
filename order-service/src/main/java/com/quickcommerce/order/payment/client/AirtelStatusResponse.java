package com.quickcommerce.order.payment.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from Airtel status check: GET /standard/v1/payments/{transactionId}
 * Used by the failsafe scheduler to resolve "stuck" PENDING_PAYMENT orders.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirtelStatusResponse {

    private String airtelTransactionId;

    /**
     * Airtel status codes:
     * "TS" — Transaction Successful (confirmed payment)
     * "TF" — Transaction Failed
     * "TA" — Transaction Abandoned (user ignored prompt)
     * "TE" — Transaction Expired
     * "TIP" — Transaction In Progress (still waiting for PIN)
     * "DP_INITIATED" — Push sent, not yet resolved
     */
    private String status;

    private String message;
}
