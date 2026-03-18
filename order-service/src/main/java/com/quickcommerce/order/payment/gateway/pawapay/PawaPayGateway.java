package com.quickcommerce.order.payment.gateway.pawapay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.quickcommerce.order.domain.MobileNetwork;
import com.quickcommerce.order.exception.PaymentGatewayException;
import com.quickcommerce.order.payment.gateway.GatewayName;
import com.quickcommerce.order.payment.gateway.GatewayPaymentRequest;
import com.quickcommerce.order.payment.gateway.GatewayPaymentResponse;
import com.quickcommerce.order.payment.gateway.GatewayStatusOutcome;
import com.quickcommerce.order.payment.gateway.GatewayStatusResponse;
import com.quickcommerce.order.payment.gateway.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * PawaPay payment gateway adapter.
 *
 * <p>PawaPay is an aggregator that routes to both Airtel and MTN networks in Zambia,
 * so a single integration covers all mobile money users.
 *
 * <p>Key differences from Airtel Direct:
 * <ul>
 *   <li>Auth: static API key (no OAuth2 token rotation needed)</li>
 *   <li>Idempotency: we supply the {@code depositId} (our {@code orderUuid})</li>
 *   <li>Network routing: via {@code correspondent} field (AIRTEL_ZAMBIA | MTN_ZAMBIA)</li>
 *   <li>Status codes: COMPLETED | FAILED | DUPLICATE_IGNORED | INITIATED | SUBMITTED</li>
 * </ul>
 *
 * Active when Spring profile includes "pawapay".
 *
 * PawaPay API reference: https://docs.pawapay.co.zw/
 */
@Slf4j
@Component
@Profile("pawapay")
public class PawaPayGateway implements PaymentGateway {

    private final WebClient webClient;

    public PawaPayGateway(
            WebClient.Builder builder,
            @Value("${pawapay.base-url}") String baseUrl,
            @Value("${pawapay.api-key}") String apiKey) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public GatewayName getGatewayName() {
        return GatewayName.PAWAPAY;
    }

