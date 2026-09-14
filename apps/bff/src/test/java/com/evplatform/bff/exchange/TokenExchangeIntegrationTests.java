package com.evplatform.bff.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.evplatform.bff.BffApplication;
import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.SessionLifecycleService;
import com.evplatform.bff.session.SessionLifecycleService.TokenMaterial;
import com.evplatform.libraries.testsupport.LocalDependencies;
import com.evplatform.libraries.testsupport.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * I1-IAM-002 phase 3: RFC 8693 token-exchange integration tests against a
 * REAL Keycloak 26.6 (Testcontainers, digest-pinned, realm imported with
 * generated test JWKS — see {@link KeycloakTestHarness}), through the FULL
 * Spring context and the REAL PostgreSQL session store (same provisioning
 * path as the other BFF test clusters).
 *
 * <p>Coverage:</p>
 * <ul>
 *   <li><b>AC-01</b> — a successful exchange yields an audience-limited
 *       token: aud == exactly [target], sub preserved, azp == ev-bff, acr +
 *       auth_time preserved, exp ≤ iat + 300, typ Bearer, no refresh_token
 *       in the response; the cache path returns a Cached result.</li>
 *   <li><b>N2</b> unknown audience → ExchangeFailure.</li>
 *   <li><b>N4</b> rogue client (no standard-token-exchange attribute) with
 *       client_secret auth → exchange rejected.</li>
 *   <li><b>N5</b> expired self-crafted subject token → rejected.</li>
 *   <li><b>N7</b> single-audience downscope for both configured targets.</li>
 *   <li><b>N8</b> id_token as subject_token → rejected.</li>
 *   <li><b>Cache lifecycle</b> — encrypted metadata entry without plaintext,
 *       cache hit without a second HTTP exchange, expiry-skew re-exchange,
 *       revoked session → session_invalid, cross-session AAD binding
 *       (copied ciphertext fails authentication → treated as miss).</li>
 *   <li><b>DA-1/DA-2</b> — Admin REST round-trip: the imported
 *       standard-token-exchange attribute key and the client
 *       profiles/policies are read back from the live realm.</li>
 * </ul>
 *
 * <p>Controlled substitutions (same pattern as SecP01SessionTests): the
 * OAuth2 client auto-configuration is excluded (it would eagerly resolve the
 * issuer at startup) with in-memory stand-ins — the exchange path under test
 * is {@link TokenExchangeClient} + {@link ClientAssertionFactory}, which read
 * the issuer from the provider property directly; the {@code Clock} bean is
 * a Mockito delegating to a {@link MutableClock}; Flyway runs in
 * {@code @BeforeAll} under the MIGRATOR role (runtime/migrator separation,
 * AGENTS.md persistence rules).</p>
 *
 * <p>Clock realism requirement: the mutable clock MUST start at (approximately)
 * REAL time — unlike the purely self-relative suites — because the
 * {@code client_assertion} (exp = mutable-now + 60s) and the subject token
 * are validated by the REAL Keycloak clock. T0 is therefore captured in
 * {@code @BeforeAll} AFTER the containers are up. Advancing the mutable clock
 * in the cache tests does NOT desynchronize Keycloak-side validity: Keycloak
 * validates token exp against its own real clock, while the BFF evaluates
 * cache/session expiry against the mutable clock.</p>
 *
 * <p>Sessions are created through the REAL {@link SessionLifecycleService}
 * (createSession + rotate, the login-success-handler path) carrying a REAL
 * ROPC access token from the driver test user; no token material is logged
 * (ARC-SEC-21).</p>
 */
