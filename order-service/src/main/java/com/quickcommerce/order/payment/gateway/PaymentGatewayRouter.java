package com.quickcommerce.order.payment.gateway;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Selects the correct {@link PaymentGateway} at runtime.
 *
 * <p><b>For new payments:</b> {@link #resolve()} returns the gateway configured by
 * {@code payment.active-gateway} (default: {@code PAWAPAY}).
 *
 * <p><b>For the failsafe scheduler:</b> {@link #resolve(GatewayName)} always returns
 * the named gateway — essential when the active gateway has been switched but old
 * {@code payment_attempts} rows still reference a different provider.
 *
 * <p><b>Fail-fast:</b> {@link #validate()} throws {@code IllegalStateException} at
 * startup if the configured active gateway has no registered bean, preventing silent
 * payment failures at runtime.
 */
@Slf4j
@Component
public class PaymentGatewayRouter {

    @Value("${payment.active-gateway:PAWAPAY}")
    private GatewayName activeGateway;

    private final Map<GatewayName, PaymentGateway> registry;

    public PaymentGatewayRouter(List<PaymentGateway> gateways) {
        registry = new EnumMap<>(GatewayName.class);
        for (PaymentGateway gateway : gateways) {
            registry.put(gateway.getGatewayName(), gateway);
            log.info("Registered payment gateway: {}", gateway.getGatewayName());
        }
    }

    @PostConstruct
    void validate() {
        if (!registry.containsKey(activeGateway)) {
            throw new IllegalStateException(
                    "Active payment gateway '" + activeGateway + "' has no registered bean. " +
                    "Check that the correct Spring profile is active. Registered gateways: " + registry.keySet());
        }
        log.info("Active payment gateway: {}", activeGateway);
    }

    /** Returns the gateway for processing new payments. */
    public PaymentGateway resolve() {
        return registry.get(activeGateway);
    }

    /**
     * Returns the gateway by name regardless of which one is currently active.
     * Used by the failsafe scheduler to route status-check calls to the provider
     * that originally processed a given {@code payment_attempts} row.
     */
    public PaymentGateway resolve(GatewayName name) {
        PaymentGateway gateway = registry.get(name);
        if (gateway == null) {
            throw new IllegalStateException(
                    "No registered gateway for '" + name + "'. " +
                    "Cannot check status for old attempts — manual reconciliation required.");
        }
        return gateway;
    }
}
