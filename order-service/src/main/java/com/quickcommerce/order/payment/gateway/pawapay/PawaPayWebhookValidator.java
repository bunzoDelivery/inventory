package com.quickcommerce.order.payment.gateway.pawapay;

import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * Pluggable webhook security validator for PawaPay callbacks.
 *
 * <p>Dev: {@link NoOpPawaPayWebhookValidator} — always passes.
 * <p>Prod: swap in an RFC-9421 HTTP Message Signature validator. PawaPay signs callbacks
 * using ECDSA/RSA with {@code Signature}, {@code Signature-Input}, {@code Content-Digest},
 * and {@code Signature-Date} headers. The verification public key is fetched from
 * {@code GET /v2/toolkit/publicKeys}. Enable in PawaPay Dashboard → Settings → API Tokens.
 *
 * <p>Same contract as {@code AirtelWebhookValidator}: return {@code Mono.empty()}
 * to proceed, {@code Mono.error()} to reject (the controller still returns 200 OK
 * to PawaPay to prevent endless retries).
 */
public interface PawaPayWebhookValidator {

    Mono<Void> validate(ServerHttpRequest request, String rawBody);
}
