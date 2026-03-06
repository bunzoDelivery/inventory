package com.quickcommerce.order.payment.webhook;

import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * Pluggable webhook security validator.
 *
 * Dev: NoOpAirtelWebhookValidator — always passes (logs a warning)
 * Prod: swap in IpAllowlistWebhookValidator or HmacWebhookValidator before
 * launch
 *
 * The webhook endpoint always returns 200 OK regardless of validation outcome
 * (to prevent Airtel from retrying endlessly). Security is enforced here by
 * returning Mono.error() which skips processing but still sends 200.
 */
public interface AirtelWebhookValidator {

    /**
     * Returns Mono.empty() if the request is valid and should be processed.
     * Returns Mono.error() if the request is rejected (invalid IP, bad signature,
     * etc.)
     */
    Mono<Void> validate(ServerHttpRequest request, String rawBody);
}
