package com.quickcommerce.order.payment.gateway.pawapay;

import com.quickcommerce.order.payment.gateway.GatewayName;
import com.quickcommerce.order.payment.gateway.GatewayPaymentRequest;
import com.quickcommerce.order.payment.gateway.GatewayPaymentResponse;
import com.quickcommerce.order.payment.gateway.GatewayStatusOutcome;
import com.quickcommerce.order.payment.gateway.GatewayStatusResponse;
import com.quickcommerce.order.payment.gateway.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Mock PawaPay gateway for local development and integration tests.
 * Active when Spring profile includes "mock-pawapay".
 *
 * Behaviour:
 * - {@link #initiatePayment} returns the orderUuid as the depositId immediately
 *   (mirrors real PawaPay: we supply the depositId ourselves)
 * - {@link #checkPaymentStatus} returns COMPLETED (SUCCESS) to simulate the
 *   customer accepting the prompt — same as MockAirtelMoneyClient's behaviour
 *
 * To test a FAILED path, POST the PawaPay webhook manually with status "FAILED".
 */
@Slf4j
@Component
@Profile("mock-pawapay")
public class MockPawaPayGateway implements PaymentGateway {

    @Override
    public GatewayName getGatewayName() {
        return GatewayName.PAWAPAY;
    }

    @Override
    public Mono<GatewayPaymentResponse> initiatePayment(GatewayPaymentRequest request) {
        log.info("[MOCK-PAWAPAY] Deposit initiated — depositId={}, msisdn={}, amount={} {}, network={}",
                request.getOrderUuid(), request.getMsisdn(),
                request.getAmount(), request.getCurrency(), request.getMobileNetwork());

        return Mono.just(GatewayPaymentResponse.builder()
                .gatewayRef(request.getOrderUuid())
                .build());
    }

    @Override
    public Mono<GatewayStatusResponse> checkPaymentStatus(String gatewayRef) {
        log.info("[MOCK-PAWAPAY] Status check for depositId={} → returning COMPLETED", gatewayRef);

        return Mono.just(GatewayStatusResponse.builder()
                .gatewayRef(gatewayRef)
                .outcome(GatewayStatusOutcome.SUCCESS)
                .rawStatus("COMPLETED")
                .build());
    }
}
