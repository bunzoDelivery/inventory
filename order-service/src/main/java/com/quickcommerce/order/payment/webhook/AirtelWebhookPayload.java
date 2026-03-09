package com.quickcommerce.order.payment.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Incoming webhook payload from Airtel after customer enters PIN.
 *
 * Example payload (Airtel Africa Collections webhook):
 * {
 * "transaction": {
 * "id": "MP211103.1657.L09441", ← airtelTransactionId we stored
 * "message": "Paid",
 * "status_code": "TS", ← TS=success, TF=failed
 * "airtel_money_id": "CI211103.1657.X1234"
 * }
 * }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AirtelWebhookPayload {

    @JsonProperty("transaction")
    private TransactionPayload transaction;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionPayload {

        /** Airtel's transaction ID — matches airtel_transaction_id on our order */
        @JsonProperty("id")
        private String id;

        /**
         * Transaction status code:
         * "TS" — Transaction Successful
         * "TF" — Transaction Failed
         * "TA" — Transaction Abandoned
         */
        @JsonProperty("status_code")
        private String statusCode;

        @JsonProperty("message")
        private String message;

        @JsonProperty("airtel_money_id")
        private String airtelMoneyId;
    }

    public boolean isSuccess() {
        return transaction != null && "TS".equals(transaction.getStatusCode());
    }

    public String getTransactionId() {
        return transaction != null ? transaction.getId() : null;
    }

    public String getStatusCode() {
        return transaction != null ? transaction.getStatusCode() : null;
    }
}
