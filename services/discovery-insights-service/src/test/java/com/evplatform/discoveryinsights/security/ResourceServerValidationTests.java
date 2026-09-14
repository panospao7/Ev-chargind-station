package com.evplatform.discoveryinsights.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I1-IAM-002 (SEC-001 §8.3): resource-server validation of the internal
 * discovery API, proven end-to-end through the REAL security chain
 * (full Spring context, real SecurityFilterChain, real JwtDecoder against
 * a locally-served test JWKS) with locally-minted RSA JWTs — deterministic,
 * no live Keycloak dependency.
 *
 * <p>Driven through MockMvc with
 * {@code MockMvcBuilders.webAppContextSetup(...).apply(springSecurity())}
 * (the proven Boot 4.1 pattern from the BFF proxy tests — Boot 4.1 has no
 * TestRestTemplate/@AutoConfigureMockMvc in test-autoconfigure; the
 * springSecurity() setup installs the real SecurityFilterChain as a filter
 * so the bearer-token filter, decoder, validator chain, converter, and
 * authorizeHttpRequests rules are all exercised on the same thread, which
 * also keeps the decoder→entry-point thread-local bridge honest).</p>
 *
 * <p>Coverage: positive authorization (correct aud + scope + azp → 200),
 * wrong audience → 401 TOKEN_AUDIENCE_INVALID, missing scope → 403
 * INSUFFICIENT_SCOPE, user token (no service audience) → 401, ID token
 * (typ) → 401, expired → 401, wrong issuer → 401, bad signature → 401,
 * azp not in the allowed set → 401, public path stays anonymous → 200,
 * actuator health → 200.</p>
 *
 * <p>The JWKS endpoint is a tiny {@code com.sun.net.httpserver} serving the
 * test realm key. The issuer/audience/azp/scope expectations are pinned via
 * {@code @DynamicPropertySource}; the Nimbus decoder fetches the JWKS over
 * HTTP exactly as it would against Keycloak. The public-path collaborator
 * ({@link com.evplatform.discoveryinsights.projection.StationSearchProjectionReader})
 * is mocked so the public-path scenario proves the AUTH dimension (200 for
 * an anonymous caller) without a database.</p>
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        // Container-less MockMvc context: no PostgreSQL/RabbitMQ is running,
        // so the corresponding health indicators would report DOWN and the
        // anonymous-health scenario would see 503 instead of 200.
        "management.health.db.enabled=false",
        "management.health.rabbit.enabled=false",
        "management.health.redis.enabled=false"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResourceServerValidationTests {

    // ------------------------------------------------------------------
    // Test realm identity (deterministic; the values the config pins)
    // ------------------------------------------------------------------

    private static final String ISSUER = "http://127.0.0.1:8180/realms/ev-local";
    private static final String AUDIENCE = "svc-discovery-insights";
    private static final List<String> ALLOWED_CLIENTS = List.of(
            "ev-bff", "svc-discovery-insights");
    private static final String SCOPE = "discovery:insights:read";

    /** Test realm RSA key — the public JWK is served over the JWKS endpoint. */
    private static KeyPair realmKeyPair;
    private static RSAKey realmJwk;
    private static HttpServer jwksServer;
    private static String jwksUrl;

    @BeforeAll
    static void startJwksServer() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        realmKeyPair = generator.generateKeyPair();
        realmJwk = new RSAKey.Builder((RSAPublicKey) realmKeyPair.getPublic())
                .privateKey((RSAPrivateKey) realmKeyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("test-realm-key-1")
                .build();

        // Public-only JWK set serialized as REAL JSON (nimbus 10.9.1:
        // JWKSet.toString() delegates to JSONObjectUtils.toJSONString, while
        // toJSONObject().toString() would emit a Java Map toString with '='
        // separators — the "Invalid JSON object" failure seen in the first
        // run).
        String jwksJson = new JWKSet(realmJwk).toPublicJWKSet().toString();
        byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/realms/ev-local/protocol/openid-connect/certs",
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        jwksServer.start();
        jwksUrl = "http://127.0.0.1:" + jwksServer.getAddress().getPort()
                + "/realms/ev-local/protocol/openid-connect/certs";
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
        JwtDecoderConfig.AUDIENCE_VALIDATION.remove();
    }

    @DynamicPropertySource
    static void pinSecurityProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> jwksUrl);
        registry.add("app.security.issuer", () -> ISSUER);
        registry.add("app.security.audience", () -> AUDIENCE);
        registry.add("app.security.allowed-clients", () -> String.join(",", ALLOWED_CLIENTS));
        registry.add("app.security.required-scope", () -> SCOPE);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    /** Public-path collaborator mocked so no database is needed. */
    @MockitoBean
    private com.evplatform.discoveryinsights.projection.StationSearchProjectionReader reader;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        // springSecurity() installs the SecurityFilterChain bean as a filter
        // in the MockMvc chain — the resource-server behavior asserted below
        // is actually exercised; plain webAppContextSetup would bypass the
        // security filter chain entirely.
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // ------------------------------------------------------------------
    // Token minting helpers
    // ------------------------------------------------------------------

    /**
     * Mints an RS256 access token with the given claims overrides against
     * the test realm key. Default shape mirrors a Keycloak client-credentials
     * service token: iss = realm, aud = [target service], azp = requesting
     * client, typ = Bearer, scope = space-separated list, fresh iat/exp/nbf.
     */
    private static SignedJWT mintToken(String issuer, List<String> audience,
                                       String azp, String typ, String scope,
                                       Instant issuedAt, Instant expiresAt,
                                       String subject) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject)
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString());
            if (audience != null) {
                builder.audience(audience);
            }
            if (azp != null) {
                builder.claim("azp", azp);
            }
            if (typ != null) {
                builder.claim("typ", typ);
            }
            if (scope != null) {
                builder.claim("scope", scope);
            }
            if (issuedAt != null) {
                // Keycloak access tokens carry nbf = iat; the decoder's
                // timestamp validator requires nbf (allowEmptyNotBeforeClaim
                // = false), so the minted tokens must carry it too.
                builder.notBeforeTime(Date.from(issuedAt));
            }
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(realmJwk.getKeyID());
            if (typ != null) {
                // JOSE header typ must be present and equal "Bearer" —
                // JwtTypeValidator reads the HEADER typ (verified in the
                // spring-security-oauth2-jose 7.1.1 bytecode), not a body
                // claim; Keycloak access tokens set it. Omitting it would
                // make every token fail the type validator (401 before
                // scope/audience differentiation).
                headerBuilder.type(new com.nimbusds.jose.JOSEObjectType(typ));
            }
            SignedJWT jwt = new SignedJWT(headerBuilder.build(), builder.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) realmKeyPair.getPrivate()));
            return jwt;
        } catch (JOSEException e) {
            throw new IllegalStateException("token minting failed", e);
        }
    }

    /** The default valid service token (correct aud/scope/azp/typ/iss). */
    private static SignedJWT validServiceToken() {
        Instant now = Instant.now();
        return mintToken(ISSUER, List.of(AUDIENCE), "ev-bff", "Bearer", SCOPE,
                now.minusSeconds(10), now.plusSeconds(300), "service-account-ev-bff");
    }

    // ------------------------------------------------------------------
    // 1. Positive: correct aud + scope + azp → 200 on both internal paths
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void validServiceTokenIsAuthorizedOnBothInternalPaths() throws Exception {
        String token = validServiceToken().serialize();

        mockMvc.perform(get("/stations/STATION-1/utilization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"stationRef\":\"STATION-1\"")));

        mockMvc.perform(get("/insights/trends")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-31T00:00:00Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("totalSessions")));
    }

    // ------------------------------------------------------------------
    // 2. Wrong audience → 401 TOKEN_AUDIENCE_INVALID
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void wrongAudienceIsRejectedWithTokenAudienceInvalid() throws Exception {
        Instant now = Instant.now();
        String token = mintToken(ISSUER, List.of("svc-station-operations"),
                "ev-bff", "Bearer", SCOPE,
                now.minusSeconds(10), now.plusSeconds(300), "service-account-ev-bff")
                .serialize();

        mockMvc.perform(get("/stations/STATION-1/utilization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(containsString("TOKEN_AUDIENCE_INVALID")));
    }

    // ------------------------------------------------------------------
    // 3. Missing scope → 403 INSUFFICIENT_SCOPE
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void missingScopeIsRejectedWithInsufficientScope() throws Exception {
        Instant now = Instant.now();
        String token = mintToken(ISSUER, List.of(AUDIENCE), "ev-bff", "Bearer",
                "some:other:scope",
                now.minusSeconds(10), now.plusSeconds(300), "service-account-ev-bff")
                .serialize();

        mockMvc.perform(get("/stations/STATION-1/utilization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(containsString("INSUFFICIENT_SCOPE")));
    }

    // ------------------------------------------------------------------
    // 4. User token (no service audience) → 401
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void userTokenWithoutServiceAudienceIsRejected() throws Exception {
        Instant now = Instant.now();
        // A user-profile token: no aud claim for this service, no azp
        String token = mintToken(ISSUER, null, null, "Bearer", "openid profile",
                now.minusSeconds(10), now.plusSeconds(300), "user-123")
                .serialize();

        mockMvc.perform(get("/stations/STATION-1/utilization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.anyOf(
                        containsString("TOKEN_INVALID"),
                        containsString("TOKEN_AUDIENCE_INVALID"))));
    }

    // ------------------------------------------------------------------
    // 5. ID token (typ) → 401
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void idTokenTypeIsRejected() throws Exception {
        Instant now = Instant.now();
        String token = mintToken(ISSUER, List.of(AUDIENCE), "ev-bff", "ID", SCOPE,
                now.minusSeconds(10), now.plusSeconds(300), "user-123")
                .serialize();

        mockMvc.perform(get("/stations/STATION-1/utilization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("TOKEN_INVALID")));
    }

    // ------------------------------------------------------------------
    // 6. Expired token → 401
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void expiredTokenIsRejected() throws Exception {
        Instant now = Instant.now();
        String token = mintToken(ISSUER, List.of(AUDIENCE), "ev-bff", "Bearer", SCOPE,
                now.minusSeconds(3600), now.minusSeconds(600), "service-account-ev-bff")
                .serialize();

        mockMvc.perform(get("/stations/STATION-1/utilization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("TOKEN_INVALID")));
    }

    // ------------------------------------------------------------------
    // 7. Wrong issuer → 401
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void wrongIssuerIsRejected() throws Exception {
        Instant now = Instant.now();
        String token = mintToken("http://evil.example/realms/other",
                List.of(AUDIENCE), "ev-bff", "Bearer", SCOPE,
                now.minusSeconds(10), now.plusSeconds(300), "service-account-ev-bff")
                .serialize();

        mockMvc.perform(get("/stations/STATION-1/utilization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("TOKEN_INVALID")));
    }

    // ------------------------------------------------------------------
    // 8. Bad signature (key not in the JWKS) → 401
    // ------------------------------------------------------------------

    @Test
    @Order(8)
    void badSignatureIsRejected() throws Exception {
        Instant now = Instant.now();
        // Mint with a DIFFERENT key than the one the JWKS serves
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair rogueKey = generator.generateKeyPair();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rogue-key").build(),
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .audience(AUDIENCE)
                        .subject("service-account-ev-bff")
                        .claim("azp", "ev-bff")
                        .claim("typ", "Bearer")
                        .claim("scope", SCOPE)
                        .issueTime(Date.from(now.minusSeconds(10)))
                        .expirationTime(Date.from(now.plusSeconds(300)))
                        .build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) rogueKey.getPrivate()));

        mockMvc.perform(get("/stations/STATION-1/utilization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.serialize())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("TOKEN_INVALID")));
    }

    // ------------------------------------------------------------------
    // 9. azp not in the allowed set → 401
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    void azpOutsideAllowlistIsRejected() throws Exception {
        Instant now = Instant.now();
        String token = mintToken(ISSUER, List.of(AUDIENCE), "rogue-client", "Bearer", SCOPE,
                now.minusSeconds(10), now.plusSeconds(300), "service-account-rogue")
                .serialize();

        mockMvc.perform(get("/stations/STATION-1/utilization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("TOKEN_INVALID")));
    }

    // ------------------------------------------------------------------
    // 10. Public path stays anonymous → 200
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void publicPathStaysAnonymous() throws Exception {
        // The public discovery API keeps its anonymous contract. The reader
        // collaborator is mocked (empty projection list), so a 200 proves
        // both the AUTH dimension (no 401/403 for a missing token) and the
        // full anonymous request path — without a database.
        when(reader.listStations(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stations")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // ------------------------------------------------------------------
    // 11. Actuator health stays anonymous → 200
    // ------------------------------------------------------------------

    @Test
    @Order(11)
    void actuatorHealthStaysAnonymous() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
