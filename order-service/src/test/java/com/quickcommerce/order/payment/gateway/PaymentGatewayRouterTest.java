package com.quickcommerce.order.payment.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PaymentGatewayRouter}.
 */
class PaymentGatewayRouterTest {

    // ─── Stubs ────────────────────────────────────────────────────────────────

    static PaymentGateway stubGateway(GatewayName name) {
        return new PaymentGateway() {
            @Override public GatewayName getGatewayName() { return name; }
            @Override public Mono<GatewayPaymentResponse> initiatePayment(GatewayPaymentRequest req) { return Mono.empty(); }
            @Override public Mono<GatewayStatusResponse> checkPaymentStatus(String ref) { return Mono.empty(); }
        };
    }

    PaymentGatewayRouter routerWith(GatewayName active, PaymentGateway... gateways) {
        PaymentGatewayRouter router = new PaymentGatewayRouter(List.of(gateways));
        ReflectionTestUtils.setField(router, "activeGateway", active);
        router.validate();
        return router;
    }

    // ─── resolve() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolve() returns the active gateway")
    void resolve_shouldReturnActiveGateway() {
        PaymentGateway pawaPayGateway = stubGateway(GatewayName.PAWAPAY);
        PaymentGateway airtelGateway  = stubGateway(GatewayName.AIRTEL_DIRECT);

        PaymentGatewayRouter router = routerWith(GatewayName.PAWAPAY, pawaPayGateway, airtelGateway);

        assertThat(router.resolve().getGatewayName()).isEqualTo(GatewayName.PAWAPAY);
    }

    @Test
    @DisplayName("resolve() returns AIRTEL_DIRECT when that is the active gateway")
    void resolve_shouldReturnAirtelWhenActive() {
        PaymentGateway pawaPayGateway = stubGateway(GatewayName.PAWAPAY);
        PaymentGateway airtelGateway  = stubGateway(GatewayName.AIRTEL_DIRECT);

        PaymentGatewayRouter router = routerWith(GatewayName.AIRTEL_DIRECT, pawaPayGateway, airtelGateway);

        assertThat(router.resolve().getGatewayName()).isEqualTo(GatewayName.AIRTEL_DIRECT);
    }

    // ─── resolve(GatewayName) ─────────────────────────────────────────────────

    @Test
    @DisplayName("resolve(name) always returns the named gateway regardless of active")
    void resolveByName_shouldReturnSpecificGateway() {
        PaymentGateway pawaPayGateway = stubGateway(GatewayName.PAWAPAY);
        PaymentGateway airtelGateway  = stubGateway(GatewayName.AIRTEL_DIRECT);

        // Active is PAWAPAY but we explicitly ask for AIRTEL_DIRECT (failsafe use case)
        PaymentGatewayRouter router = routerWith(GatewayName.PAWAPAY, pawaPayGateway, airtelGateway);

        assertThat(router.resolve(GatewayName.AIRTEL_DIRECT).getGatewayName())
                .isEqualTo(GatewayName.AIRTEL_DIRECT);
        assertThat(router.resolve(GatewayName.PAWAPAY).getGatewayName())
                .isEqualTo(GatewayName.PAWAPAY);
    }

    @Test
    @DisplayName("resolve(name) throws when named gateway not registered")
    void resolveByName_unknownGateway_shouldThrow() {
        PaymentGateway airtelGateway = stubGateway(GatewayName.AIRTEL_DIRECT);

        PaymentGatewayRouter router = routerWith(GatewayName.AIRTEL_DIRECT, airtelGateway);

        assertThatThrownBy(() -> router.resolve(GatewayName.PAWAPAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAWAPAY");
    }

    // ─── validate() (PostConstruct) ───────────────────────────────────────────

    @Test
    @DisplayName("validate() throws when active gateway has no registered bean")
    void validate_missingActiveGateway_shouldThrowAtStartup() {
        PaymentGateway airtelGateway = stubGateway(GatewayName.AIRTEL_DIRECT);

        // Only Airtel is registered but active is PAWAPAY — misconfiguration
        PaymentGatewayRouter router = new PaymentGatewayRouter(List.of(airtelGateway));
        ReflectionTestUtils.setField(router, "activeGateway", GatewayName.PAWAPAY);

        assertThatThrownBy(router::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAWAPAY")
                .hasMessageContaining("no registered bean");
    }

    @Test
    @DisplayName("all registered gateways are accessible by name after construction")
    void allGateways_shouldBeAccessibleByName() {
        PaymentGateway pawaPayGateway = stubGateway(GatewayName.PAWAPAY);
        PaymentGateway airtelGateway  = stubGateway(GatewayName.AIRTEL_DIRECT);

        PaymentGatewayRouter router = routerWith(GatewayName.PAWAPAY, pawaPayGateway, airtelGateway);

        assertThat(router.resolve(GatewayName.PAWAPAY)).isSameAs(pawaPayGateway);
        assertThat(router.resolve(GatewayName.AIRTEL_DIRECT)).isSameAs(airtelGateway);
    }
}
