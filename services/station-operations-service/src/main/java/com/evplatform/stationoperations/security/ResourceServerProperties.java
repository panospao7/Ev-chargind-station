package com.evplatform.stationoperations.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Internal-API resource-server validation properties (I1-IAM-002,
 * SEC-001 §8.3). Bound from the top-level {@code app.security.*}
 * configuration properties (top level, NOT under {@code spring:} — the
 * same @Value binding rule the {@code outbox.*} properties follow).
 *
 * <ul>
 *   <li>{@code issuer} — the Keycloak realm issuer every presented JWT
 *       must carry (SEC-001 §8.2 issuer validation).</li>
 *   <li>{@code audience} — this service's client id; tokens minted for
 *       another audience are rejected (SEC-001 §8.2/§8.3 audience-limited
 *       edge tokens).</li>
 *   <li>{@code allowedClients} — accepted {@code azp} values (the BFF after
 *       token exchange, and this service's own client id for direct
 *       client-credentials calls; SEC-001 §9.1 unique client identity).</li>
 *   <li>{@code requiredScope} — the scope the internal booking-operations
 *       API demands (SEC-001 §8.4 analog, recorded I1-IAM-002 decision).</li>
 * </ul>
 */
@Component
public class ResourceServerProperties {

    private final String issuer;
    private final String audience;
    private final List<String> allowedClients;
    private final String requiredScope;
    private final String jwkSetUri;

    public ResourceServerProperties(
            @Value("${app.security.issuer}") String issuer,
            @Value("${app.security.audience}") String audience,
            @Value("${app.security.allowed-clients}") List<String> allowedClients,
            @Value("${app.security.required-scope}") String requiredScope,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        this.issuer = issuer;
        this.audience = audience;
        this.allowedClients = List.copyOf(allowedClients);
        this.requiredScope = requiredScope;
        this.jwkSetUri = jwkSetUri;
    }

    public String jwkSetUri() {
        return jwkSetUri;
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    public List<String> allowedClients() {
        return allowedClients;
    }

    public String requiredScope() {
        return requiredScope;
    }
}
