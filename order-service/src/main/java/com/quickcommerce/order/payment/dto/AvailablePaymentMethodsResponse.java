package com.quickcommerce.order.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for {@code GET /api/v1/payment-methods}.
 *
 * <p>
 * Returns <em>all known</em> payment methods with their current availability
 * status.
 * Disabled methods are still present in the list (with {@code enabled=false})
 * so the
 * UI can render them as greyed-out if desired, rather than silently hiding
 * them.
 *
 * <p>
 * Example:
 * 
 * <pre>{@code
 * {
 *   "methods": [
 *     { "code": "COD",          "label": "Cash on Delivery",    "enabled": true  },
 *     { "code": "AIRTEL_MONEY", "label": "Airtel Money (USSD)", "enabled": true  },
 *     { "code": "MTN_MONEY",    "label": "MTN Money (USSD)",    "enabled": false }
 *   ]
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailablePaymentMethodsResponse {

    private List<PaymentMethodEntry> methods;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodEntry {

        /** Machine-readable code matching the {@code PaymentMethod} enum name. */
        private String code;

        /** Human-readable label for display in the checkout UI. */
        private String label;

        /** Whether this method is currently available for selection. */
        private boolean enabled;
    }
}