    /**
     * Initiates a deposit (USSD push) via PawaPay V2 API.
     *
     * <p>The {@code depositId} is set to our {@code orderUuid} — PawaPay will echo
     * it back in webhooks, making correlation trivial without any extra DB lookup.
     *
     * <p>V2 payer format: type=MMO, accountDetails.provider + accountDetails.phoneNumber
     * Provider codes for Zambia: AIRTEL_OAPI_ZMB | MTN_MOMO_ZMB | ZAMTEL_ZMB
     */
    @Override
    public Mono<GatewayPaymentResponse> initiatePayment(GatewayPaymentRequest request) {
        String provider = mapProvider(request.getMobileNetwork());
        String phone    = normalizePhone(request.getMsisdn());

        Map<String, Object> body = Map.of(
                "depositId", request.getOrderUuid(),
                "amount",    request.getAmount().toPlainString(),
                "currency",  request.getCurrency(),
                "payer", Map.of(
                        "type", "MMO",
                        "accountDetails", Map.of(
                                "provider",    provider,
                                "phoneNumber", phone
                        )
                ),
                "customerMessage", "QuickCommerce Order"
        );

        log.info("PawaPay deposit initiation — depositId={}, provider={}, amount={}",
                request.getOrderUuid(), provider, request.getAmount());

        return webClient.post()
                .uri("/v2/deposits")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PawaPayDepositResponse.class)
                .flatMap(resp -> {
                    if (!"ACCEPTED".equals(resp.getStatus()) && !"COMPLETED".equals(resp.getStatus())) {
                        log.error("PawaPay deposit rejected — depositId={}, status={}, failureCode={}",
                                request.getOrderUuid(), resp.getStatus(), resp.getFailureCode());
                        return Mono.error(new PaymentGatewayException(
                                "PawaPay rejected the deposit request. Please try again or switch to COD."));
                    }
                    log.info("PawaPay deposit accepted — depositId={}, status={}",
                            request.getOrderUuid(), resp.getStatus());
                    return Mono.just(GatewayPaymentResponse.builder()
                            .gatewayRef(request.getOrderUuid())
                            .build());
                })
                .doOnError(WebClientResponseException.class, e ->
                        log.error("PawaPay HTTP error during deposit — depositId={}, status={}, body={}",
                                request.getOrderUuid(), e.getStatusCode(), e.getResponseBodyAsString(), e));
    }

    /**
     * Polls PawaPay for the current status of a deposit.
     * Used by the failsafe scheduler for orders whose webhook was missed.
     *
     * <p>The check-status response wraps the deposit in a {@code data} field:
     * {@code { "status": "FOUND", "data": { "status": "COMPLETED", ... } }}
     * The outer {@code status} is the lookup result ("FOUND" or "NOT_FOUND");
     * the actual payment status is in {@code data.status}.
     */
    @Override
    public Mono<GatewayStatusResponse> checkPaymentStatus(String gatewayRef) {
        return webClient.get()
                .uri("/v2/deposits/{depositId}", gatewayRef)
                .retrieve()
                .bodyToMono(PawaPayStatusCheckResponse.class)
                .map(resp -> {
                    String paymentStatus = resp.getPaymentStatus();
                    log.info("PawaPay status check — depositId={}, outerStatus={}, paymentStatus={}",
                            gatewayRef, resp.getOuterStatus(), paymentStatus);
                    return GatewayStatusResponse.builder()
                            .gatewayRef(gatewayRef)
                            .outcome(mapPawaPayStatus(paymentStatus))
                            .rawStatus(paymentStatus != null ? paymentStatus : resp.getOuterStatus())
                            .build();
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("PawaPay status check failed — depositId={}, httpStatus={}",
                            gatewayRef, e.getStatusCode());
                    return Mono.just(GatewayStatusResponse.builder()
                            .gatewayRef(gatewayRef)
                            .outcome(GatewayStatusOutcome.PENDING)
                            .rawStatus("HTTP_ERROR_" + e.getStatusCode().value())
                            .build());
                });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Maps our MobileNetwork enum to PawaPay's V2 provider identifier for Zambia.
     * Full list: https://docs.pawapay.io/v2/docs/providers
     */
    private String mapProvider(MobileNetwork network) {
        return switch (network) {
            case AIRTEL -> "AIRTEL_OAPI_ZMB";
            case MTN    -> "MTN_MOMO_ZMB";
        };
    }

    /**
     * PawaPay expects E.164 format without leading zero or '+': 0971234567 → 260971234567
     */
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("260")) return digits;
        return digits.startsWith("0") ? "260" + digits.substring(1) : digits;
    }

    /**
     * Maps PawaPay deposit status to canonical outcome.
     * ACCEPTED/SUBMITTED/INITIATED = still in progress
     * COMPLETED                    = paid
     * FAILED/EXPIRED               = terminal failure
     * DUPLICATE_IGNORED            = treated as pending (idempotency guard handles it)
     */
    private GatewayStatusOutcome mapPawaPayStatus(String status) {
        if (status == null) return GatewayStatusOutcome.PENDING;
        return switch (status) {
            case "COMPLETED"         -> GatewayStatusOutcome.SUCCESS;
            case "FAILED", "EXPIRED" -> GatewayStatusOutcome.FAILED;
            default                  -> GatewayStatusOutcome.PENDING;
        };
    }

    // ─── PawaPay response shapes ──────────────────────────────────────────────

    /**
     * Response shape for GET /v2/deposits/{id} (check status).
     * Outer status is "FOUND" or "NOT_FOUND"; actual payment status is in data.status.
     * { "status": "FOUND", "data": { "status": "COMPLETED", ... } }
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PawaPayStatusCheckResponse {
        @JsonProperty("status")
        private String outerStatus;   // FOUND | NOT_FOUND

        @JsonProperty("data")
        private PawaPayDepositResponse data;

        String getOuterStatus() { return outerStatus; }

        /** Returns the actual payment status from data.status, or null if not found. */
        String getPaymentStatus() {
            return data != null ? data.getStatus() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PawaPayDepositResponse {
        @JsonProperty("depositId")
        private String depositId;

        @JsonProperty("status")
        private String status;

        // V2: failureReason.failureCode is nested, but we flatten it for convenience
        @JsonProperty("failureReason")
        private FailureReason failureReason;

        String getStatus() { return status; }
        String getFailureCode() {
            return failureReason != null ? failureReason.failureCode : null;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class FailureReason {
            @JsonProperty("failureCode")
            String failureCode;
            @JsonProperty("failureMessage")
            String failureMessage;
        }
    }
}
