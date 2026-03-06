package com.quickcommerce.order.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Real Airtel Money API client.
 * Active when Spring profile includes "airtel" (staging/prod).
 *
 * Airtel Africa API:
 * STK Push: POST /merchant/v1/payments/
 * Status check: GET /standard/v1/payments/{transactionId}
 */
@Slf4j
@Component
@Profile("airtel")
public class HttpAirtelMoneyClient implements AirtelMoneyClient {

    private final WebClient webClient;
    private final AirtelTokenService tokenService;
    private final String country;
    private final String currency;

    public HttpAirtelMoneyClient(
            WebClient.Builder builder,
            AirtelTokenService tokenService,
            @Value("${airtel.base-url}") String baseUrl,
            @Value("${airtel.country:ZM}") String country,
            @Value("${airtel.currency:ZMW}") String currency) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.tokenService = tokenService;
        this.country = country;
        this.currency = currency;
    }

    @Override
    public Mono<AirtelPushResponse> initiateUssdPush(AirtelPushRequest request) {
        return tokenService.getToken()
                .flatMap(token -> {
                    // Airtel expects MSISDN without leading 0, with country code: 0971234567 →
                    // 260971234567
                    String msisdn = normalizePhone(request.getMsisdn());

                    Map<String, Object> body = Map.of(
                            "reference", request.getReference(),
                            "subscriber", Map.of(
                                    "country", country,
                                    "currency", currency,
                                    "msisdn", msisdn),
                            "transaction", Map.of(
                                    "amount", request.getAmount(),
                                    "country", country,
                                    "currency", currency,
                                    "id", request.getReference()));

                    return webClient.post()
                            .uri("/merchant/v1/payments/")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header("X-Country", country)
                            .header("X-Currency", currency)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(AirtelPaymentApiResponse.class)
                            .map(resp -> AirtelPushResponse.builder()
                                    .airtelTransactionId(
                                            resp.getData() != null && resp.getData().getTransaction() != null
                                                    ? resp.getData().getTransaction().getId() : null)
                                    .status(resp.getStatus() != null ? resp.getStatus().getResponseCode() : "UNKNOWN")
                                    .message(resp.getStatus() != null ? resp.getStatus().getMessage() : "")
                                    .build())
                            .doOnSuccess(r -> log.info("Airtel STK push initiated — ref={}, airtelTxId={}",
                                    request.getReference(), r.getAirtelTransactionId()))
                            .doOnError(e -> log.error("Airtel STK push failed — ref={}", request.getReference(), e));
                });
    }

    @Override
    public Mono<AirtelStatusResponse> checkPaymentStatus(String airtelTransactionId) {
        return tokenService.getToken()
                .flatMap(token -> webClient.get()
                        .uri("/standard/v1/payments/{id}", airtelTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Country", country)
                        .header("X-Currency", currency)
                        .retrieve()
                        .bodyToMono(AirtelPaymentApiResponse.class)
                        .map(resp -> AirtelStatusResponse.builder()
                                .airtelTransactionId(airtelTransactionId)
                                .status(resp.getData() != null && resp.getData().getTransaction() != null
                                        ? resp.getData().getTransaction().getStatus()
                                        : "UNKNOWN")
                                .message(resp.getStatus() != null ? resp.getStatus().getMessage() : "")
                                .build())
                        .onErrorResume(WebClientResponseException.class, e -> {
                            log.error("Airtel status check failed for txId={}, status={}", airtelTransactionId,
                                    e.getStatusCode());
                            return Mono.just(AirtelStatusResponse.builder()
                                    .airtelTransactionId(airtelTransactionId)
                                    .status("TF")
                                    .message("Status check failed: " + e.getMessage())
                                    .build());
                        }));
    }

    /** Converts 0971234567 → 260971234567 */
    private String normalizePhone(String phone) {
        if (phone == null)
            return "";
        String digits = phone.replaceAll("\\D", "");
        return digits.startsWith("0") ? "260" + digits.substring(1) : digits;
    }

    // ─── Airtel API response shape ────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AirtelPaymentApiResponse {
        @JsonProperty("data")
        private DataWrapper data;
        @JsonProperty("status")
        private StatusBlock status;

        DataWrapper getData() {
            return data;
        }

        StatusBlock getStatus() {
            return status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class DataWrapper {
        @JsonProperty("transaction")
        private TransactionBlock transaction;

        TransactionBlock getTransaction() {
            return transaction;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TransactionBlock {
        @JsonProperty("id")
        private String id;
        @JsonProperty("status")
        private String status;
        @JsonProperty("airtel_money_id")
        private String airtelMoneyId;

        String getId() {
            return id != null ? id : airtelMoneyId;
        }

        String getStatus() {
            return status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StatusBlock {
        @JsonProperty("code")
        private String responseCode;
        @JsonProperty("message")
        private String message;
        @JsonProperty("result_code")
        private String resultCode;

        String getResponseCode() {
            return responseCode;
        }

        String getMessage() {
            return message;
        }
    }
}
