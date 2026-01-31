package com.quickcommerce.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.JWSAlgorithm;

/**
 * Security configuration for WebFlux (disabled for test profile)
 */
@Configuration
@EnableWebFluxSecurity
@Profile("!test")
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder())))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        byte[] secretKey = "dummy-secret-key-for-local-dev-must-be-long-enough".getBytes();
        return NimbusReactiveJwtDecoder.withSecretKey(new SecretKeySpec(secretKey, "HmacSHA256")).build();
    }
}
