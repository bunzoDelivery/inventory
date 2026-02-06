package com.quickcommerce.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for the search service
 * - Admin endpoints require authentication
 * - Public search endpoint is accessible without auth
 * - Actuator endpoints accessible for health checks
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    /**
     * Configure security filter chain
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for REST APIs
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints
                .pathMatchers("/search/**").permitAll()
                .pathMatchers("/actuator/health/**").permitAll()
                .pathMatchers("/actuator/info").permitAll()
                .pathMatchers("/actuator/metrics/**").permitAll()
                // Admin endpoints require authentication
                .pathMatchers("/admin/**").authenticated()
                // All other endpoints require authentication
                .anyExchange().authenticated()
            )
            .httpBasic(basic -> {}) // Enable HTTP Basic Authentication
            .build();
    }

    /**
     * Configure password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configure user details service with hardcoded admin users
     * In production, this should be replaced with database-backed user service
     */
    @Bean
    public MapReactiveUserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("admin123"))
            .roles("ADMIN")
            .build();

        UserDetails operator = User.builder()
            .username("operator")
            .password(passwordEncoder.encode("operator123"))
            .roles("OPERATOR")
            .build();

        return new MapReactiveUserDetailsService(admin, operator);
    }
}
