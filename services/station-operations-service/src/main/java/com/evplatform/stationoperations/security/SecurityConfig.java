package com.evplatform.stationoperations.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

/**
 * Internal-API resource-server security configuration (I1-IAM-002,
 * SEC-001 §8.3).
 *
 * <p><b>Chain scoping decision:</b> the protected set is exactly the
 * internal booking-operations API from station-operations-internal-api-v1.yaml
 * — every method on {@code /internal/v1/**} (today:
 * {@code POST /internal/v1/booking-operations/impact-previews},
 * {@code GET /internal/v1/booking-operations/capacity-restrictions/{ref}},
 * {@code POST /internal/v1/booking-operations/capacity-restrictions/{ref}/reconciliation-requests};
 * the wildcard covers the internal API surface as it grows with later
 * tasks). The service has NO other HTTP endpoints today — the management
 * endpoints ({@code /actuator/health}) stay permitAll and {@code anyRequest}
 * stays permitAll so nothing else changes behavior. No existing endpoint
 * becomes authenticated by this change; the internal controllers do not
 * exist yet (test-only fixtures exercise the chain).</p>
 *
 * <p><b>Error contract</b> (RFC 9457 problem+json, base URI matches the
 * platform convention {@code https://api.evplatform.example/problems/}):</p>
 * <ul>
 *   <li>401 TOKEN_INVALID — missing/malformed/expired/wrong-issuer/
 *       wrong-typ/bad-signature/wrong-azp bearer tokens;</li>
 *   <li>401 TOKEN_AUDIENCE_INVALID — structurally valid token whose
 *       {@code aud} does not contain this service (audience-limited edge
 *       tokens, SEC-001 §8.3);</li>
 *   <li>403 INSUFFICIENT_SCOPE — authenticated token without the required
 *       {@code SCOPE_station-operations:internal:access} authority.</li>
 * </ul>
 *
 * <p>{@code @Configuration} is declared explicitly: in Spring Security
 * 7.1.1 {@code @EnableWebSecurity} is no longer meta-annotated with
 * {@code @Configuration} (same lesson as the BFF SecurityConfig,
 * I1-IAM-001 phase 2).</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Problem type URI base — matches the platform/BFF convention. */
    static final String PROBLEM_BASE = "https://api.evplatform.example/problems/";

    @Bean
    public SecurityFilterChain staInternalApiSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

        http
                // Stateless bearer-only resource server: the internal
                // booking-operations API authenticates with Authorization:
                // Bearer (serviceToken security scheme in
                // station-operations-internal-api-v1.yaml); no cookie-based
                // ambient credential exists on this API, so CSRF protection
                // is inapplicable and its default would reject the contract's
                // POST operations with an unrelated 403.
                .csrf(org.springframework.security.config.annotation.web.configurers.CsrfConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // ---- internal API (the protected set, SEC-001 §8.3) ----
                        .requestMatchers("/internal/v1/**")
                                .hasAuthority("SCOPE_station-operations:internal:access")
                        // ---- preserved existing behavior ----
                        .requestMatchers("/actuator/health").permitAll()
                        // everything else keeps today's anonymous behavior;
                        // the internal paths above are the protected set.
                        .anyRequest().permitAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(new ResourceServerAuthenticationEntryPoint())
                        .accessDeniedHandler(new InsufficientScopeAccessDeniedHandler()));

        return http.build();
    }

    /**
     * Scope claim → {@code SCOPE_*} authorities. The default
     * {@link org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter}
     * maps the {@code scope} (and {@code scp}) claim to
     * {@code SCOPE_<scope>} authorities — exactly the authority form
     * {@code hasAuthority("SCOPE_…")} matches.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new org.springframework.security.oauth2.server.resource.authentication
                        .JwtGrantedAuthoritiesConverter());
        return converter;
    }

    /**
     * 401 entry point. Emits TOKEN_INVALID generically; when the decoder's
     * audience validator flagged the failure (thread-local bridge,
     * {@link JwtDecoderConfig#AUDIENCE_VALIDATION}) it emits
     * TOKEN_AUDIENCE_INVALID instead. Both are 401 problem+json with the
     * registry code as title and the code-derived type URI.
     */
    static final class ResourceServerAuthenticationEntryPoint
            implements org.springframework.security.web.AuthenticationEntryPoint {

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             org.springframework.security.core.AuthenticationException authException)
                throws IOException {
            boolean audienceFailure = Boolean.TRUE.equals(
                    JwtDecoderConfig.AUDIENCE_VALIDATION.get());
            JwtDecoderConfig.AUDIENCE_VALIDATION.set(Boolean.FALSE);
            if (audienceFailure) {
                writeProblem(response, HttpStatus.UNAUTHORIZED, "TOKEN_AUDIENCE_INVALID",
                        "The token audience does not match this service.");
            } else {
                writeProblem(response, HttpStatus.UNAUTHORIZED, "TOKEN_INVALID",
                        "The presented credential is invalid.");
            }
        }
    }

    /** 403 INSUFFICIENT_SCOPE for authenticated tokens lacking the scope. */
    static final class InsufficientScopeAccessDeniedHandler
            implements org.springframework.security.web.access.AccessDeniedHandler {

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           org.springframework.security.access.AccessDeniedException accessDeniedException)
                throws IOException {
            writeProblem(response, HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE",
                    "The token lacks a required scope for this operation.");
        }
    }

    /** RFC 9457 problem+json writer (fixed strings only — no injection). */
    static void writeProblem(HttpServletResponse response, HttpStatus status,
                             String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        String json = "{\"type\":\"" + PROBLEM_BASE + code.toLowerCase()
                + "\",\"title\":\"" + code + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\"}";
        response.getWriter().write(json);
    }
}
