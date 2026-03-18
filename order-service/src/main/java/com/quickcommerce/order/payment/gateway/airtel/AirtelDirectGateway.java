package com.quickcommerce.order.payment.gateway.airtel;

import com.quickcommerce.order.domain.MobileNetwork;
import com.quickcommerce.order.exception.PaymentGatewayException;
import com.quickcommerce.order.payment.client.AirtelMoneyClient;
import com.quickcommerce.order.payment.client.AirtelPushRequest;
import com.quickcommerce.order.payment.gateway.GatewayName;
import com.quickcommerce.order.payment.gateway.GatewayPaymentRequest;
import com.quickcommerce.order.payment.gateway.GatewayPaymentResponse;
import com.quickcommerce.order.payment.gateway.GatewayStatusOutcome;
import com.quickcommerce.order.payment.gateway.GatewayStatusResponse;
import com.quickcommerce.order.payment.gateway.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adapter that exposes the existing {@link AirtelMoneyClient} as a {@link PaymentGateway}.
 *
 * <p>This class does <em>not</em> replace or rewrite any Airtel code — it only translates
 * between the canonical {@link GatewayPaymentRequest}/{@link GatewayPaymentResponse} DTOs
 * and the Airtel-specific types. The underlying Http/Mock client is injected by profile
 * as before.
 *
 * <p>MTN numbers are rejected eagerly: Airtel's direct API cannot process them, and a
 * clear error is better than a cryptic Airtel API failure downstream.
 *
 * <p>Active only when an Airtel profile is loaded ("airtel" or "mock-airtel").
 * This prevents an unsatisfied dependency on {@link AirtelMoneyClient} when running
 * with a PawaPay-only profile.
 */
@Slf4j
@Component
@Profile({"airtel", "mock-airtel"})
@RequiredArgsConstructor
public class AirtelDirectGateway implements PaymentGateway {

    private final AirtelMoneyClient airtelClient;

    @Value("${airtel.country:ZM}")
    private String country;

    @Value("${airtel.currency:ZMW}")
    private String currency;

    @Override
    public GatewayName getGatewayName() {
        return GatewayName.AIRTEL_DIRECT;
    }

    @Override
    public Mono<GatewayPaymentResponse> initiatePayment(GatewayPaymentRequest request) {
        if (request.getMobileNetwork() == MobileNetwork.MTN) {
            return Mono.error(new PaymentGatewayException(
                    "Airtel Direct cannot process MTN numbers. " +
                    "Please switch to PawaPay or choose an Airtel number."));
        }

        AirtelPushRequest pushReq = AirtelPushRequest.builder()
                .msisdn(request.getMsisdn())
                .reference(request.getOrderUuid())
                .amount(request.getAmount())
                .country(country)
                .currency(currency)
                .build();

        return airtelClient.initiateUssdPush(pushReq)
                .flatMap(resp -> {
                    if (resp.getAirtelTransactionId() == null || resp.getAirtelTransactionId().isBlank()) {
                        return Mono.error(new PaymentGatewayException(
                                "Airtel Direct did not return a transaction ID. Please try COD or contact support."));
                    }
                    return Mono.just(GatewayPaymentResponse.builder()
                            .gatewayRef(resp.getAirtelTransactionId())
                            .build());
                });
    }

    @Override
    public Mono<GatewayStatusResponse> checkPaymentStatus(String gatewayRef) {
        return airtelClient.checkPaymentStatus(gatewayRef)
                .map(resp -> GatewayStatusResponse.builder()
                        .gatewayRef(gatewayRef)
                        .outcome(mapAirtelStatus(resp.getStatus()))
                        .rawStatus(resp.getStatus())
                        .build());
    }

    /**
     * Maps Airtel status codes to the canonical outcome.
     * TS  = Transaction Successful
     * TF  = Transaction Failed
     * TA  = Transaction Abandoned (user ignored prompt)
     * TE  = Transaction Error
     * TIP = Transaction In Progress
     * DP_INITIATED = Deposit Push initiated (still pending)
     */
    private GatewayStatusOutcome mapAirtelStatus(String status) {
        if (status == null) return GatewayStatusOutcome.PENDING;
        return switch (status) {
            case "TS"           -> GatewayStatusOutcome.SUCCESS;
            case "TF", "TA", "TE" -> GatewayStatusOutcome.FAILED;
            default             -> GatewayStatusOutcome.PENDING;
        };
    }
}
