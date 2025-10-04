package com.quickcommerce.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

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
                        .pathMatchers("/api/v1/inventory/health").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/api/v1/inventory/public/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder())))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // This should be configured with your actual JWT issuer URI
        // For now, returning null to allow the application to start
        // In production, configure with: NimbusReactiveJwtDecoder.withJwkSetUri("your-jwt-issuer-uri").build();
        return null;
    }
}
