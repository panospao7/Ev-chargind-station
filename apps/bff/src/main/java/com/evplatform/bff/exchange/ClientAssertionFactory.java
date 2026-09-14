package com.evplatform.bff.exchange;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Factory for the ev-bff {@code private_key_jwt} client assertion (RFC 7523,
 * SEC-001 §4.2/§5.1) used to authenticate the token-exchange request at the
 * Keycloak token endpoint (I1-IAM-002, SEC-P03).
 *
 * <p>The assertion is an RS256-signed JWT over the SAME ev-bff client key
 * used for the authorization-code flow ({@code ClientKeyConfig#BFF_CLIENT_JWK_BEAN},
 * RFC 7638 thumbprint kid): claims {@code iss = sub = "ev-bff"},
 * {@code aud = <issuer>/protocol/openid-connect/token} (the token endpoint,
 * per RFC 7523 §3 aud requirements as implemented by Keycloak),
 * {@code exp = now + 60s} (SEC-001 §8.1 assertion lifetime cap),
 * {@code iat = now}, and a unique {@code jti} (SEC-001 §8.1: replay
 * protection — a new jti per assertion).</p>
 *
 * <p>The serialized assertion is SECRET-equivalent material: it is handed
 * only to {@link TokenExchangeClient} for the token request and is NEVER
 * logged (ARC-SEC-21).</p>
 */
@Component
public class ClientAssertionFactory {

    /** ev-bff client id (assertion iss/sub). */
    public static final String CLIENT_ID = "ev-bff";

    /** Assertion lifetime (SEC-001 §8.1: ≤ 60s). */
    private static final long ASSERTION_TTL_SECONDS = 60;

    private final RSAKey bffClientJwk;
    private final Clock clock;
    private final String tokenEndpointAudience;

    /**
     * @param bffClientJwk          the ev-bff signing key (kid = RFC 7638
     *                              thumbprint, private key attached)
     * @param clock                 injectable clock for exp/iat
     * @param issuer                the Keycloak issuer (same configured
     *                              source as {@code BackChannelLogoutController})
     */
    public ClientAssertionFactory(RSAKey bffClientJwk,
                                  Clock clock,
                                  @org.springframework.beans.factory.annotation.Value(
                                          "${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
                                  String issuer) {
        this.bffClientJwk = bffClientJwk;
        this.clock = clock;
        this.tokenEndpointAudience = issuer + "/protocol/openid-connect/token";
    }

    /**
     * Creates a fresh signed client assertion. Each call produces a new
     * {@code jti} and current {@code iat}/{@code exp} — never cache or
     * reuse a serialized assertion across requests.
     *
     * @return the compact JWS serialization (never logged)
     */
    public String createAssertion() {
        Instant now = Instant.now(clock);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(CLIENT_ID)
                .subject(CLIENT_ID)
                .audience(tokenEndpointAudience)
                .expirationTime(Date.from(now.plusSeconds(ASSERTION_TTL_SECONDS)))
                .issueTime(Date.from(now))
                .jwtID(UUID.randomUUID().toString())
                .build();
        // The realm pins "token.endpoint.auth.signing.alg": "RS256"; the
        // header kid lets Keycloak select the key from the registered JWKS.
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(bffClientJwk.getKeyID())
                .build();
        SignedJWT signedJwt = new SignedJWT(header, claims);
        try {
            signedJwt.sign(new RSASSASigner(bffClientJwk));
        } catch (JOSEException e) {
            // Exception text carries no key material; the message is fixed.
            throw new IllegalStateException("Client assertion signing failed", e);
        }
        return signedJwt.serialize();
    }
}
