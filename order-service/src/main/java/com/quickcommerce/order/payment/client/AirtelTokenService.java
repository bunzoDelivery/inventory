package com.quickcommerce.order.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the Airtel OAuth2 access token.
 *
 * The token is cached in-memory using an AtomicReference (no Redis needed).
 * A @Scheduled job refreshes it every 45 minutes (tokens expire in ~60 min).
 *
 * Concurrent callers that find an expired token share a single in-flight refresh
 * (via a volatile cached Mono) — avoiding a thundering herd of parallel OAuth2 calls.
 *
 * Only active when Spring profile includes "airtel" (staging/prod).
 * In mock-airtel dev mode this class is not instantiated and does not schedule.
 */
@Slf4j
@Service
@Profile("airtel")
public class AirtelTokenService {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;

    private final AtomicReference<CachedToken> tokenRef = new AtomicReference<>(CachedToken.empty());

    // Shared in-flight refresh — prevents thundering herd on token expiry
    private volatile Mono<String> ongoingRefresh = null;

    public AirtelTokenService(
            WebClient.Builder builder,
            @Value("${airtel.base-url}") String baseUrl,
            @Value("${airtel.client-id}") String clientId,
            @Value("${airtel.client-secret}") String clientSecret) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Returns the cached Bearer token. If the token is missing or expired, fetches a new one.
     * Concurrent callers on an expired token share one refresh call.
     */
    public synchronized Mono<String> getToken() {
        CachedToken cached = tokenRef.get();
        if (cached.isValid()) {
            return Mono.just(cached.token);
        }
        if (ongoingRefresh == null) {
            ongoingRefresh = refreshToken()
                    .doFinally(s -> ongoingRefresh = null)
                    .cache();
        }
        return ongoingRefresh;
    }

    /**
     * Proactively refreshes every 45 minutes. Override with ${airtel.token-refresh-interval-ms}
     */
    @Scheduled(fixedRateString = "${airtel.token-refresh-interval-ms:2700000}")
    public void scheduledRefresh() {
        refreshToken()
                .doOnSuccess(t -> log.info("Airtel token refreshed successfully"))
                .doOnError(e -> log.error("Scheduled Airtel token refresh failed", e))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private Mono<String> refreshToken() {
        log.info("Fetching new Airtel OAuth2 token");
        return webClient.post()
                .uri("/auth/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("grant_type", "client_credentials"))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .map(resp -> {
                    CachedToken ct = new CachedToken(resp.accessToken, resp.expiresIn);
                    tokenRef.set(ct);
                    log.info("Airtel token cached, expires in {}s", resp.expiresIn);
                    return ct.token;
                })
                .doOnError(e -> log.error("Failed to fetch Airtel OAuth2 token", e));
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private static class CachedToken {
        final String token;
        final Instant expiresAt;

        CachedToken(String token, int expiresInSeconds) {
            this.token = token;
            // Treat as expired 2 minutes early as a safety buffer
            this.expiresAt = Instant.now().plusSeconds(expiresInSeconds - 120L);
        }

        private CachedToken() {
            this.token = null;
            this.expiresAt = Instant.EPOCH;
        }

        static CachedToken empty() {
            return new CachedToken();
        }

        boolean isValid() {
            return token != null && Instant.now().isBefore(expiresAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TokenResponse {
        @JsonProperty("access_token")
        String accessToken;
        @JsonProperty("expires_in")
        int expiresIn;
    }
}
