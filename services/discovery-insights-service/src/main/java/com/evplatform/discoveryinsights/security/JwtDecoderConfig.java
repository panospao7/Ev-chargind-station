package com.evplatform.discoveryinsights.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;

/**
 * JWT decoder for the internal discovery API (I1-IAM-002, SEC-001 §8.3).
 *
 * <p>Validation chain (SEC-001 §8.2 token rules — issuer, expiry, audience,
 * type; plus the recorded azp client-allowlist decision):</p>
 * <ul>
 *   <li>{@link JwtTimestampValidator} — exp/nbf freshness with a 60s clock
 *       skew allowance ({@code setAllowEmptyNotBeforeClaim(false)} so a
 *       token without nbf is not silently accepted);</li>
 *   <li>{@link JwtIssuerValidator} — {@code iss} must be the configured
 *       realm issuer;</li>
 *   <li>audience — {@code aud} must contain this service's client id
 *       ("tokens issued for one audience are rejected by another",
 *       SEC-001 §8.2/§8.3); failures are marked in
 *       {@link #AUDIENCE_VALIDATION} so the entry point can emit
 *       TOKEN_AUDIENCE_INVALID instead of the generic TOKEN_INVALID;</li>
 *   <li>{@link JwtTypeValidator} — {@code typ} must be {@code Bearer}
 *       (Keycloak access-token default); ID tokens carry a different typ
 *       and are rejected (SEC-001 §8.2 "ID Tokens are not accepted as API
 *       access tokens");</li>
 *   <li>{@link JwtClaimValidator} on {@code azp} — the authorized party
 *       must be an allowlisted client. Keycloak client-credentials tokens
 *       carry {@code azp} equal to the requesting client id, so the
 *       allowlist is {ev-bff (after token exchange), svc-discovery-insights
 *       (direct service calls)}; a missing {@code azp} is rejected
 *       (asserted empirically by ResourceServerValidationTests).</li>
 * </ul>
 *
 * <p>Signature validation is implicit: the Nimbus decoder verifies RS256
 * signatures against the realm JWKS
 * ({@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}); an
 * invalid signature fails before the validator chain runs.</p>
 *
 * <p>Audience-failure signaling: the validator chain has no access to the
 * servlet request, so the audience validator records its outcome in a
 * thread-local that the entry point reads on the same servlet thread
 * (single-threaded request dispatch; reset on every decode to prevent
 * cross-request carryover).</p>
 */
@Configuration
public class JwtDecoderConfig {

    /**
     * Thread-local flag set when the audience validator rejects a token;
     * read by the authentication entry point to choose
     * TOKEN_AUDIENCE_INVALID over the generic TOKEN_INVALID.
     */
    public static final ThreadLocal<Boolean> AUDIENCE_VALIDATION =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Bean
    public JwtDecoder jwtDecoder(ResourceServerProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
                properties.jwkSetUri()).build();

        JwtAudienceValidator audience = new JwtAudienceValidator(properties.audience());
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            AUDIENCE_VALIDATION.set(Boolean.FALSE);
            OAuth2TokenValidatorResult result = audience.validate(jwt);
            if (!result.getErrors().isEmpty()) {
                AUDIENCE_VALIDATION.set(Boolean.TRUE);
            }
            return result;
        };

        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ofSeconds(60));
        timestamp.setAllowEmptyNotBeforeClaim(false);

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                timestamp,
                new JwtIssuerValidator(properties.issuer()),
                audienceValidator,
                new JwtTypeValidator("Bearer"),
                new JwtClaimValidator<>("azp",
                        azp -> azp != null && properties.allowedClients().contains(azp.toString())));

        decoder.setJwtValidator(validator);
        return decoder;
    }
}
