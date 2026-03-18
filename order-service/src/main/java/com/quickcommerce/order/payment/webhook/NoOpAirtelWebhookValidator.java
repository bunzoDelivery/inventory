package com.quickcommerce.order.payment.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Development/MVP webhook validator — always passes.
 *
 * IMPORTANT: Replace this with a real validator before production.
 * Options:
 * - IpAllowlistWebhookValidator: check request IP against Airtel's published IP
 * ranges
 * - HmacWebhookValidator: verify HMAC-SHA256 signature header if Airtel
 * provides it
 *
 * The warning log intentionally stays loud so it's obvious in staging logs when
 * validation is off.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!airtel")
public class NoOpAirtelWebhookValidator implements AirtelWebhookValidator {

    @Override
    public Mono<Void> validate(ServerHttpRequest request, String rawBody) {
        log.warn("[SECURITY] Airtel webhook validation is DISABLED. " +
                "Deploy with a real AirtelWebhookValidator before production. " +
                "Caller IP: {}", request.getRemoteAddress());
        return Mono.empty(); // Always passes
    }
}
