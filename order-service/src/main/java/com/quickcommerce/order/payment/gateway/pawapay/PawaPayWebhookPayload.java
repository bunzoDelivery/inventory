package com.quickcommerce.order.payment.gateway.pawapay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Incoming webhook payload from PawaPay V2 after customer enters PIN.
 *
 * Example V2 callback:
 * {
 *   "depositId":   "f4401bd2-1568-4140-bf2d-eb77d2b2b639",  ← our orderUuid
 *   "status":      "COMPLETED",    ← COMPLETED | FAILED | DUPLICATE_IGNORED
 *   "amount":      "15",
 *   "currency":    "ZMW",
 *   "country":     "ZMB",
 *   "payer": {
 *     "type": "MMO",
 *     "accountDetails": {
 *       "provider":    "AIRTEL_OAPI_ZMB",
 *       "phoneNumber": "260971234567"
 *     }
 *   },
 *   "created":     "2024-01-01T10:00:00Z"
 * }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PawaPayWebhookPayload {

    /** Our orderUuid — the depositId we set when initiating the deposit. */
    @JsonProperty("depositId")
    private String depositId;

    /**
     * COMPLETED         — customer paid successfully
     * FAILED            — payment failed or expired
     * DUPLICATE_IGNORED — same depositId retried; PawaPay de-duplicated it
     */
    @JsonProperty("status")
    private String status;

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("country")
    private String country;

    /** V2 payer: type=MMO, accountDetails.provider + accountDetails.phoneNumber */
    @JsonProperty("payer")
    private Payer payer;

    public boolean isSuccess() {
        return "COMPLETED".equals(status);
    }

    public boolean isDuplicate() {
        return "DUPLICATE_IGNORED".equals(status);
    }

    /** Returns the PawaPay provider code, e.g. "AIRTEL_OAPI_ZMB" or "MTN_MOMO_ZMB" */
    public String getProvider() {
        if (payer != null && payer.accountDetails != null) {
            return payer.accountDetails.provider;
        }
        return null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payer {
        @JsonProperty("type")
        private String type;

        @JsonProperty("accountDetails")
        private AccountDetails accountDetails;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountDetails {
        @JsonProperty("provider")
        private String provider;

        @JsonProperty("phoneNumber")
        private String phoneNumber;
    }
}
