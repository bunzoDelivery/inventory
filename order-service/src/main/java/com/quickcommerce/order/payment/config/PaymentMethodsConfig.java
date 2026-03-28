package com.quickcommerce.order.payment.config;

import com.quickcommerce.order.domain.PaymentMethod;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Controls which payment methods are visible to the checkout UI.
 *
 * <p>
 * These flags are <em>independent</em> of {@code payment.active-gateway}. The
 * active
 * gateway determines which backend provider processes mobile-money pushes;
 * these flags
 * determine what the UI offers to the customer.
 *
 * <p>
 * Configuration map prefix: {@code payment.enabled-methods}
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentMethodsConfig {

    /**
     * Map of payment methods and their enabled status.
     * Default to true for all defined methods.
     */
    private Map<PaymentMethod, Boolean> enabledMethods = new EnumMap<>(PaymentMethod.class);

    /**
     * Checks if a specific payment method is enabled.
     * Defaults to true if the method is not present in the map.
     */
    public boolean isEnabled(PaymentMethod method) {
        return enabledMethods.getOrDefault(method, true);
    }
}