@SpringBootTest(classes = { BffApplication.class,
        TokenExchangeIntegrationTests.ExchangeTestConfig.class }, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration",
        "spring.flyway.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TokenExchangeIntegrationTests {

    private static final String PW = "evplatform_dev_only";
    private static final String DB = "bff_session_db";
    private static final String MIGRATOR = "bff_session_migrator";
    private static final String RUNTIME = "bff_session_runtime";
    private static final String SCHEMA = "bff_session";

    static final String AUD_STATION = "svc-station-operations";
    static final String AUD_DISCOVERY = "svc-discovery-insights";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static PostgreSQLContainer pg;
    private static KeycloakTestHarness keycloak;
    private static MutableClock mutableClock;
    private static String issuer;

    /**
     * Start instant of the shared MutableClock — captured in @BeforeAll AFTER
     * the containers are up (see the clock-realism note in the class Javadoc).
     */
    private static Instant t0;

    private static final String TEST_SESSION_KEY_B64;

    static {
        byte[] aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);
        TEST_SESSION_KEY_B64 = Base64.getEncoder().encodeToString(aesKey);
    }

    @DynamicPropertySource
    static void exchangeTestProperties(DynamicPropertyRegistry registry) {
        // Supplier lambdas are resolved during context creation, which happens
        // AFTER @BeforeAll has started both containers and set `issuer`.
        registry.add("bff.session.encryption.keys.v1", () -> TEST_SESSION_KEY_B64);
        registry.add("bff.oauth.client-private-key-path",
                () -> keycloak.bffPrivateKeyPemPath());
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
                () -> issuer);
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://"
                + pg.getHost() + ":" + pg.getMappedPort(5432) + "/" + DB);
        registry.add("spring.datasource.username", () -> RUNTIME);
        registry.add("spring.datasource.password", () -> PW);
    }

    @BeforeAll
    static void startContainersAndMigrate() {
        // Order matters: Keycloak first (it supplies the issuer + key path),
        // then PostgreSQL, then Flyway under the MIGRATOR role, then the
        // clock origin (≈ real time, see class Javadoc).
        keycloak = KeycloakTestHarness.start();
        issuer = keycloak.issuer();
        pg = LocalDependencies.newPostgresWithProvisioning(
                Path.of("..", "..", "infra", "local", "postgres"));
        pg.start();
        Flyway.configure()
                .dataSource("jdbc:postgresql://" + pg.getHost() + ":"
                        + pg.getMappedPort(5432) + "/" + DB, MIGRATOR, PW)
                .locations("filesystem:" + Path.of("..", "..", "apps", "bff",
                        "src", "main", "resources", "db", "migration")
                        .toAbsolutePath().normalize())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .load()
                .migrate();
        t0 = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        mutableClock = MutableClock.at(t0.toString());
    }

    @AfterAll
    static void stopContainers() {
        if (pg != null) {
            pg.stop();
        }
        if (keycloak != null) {
            keycloak.close();
        }
    }

    @BeforeEach
    void setUpClock() {
        Mockito.reset(clock);
        Mockito.when(clock.instant()).thenAnswer(invocation -> mutableClock.instant());
        Mockito.when(clock.getZone()).thenReturn(mutableClock.getZone());
        Mockito.when(clock.withZone(ArgumentMatchers.any())).thenReturn(mutableClock);
        countingClient.reset();
    }

    // ------------------------------------------------------------------
    // Context handles
    // ------------------------------------------------------------------

    @Autowired
    private SessionLifecycleService lifecycle;

    @Autowired
    private ExchangedTokenCache tokenCache;

    @Autowired
    private CountingTokenExchangeClient countingClient;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ClientAssertionFactory assertionFactory;

    /** Overridden Clock bean: every consumer reads the shared MutableClock. */
    @MockitoBean
    private Clock clock;

    // ------------------------------------------------------------------
    // Session helper (real lifecycle service, real store, real ROPC token)
    // ------------------------------------------------------------------

    /** Per-test session record: rotated ref + the ROPC material behind it. */
    record TestSession(String sessionRef, KeycloakTestHarness.RopcTokens ropc) {
    }

    private TestSession newSession() {
        KeycloakTestHarness.RopcTokens ropc = keycloak.ropcDriverTokens();
        Instant now = mutableClock.instant();
        BffSession created = lifecycle.createSession(
                ropc.subject(), "sid-exchange-" + UUID.randomUUID(),
                new TokenMaterial(ropc.accessToken(), ropc.refreshToken(),
                        now.plus(Duration.ofMinutes(5))),
                ropc.acr() != null ? ropc.acr() : "urn:evplatform:acr:basic",
                now);
        String rotatedRef = lifecycle.rotate(created.sessionRef(), now);
        return new TestSession(rotatedRef, ropc);
    }

    // ------------------------------------------------------------------
    // Raw RFC 8693 request craft (negatives + AC-01 response-shape check)
    // ------------------------------------------------------------------

    /** Exact RFC 8693 form TokenExchangeClient sends, parameterized for negatives. */
    private Map<String, String> exchangeForm(String subjectToken, String audience,
                                             String clientId, String clientSecret,
                                             String clientAssertion) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.put("subject_token", subjectToken);
        form.put("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.put("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.put("audience", audience);
        if (clientId != null) {
            form.put("client_id", clientId);
        }
        if (clientSecret != null) {
            form.put("client_secret", clientSecret);
        }
        if (clientAssertion != null) {
            form.put("client_assertion_type",
                    "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
            form.put("client_assertion", clientAssertion);
        }
        return form;
    }

    private static String oauthError(String body) {
        try {
            return MAPPER.readTree(body).path("error").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /** Claims-only parse of a JWT (signature verification is downstream's job). */
    private static JWTClaimsSet claimsOf(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet();
        } catch (java.text.ParseException e) {
            throw new IllegalStateException("exchanged token was not a JWT", e);
        }
    }

    private static SignedJWT parseClaimsOnly(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (java.text.ParseException e) {
            throw new IllegalStateException("exchanged token was not a JWT", e);
        }
    }

    /** Signs an expired JWT with the shared svc test key (negative-case craft). */
    private String craftExpiredJwt() {
        try {
            RSAKey svcKey = keycloak.svcSigningKey();
            Instant past = Instant.now().minus(Duration.ofHours(1));
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("security-test-client")
                    .subject("subject-negative")
                    .audience("ev-bff")
                    .expirationTime(Date.from(past))
                    .issueTime(Date.from(past.minus(Duration.ofMinutes(5))))
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(svcKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(svcKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("expired JWT craft failed", e);
        }
    }

    // ------------------------------------------------------------------
    // AC-01 — the headline exchange assertion
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void ac01_exchangeYieldsAudienceLimitedToken() throws Exception {
        TestSession session = newSession();

        // 1) RAW exchange (the exact form TokenExchangeClient sends) to
        //    inspect the RESPONSE SHAPE: no refresh_token.
        KeycloakTestHarness.RawResponse raw = keycloak.rawTokenRequest(
                exchangeForm(session.ropc().accessToken(), AUD_STATION,
                        "ev-bff", null, assertionFactory.createAssertion()));
        assertThat(raw.status()).as("raw exchange HTTP status").isEqualTo(200);
        JsonNode body = MAPPER.readTree(raw.body());
        assertThat(body.hasNonNull("access_token")).isTrue();
        assertThat(body.has("refresh_token"))
                .as("exchange response must not contain a refresh_token")
                .isFalse();
        assertThat(body.path("issued_token_type").asText())
                .isEqualTo("urn:ietf:params:oauth:token-type:access_token");

        // 2) Claims of the raw-exchanged token.
        SignedJWT parsed = parseClaimsOnly(body.path("access_token").asText());
        JWTClaimsSet claims = parsed.getJWTClaimsSet();
        // aud == exactly [svc-station-operations] (single-audience downscope)
        assertThat(claims.getAudience()).containsExactly(AUD_STATION);
        // sub preserved from the subject token
        assertThat(claims.getSubject()).isEqualTo(session.ropc().subject());
        // azp == ev-bff (the requester)
        assertThat(claims.getClaim("azp")).isEqualTo("ev-bff");
        // acr + auth_time preserved from the user authentication
        assertThat(claims.getClaim("acr")).isEqualTo(session.ropc().acr());
        assertThat(claims.getClaim("auth_time")).isEqualTo(session.ropc().authTime());
        // exp ≤ iat + 300 (5-minute downstream token cap)
        assertThat(claims.getExpirationTime()).isNotNull();
        assertThat(claims.getIssueTime()).isNotNull();
        long lifetime = (claims.getExpirationTime().getTime()
                - claims.getIssueTime().getTime()) / 1000;
        assertThat(lifetime).isLessThanOrEqualTo(300);
        // typ claim Bearer — the claim (not the JOSE header) is what the
        // downstream JwtTypeValidator("Bearer") chains enforce; Keycloak 26.6
        // emits header typ=JWT with the Bearer designation in the typ claim
        // (observed empirically for both ROPC and exchanged tokens).
        assertThat(claims.getClaim("typ")).isEqualTo("Bearer");

        // 3) The cache path (getOrExchange) succeeds for the same session.
        ExchangedTokenCache.Result result =
                tokenCache.getOrExchange(session.sessionRef(), AUD_STATION);
        assertThat(result).isInstanceOf(ExchangedTokenCache.Cached.class);
        JWTClaimsSet cachedClaims = parseClaimsOnly(
                ((ExchangedTokenCache.Cached) result).accessToken()).getJWTClaimsSet();
        assertThat(cachedClaims.getAudience()).containsExactly(AUD_STATION);
    }

    // ------------------------------------------------------------------
    // Negatives
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void n2_unknownAudienceFails() {
        TestSession session = newSession();

        ExchangedTokenCache.Result result =
                tokenCache.getOrExchange(session.sessionRef(), "unknown-client");

        assertThat(result).isInstanceOf(ExchangedTokenCache.ExchangeFailure.class);
        ExchangedTokenCache.ExchangeFailure failure =
                (ExchangedTokenCache.ExchangeFailure) result;
        assertThat(failure.error()).isNotBlank();
    }

    @Test
    @Order(3)
    void n4_rogueClientExchangeFails() {
        // Replicates the exchange request shape with the rogue confidential
        // client (service-account enabled, client_secret auth, NO
        // standard-token-exchange attribute): the client-attributes condition
        // does not match, so the exchange policy does not apply.
        KeycloakTestHarness.RopcTokens ropc = keycloak.ropcDriverTokens();
        KeycloakTestHarness.RawResponse response = keycloak.rawTokenRequest(
                exchangeForm(ropc.accessToken(), AUD_STATION,
                        "rogue-exchange-client", "evplatform_dev_only_rogue", null));
        assertThat(response.status()).isEqualTo(400);
        assertThat(oauthError(response.body())).isNotBlank();
    }

    @Test
    @Order(4)
    void n5_expiredSubjectTokenFails() {
        // Self-crafted expired JWT signed with the shared svc test key (a key
        // the realm does not trust for any token it validates): the exchange
        // must fail regardless of signature validity — the token is expired.
        KeycloakTestHarness.RawResponse response = keycloak.rawTokenRequest(
                exchangeForm(craftExpiredJwt(), AUD_STATION,
                        "ev-bff", null, assertionFactory.createAssertion()));
        assertThat(response.status()).isEqualTo(400);
        assertThat(oauthError(response.body())).isNotBlank();
    }

    @Test
    @Order(5)
    void n7_singleAudienceBothTargets() {
        TestSession session = newSession();

        for (String aud : List.of(AUD_STATION, AUD_DISCOVERY)) {
            ExchangedTokenCache.Result result = tokenCache.getOrExchange(
                    session.sessionRef(), aud);
            assertThat(result).isInstanceOf(ExchangedTokenCache.Cached.class);
            String token = ((ExchangedTokenCache.Cached) result).accessToken();
            JWTClaimsSet claims = claimsOf(token);
            // Each exchanged token carries EXACTLY its own audience.
            assertThat(claims.getAudience()).containsExactly(aud);
        }
    }

    @Test
    @Order(6)
    void n8_idTokenAsSubjectFails() {
        TestSession session = newSession();

        KeycloakTestHarness.RawResponse response = keycloak.rawTokenRequest(
                exchangeForm(session.ropc().idToken(), AUD_STATION,
                        "ev-bff", null, assertionFactory.createAssertion()));
        assertThat(response.status()).isEqualTo(400);
        assertThat(oauthError(response.body())).isNotBlank();
    }

    // ------------------------------------------------------------------
    // Cache lifecycle cluster
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void cache_a_metadataStoresCiphertextOnly() throws Exception {
        TestSession session = newSession();
        ExchangedTokenCache.Result result =
                tokenCache.getOrExchange(session.sessionRef(), AUD_STATION);
        assertThat(result).isInstanceOf(ExchangedTokenCache.Cached.class);
        String exchangedToken = ((ExchangedTokenCache.Cached) result).accessToken();

        String metadata = lifecycle.storeMetadata(session.sessionRef());
        JsonNode entry = MAPPER.readTree(metadata)
                .path(ExchangedTokenCache.METADATA_KEY).path(AUD_STATION);
        // Entry exists with ciphertext + keyId + expiresAt
        assertThat(entry.isObject()).isTrue();
        assertThat(entry.path("ciphertext").isTextual()).isTrue();
        assertThat(entry.path("ciphertext").asText()).isNotBlank();
        assertThat(entry.path("keyId").asText()).isEqualTo("v1");
        assertThat(entry.path("expiresAt").isTextual()).isTrue();
        // The DB row does NOT contain the plaintext access token string
        String rawRow = jdbcClient.sql("""
                SELECT security_event_metadata::text
                FROM bff_session.bff_session WHERE session_ref = ?
                """).param(session.sessionRef()).query((rs, i) -> rs.getString(1)).single();
        assertThat(rawRow).doesNotContain(exchangedToken);
    }

    @Test
    @Order(8)
    void cache_b_secondCallIsCacheHitWithoutNewExchange() {
        TestSession session = newSession();
        ExchangedTokenCache.Result first =
                tokenCache.getOrExchange(session.sessionRef(), AUD_STATION);
        assertThat(first).isInstanceOf(ExchangedTokenCache.Cached.class);
        String firstToken = ((ExchangedTokenCache.Cached) first).accessToken();
        int exchangesAfterFirst = countingClient.exchangeCount();

        ExchangedTokenCache.Result second =
                tokenCache.getOrExchange(session.sessionRef(), AUD_STATION);
        assertThat(second).isInstanceOf(ExchangedTokenCache.Cached.class);
        String secondToken = ((ExchangedTokenCache.Cached) second).accessToken();

        // Same decrypted token, and NO second HTTP exchange happened.
        assertThat(secondToken).isEqualTo(firstToken);
        assertThat(countingClient.exchangeCount()).isEqualTo(exchangesAfterFirst);
    }

    @Test
    @Order(9)
    void cache_c_expirySkewForcesReExchange() {
        TestSession session = newSession();
        ExchangedTokenCache.Result first =
                tokenCache.getOrExchange(session.sessionRef(), AUD_STATION);
        assertThat(first).isInstanceOf(ExchangedTokenCache.Cached.class);
        String firstToken = ((ExchangedTokenCache.Cached) first).accessToken();
        int exchangesAfterFirst = countingClient.exchangeCount();

        // Advance the shared MutableClock past expiresAt - 30s skew. The
        // cached entry's expiresAt = mutableNow + expiresIn (300s), so +6min
        // is unambiguously inside the skew window. Keycloak-side validity is
        // unaffected: it validates tokens against its own real clock.
        mutableClock.advance(Duration.ofMinutes(6));

        ExchangedTokenCache.Result second =
                tokenCache.getOrExchange(session.sessionRef(), AUD_STATION);
        assertThat(second).isInstanceOf(ExchangedTokenCache.Cached.class);
        String secondToken = ((ExchangedTokenCache.Cached) second).accessToken();

        // A NEW exchange happened (counter incremented) and the token differs.
        assertThat(countingClient.exchangeCount()).isEqualTo(exchangesAfterFirst + 1);
        assertThat(secondToken).isNotEqualTo(firstToken);

        // Re-point the clock so subsequent tests are unaffected.
        mutableClock.advance(Duration.ofMinutes(-6));
    }

    @Test
    @Order(10)
    void cache_d_revokedSessionYieldsSessionInvalid() {
        TestSession session = newSession();
        // Prime the cache so the revoked-session path is what's exercised.
        ExchangedTokenCache.Result prime =
                tokenCache.getOrExchange(session.sessionRef(), AUD_STATION);
        assertThat(prime).isInstanceOf(ExchangedTokenCache.Cached.class);

        assertThat(lifecycle.revoke(session.sessionRef())).isTrue();

        ExchangedTokenCache.Result result =
                tokenCache.getOrExchange(session.sessionRef(), AUD_STATION);
        assertThat(result).isInstanceOf(ExchangedTokenCache.ExchangeFailure.class);
        assertThat(((ExchangedTokenCache.ExchangeFailure) result).error())
                .isEqualTo("session_invalid");
    }

    @Test
    @Order(11)
    void cache_e_crossSessionAadBindingFailsAuthentication() throws Exception {
        TestSession sessionA = newSession();
        ExchangedTokenCache.Result first =
                tokenCache.getOrExchange(sessionA.sessionRef(), AUD_STATION);
        assertThat(first).isInstanceOf(ExchangedTokenCache.Cached.class);
        String originalToken = ((ExchangedTokenCache.Cached) first).accessToken();

        // Session B (different sessionRef → different AAD binding).
        TestSession sessionB = newSession();
        assertThat(sessionB.sessionRef()).isNotEqualTo(sessionA.sessionRef());

        // Copy session A's exchangedTokens fragment into session B's row
        // (simulates a ciphertext copied between sessions — the AAD binding
        // must make it fail GCM authentication and degrade to a cache miss).
        String metadataA = lifecycle.storeMetadata(sessionA.sessionRef());
        JsonNode fragment = MAPPER.readTree(metadataA)
                .path(ExchangedTokenCache.METADATA_KEY);
        lifecycle.mergeMetadata(sessionB.sessionRef(),
                MAPPER.writeValueAsString(
                        Map.of(ExchangedTokenCache.METADATA_KEY, fragment)));

        ExchangedTokenCache.Result stolen =
                tokenCache.getOrExchange(sessionB.sessionRef(), AUD_STATION);
        // Decryption fails → treated as miss → re-exchange works.
        assertThat(stolen).isInstanceOf(ExchangedTokenCache.Cached.class);
        String bToken = ((ExchangedTokenCache.Cached) stolen).accessToken();
        // The recovered token is a fresh exchange (differs from A's cached
        // token — Keycloak mints a new JTI per exchange).
        assertThat(bToken).isNotEqualTo(originalToken);
        // ...and it is a genuine audience-limited token.
        JWTClaimsSet claims = claimsOf(bToken);
        assertThat(claims.getAudience()).containsExactly(AUD_STATION);
    }

    // ------------------------------------------------------------------
    // DA-1 / DA-2 — Admin REST round-trip
    // ------------------------------------------------------------------

    @Test
    @Order(12)
    void da1_da2_adminRoundTrip() {
        // DA-2: clientProfiles + clientPolicies imported and readable.
        JsonNode profiles = keycloak.adminGet("client-policies/profiles");
        JsonNode policies = keycloak.adminGet("client-policies/policies");
        boolean profilePresent = containsNamed(profiles.path("profiles"),
                "ev-token-exchange-downscope");
        boolean policyPresent = containsNamed(policies.path("policies"),
                "ev-bff-token-exchange-policy");
        // Record the observed shapes (evidence; no token material involved).
        System.out.println("[DA-2] profiles contains ev-token-exchange-downscope: "
                + profilePresent + "; policies contains ev-bff-token-exchange-policy: "
                + policyPresent);

        // DA-1: the ev-bff client attributes round-trip — record EVERY
        // attribute key so the observed standard-token-exchange key string is
        // captured empirically.
        JsonNode clients = keycloak.adminGet("clients?clientId=ev-bff");
        assertThat(clients.isArray()).isTrue();
        assertThat(clients.size()).isEqualTo(1);
        JsonNode evBff = clients.get(0);
        List<String> attributeKeys = new ArrayList<>();
        evBff.path("attributes").fieldNames().forEachRemaining(attributeKeys::add);
        System.out.println("[DA-1] ev-bff attribute keys: " + attributeKeys);
        boolean exchangeKeyPresent = attributeKeys.stream()
                .anyMatch(k -> k.equalsIgnoreCase("standard.token.exchange.enabled"));
        System.out.println("[DA-1] standard.token.exchange.enabled present: "
                + exchangeKeyPresent);

        // The realm import DID take: both DA items must round-trip. If the
        // attribute/policy were silently ignored, this assertion fails and
        // the deviation-file STOP rule applies.
        assertThat(profilePresent).as("DA-2 clientProfiles round-trip").isTrue();
        assertThat(policyPresent).as("DA-2 clientPolicies round-trip").isTrue();
        assertThat(exchangeKeyPresent).as("DA-1 attribute key round-trip").isTrue();
    }

    private static boolean containsNamed(JsonNode array, String name) {
        for (JsonNode item : array) {
            if (name.equals(item.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Test configuration: counting exchange client + OAuth2 stand-ins
    // ------------------------------------------------------------------

    /**
     * Counting decorator around {@link TokenExchangeClient}: proves cache
     * hits do NOT reach the token endpoint. Registered as @Primary so
     * {@link ExchangedTokenCache} (constructor-injected by type) receives
     * the decorator; the test @Autowired field sees the same instance.
     */
    public static class CountingTokenExchangeClient extends TokenExchangeClient {

        private final java.util.concurrent.atomic.AtomicInteger count =
                new java.util.concurrent.atomic.AtomicInteger();

        CountingTokenExchangeClient(ClientAssertionFactory assertionFactory,
                                    String issuer) {
            super(assertionFactory, issuer);
        }

        @Override
        public ExchangeResult exchange(String subjectToken, String audience) {
            count.incrementAndGet();
            return super.exchange(subjectToken, audience);
        }

        int exchangeCount() {
            return count.get();
        }

        void reset() {
            count.set(0);
        }
    }

    @TestConfiguration
    static class ExchangeTestConfig {

        /**
         * The counting client's {@link ClientAssertionFactory} uses the REAL
         * clock: Keycloak validates the {@code client_assertion} (iat/exp)
         * against ITS real clock, while the shared MutableClock simulates
         * BFF-internal time only (sessions, cache expiry). An assertion
         * built from the advanced mutable clock (cache_c's +6min) would be
         * rejected with "Token was issued in the future". In production the
         * BFF clock IS real time, so this substitution changes nothing
         * about the code under test — the exchange request shape and the
         * assertion claims are identical.
         */
        @Bean
        @Primary
        CountingTokenExchangeClient countingTokenExchangeClient(
                @org.springframework.beans.factory.annotation.Qualifier(
                        com.evplatform.bff.security.ClientKeyConfig.BFF_CLIENT_JWK_BEAN)
                RSAKey bffClientJwk,
                @org.springframework.beans.factory.annotation.Value(
                        "${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
                String issuer) {
            return new CountingTokenExchangeClient(new ClientAssertionFactory(
                    bffClientJwk, Clock.systemUTC(), issuer), issuer);
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(
                    ClientRegistration.withRegistrationId("ev-bff")
                            .clientId("ev-bff")
                            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("{baseUrl}/login/oauth2/code/ev-bff")
                            .authorizationUri(issuer + "/protocol/openid-connect/auth")
                            .tokenUri(issuer + "/protocol/openid-connect/token")
                            .scope("openid", "profile")
                            .build());
        }

        @Bean
        OAuth2AuthorizedClientService authorizedClientService(
                ClientRegistrationRepository repository) {
            return new InMemoryOAuth2AuthorizedClientService(repository);
        }
    }
}
