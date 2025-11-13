package com.adtracker.webhookingest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the webhook ingestion service.
 *
 * Configures Spring Security with appropriate settings for a stateless
 * webhook receiver API. CSRF is disabled for webhook endpoints while
 * maintaining security for other aspects.
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    /**
     * Configure the security filter chain.
     *
     * Key security features:
     * - Stateless session management (no session cookies)
     * - CSRF disabled for webhook endpoints (POST from external systems)
     * - Actuator endpoints protected with authentication
     * - Webhook endpoints publicly accessible but with validation
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Webhook endpoints - publicly accessible
                        .requestMatchers("/api/v1/webhooks/**").permitAll()
                        // Actuator health endpoints - publicly accessible
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Other actuator endpoints - require authentication
                        .requestMatchers("/actuator/**").authenticated()
                        // All other endpoints - require authentication
                        .anyRequest().authenticated()
                );

        log.info("Security filter chain configured successfully");
        return http.build();
    }
}
