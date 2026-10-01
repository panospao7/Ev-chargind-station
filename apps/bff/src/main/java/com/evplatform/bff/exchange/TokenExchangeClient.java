package com.evplatform.bff.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * Keycloak Standard Token Exchange V2 client (SEC-001 §7.1, SEC-P03).
 *
 * Exchanges the BFF's user-session access token for an audience-limited
 * downstream token: one exchange per target service, the human subject /
 * acr / auth-time preserved by the IdP, ev-bff as the authorized party, no
 * refresh material in the response (packet AC-01). Client authentication is
 * {@code private_key_jwt} with the same ev-bff RSA key used for the
 * authorization-code flow (unique key per client, §8.1; assertions ≤60 s
 * with unique jti, §8.2).
 */
@Component
public class TokenExchangeClient {

    public static final String GRANT_TYPE =
            "urn:ietf:params:oauth:grant-type:token-exchange";
    public static final String JWT_BEARER =
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    public static final String ACCESS_TOKEN_TYPE =
            "urn:ietf:params:oauth:token-type:access_token";

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final RSAKey bffClientJwk;
    private final String issuer;
    private final String bffClientId;
    private final long assertionLifetimeSeconds;

    public TokenExchangeClient(com.nimbusds.jose.jwk.RSAKey bffClientJwk,
                               @Value("${bff.oauth.issuer-url:http://127.0.0.1:8180/realms/ev-local}") String issuer,
                               @Value("${bff.oauth.client-id:ev-bff}") String bffClientId,
                               @Value("${bff.exchange.assertion-lifetime-seconds:60}") long assertionLifetimeSeconds) {
        this.bffClientJwk = bffClientJwk;
        this.issuer = stripTrailingSlash(issuer);
        this.bffClientId = bffClientId;
        this.assertionLifetimeSeconds = assertionLifetimeSeconds;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    /** One exchange result. */
    public record ExchangedToken(String accessToken, long expiresInSeconds) {
    }

    /** Exchange failure with the token-endpoint error semantics. */
    public static class ExchangeFailedException extends RuntimeException {
        public final int statusCode;
        public final String oauthError;

        public ExchangeFailedException(int statusCode, String oauthError, String body) {
            super("token exchange failed: HTTP " + statusCode
                    + (oauthError == null ? "" : " (" + oauthError + ")")
                    + (body == null ? "" : " detail=" + body));
            this.statusCode = statusCode;
            this.oauthError = oauthError;
        }
    }

    /**
     * Exchanges {@code subjectToken} for a token for {@code targetAudience}.
     *
     * @throws ExchangeFailedException on any non-200 token-endpoint response
     *         (the §7.3 negative proofs assert against these semantics)
     */
    public ExchangedToken exchange(String subjectToken, String targetAudience)
            throws Exception {
        String form = "grant_type=" + urlEncode(GRANT_TYPE)
                + "&client_id=" + urlEncode(bffClientId)
                + "&client_assertion_type=" + urlEncode(JWT_BEARER)
                + "&client_assertion=" + urlEncode(clientAssertion())
                + "&subject_token=" + urlEncode(subjectToken)
                + "&subject_token_type=" + urlEncode(ACCESS_TOKEN_TYPE)
                + "&requested_token_type=" + urlEncode(ACCESS_TOKEN_TYPE)
                + "&audience=" + urlEncode(targetAudience)
                + "&scope=openid";

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(issuer + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString());
        JsonNode body = mapper.readTree(response.body());
        if (response.statusCode() != 200) {
            throw new ExchangeFailedException(response.statusCode(),
                    body.path("error").asText(null), response.body());
        }
        String accessToken = body.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new ExchangeFailedException(response.statusCode(),
                    "invalid_response", response.body());
        }
        return new ExchangedToken(accessToken,
                body.path("expires_in").asLong(0));
    }

    /**
     * Signs a private_key_jwt client assertion (§8.2): iss/sub = ev-bff,
     * aud = issuer, jti unique, lifetime ≤ configured cap (default 60 s).
     */
    String clientAssertion() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(bffClientId)
                .subject(bffClientId)
                .audience(issuer)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date(now * 1000))
                .expirationTime(new Date((now + assertionLifetimeSeconds) * 1000))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(bffClientJwk.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(bffClientJwk.toRSAPrivateKey()));
        return jwt.serialize();
    }

    private static String urlEncode(String v) {
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
