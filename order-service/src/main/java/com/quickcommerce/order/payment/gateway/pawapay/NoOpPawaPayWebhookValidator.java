package com.quickcommerce.order.payment.gateway.pawapay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * No-op validator for dev/test. Always passes.
 *
 * Before going to production, replace this with an RFC-9421 HTTP Message Signature validator.
 * PawaPay signs callbacks using ECDSA/RSA and includes:
 *   Content-Digest, Signature-Date, Signature, Signature-Input headers.
 * The public key to verify against is available from the /v2/toolkit/publicKeys endpoint.
 * Enable signed callbacks in the PawaPay Dashboard under Settings → API Tokens.
 * This feature is optional but strongly recommended for production.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!pawapay")
public class NoOpPawaPayWebhookValidator implements PawaPayWebhookValidator {

    @Override
    public Mono<Void> validate(ServerHttpRequest request, String rawBody) {
        log.warn("NoOpPawaPayWebhookValidator is active — ALL webhook requests are accepted. " +
                 "Replace with RFC-9421 signature validator before production.");
        return Mono.empty();
    }
}
