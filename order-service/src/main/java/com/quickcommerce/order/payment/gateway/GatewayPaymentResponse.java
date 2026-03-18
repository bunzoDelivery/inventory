package com.quickcommerce.order.payment.gateway;

import lombok.Builder;
import lombok.Value;

/**
 * Canonical response returned by a {@link PaymentGateway} after successfully
 * initiating a USSD push. The {@code gatewayRef} is the provider-assigned
 * transaction / deposit ID that will appear in webhooks and status-check responses.
 */
@Value
@Builder
public class GatewayPaymentResponse {

    /**
     * Provider-assigned reference for this transaction.
     * <ul>
     *   <li>Airtel Direct: the {@code transaction.id} from the STK push response</li>
     *   <li>PawaPay: the {@code depositId} (our own {@code orderUuid} echoed back)</li>
     * </ul>
     */
    String gatewayRef;
}
