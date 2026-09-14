package com.evplatform.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.evplatform.bff.BffApplication;
import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.SessionLifecycleService;
import com.evplatform.bff.session.SessionLifecycleService.TokenMaterial;
import com.evplatform.libraries.testsupport.LocalDependencies;
import com.evplatform.libraries.testsupport.MutableClock;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * I1-IAM-001 cluster D4 (SEC-P08 / SEC-001 §5.4 items 10-12 deepening):
 * dedicated tests for the OIDC Back-Channel Logout receiver
 * ({@link BackChannelLogoutController} at {@code /api/internal/back-channel-logout}),
 * executed through the FULL Spring context — real SecurityFilterChain
 * (permitAll on the BCL path, form-urlencoded content-type allowance scoped
 * to exactly this path), real session stack, real PostgreSQL via
 * Testcontainers with the shared provisioning script — with the SAME
 * controlled substitutions as {@link SecP01SessionTests} (identity test
 * keys via {@code @DynamicPropertySource}, OAuth2 client auto-configuration
 * excluded with in-memory stand-ins, Clock overridden with a Mockito
 * delegate to the shared {@link MutableClock}, IdP JWKSource overridden
 * with a test RSA key).
 *
 * <p>All tokens are signed in-process with the test IdP key; iat/exp use the
 * JVM wall clock because Nimbus's DefaultJWTProcessor runs its exp/max-age
 * checks against {@code Clock.systemUTC()} (not injectable). No OAuth token
 * material appears anywhere in this suite.</p>
 *
 * <p>Numbered test methods map 1:1 to the D4 items in the task packet
 * (I1-IAM-001 phase 2). Sessions are simulated through the REAL
 * {@link SessionLifecycleService#createSession} + {@code rotate} calls —
 * the same calls the login success handler makes.</p>
 */
@SpringBootTest(classes = { BffApplication.class,
        BackChannelLogoutTests.IdentityTestConfig.class }, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration",
        "spring.flyway.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackChannelLogoutTests {

    private static final String PW = "evplatform_dev_only";
    private static final String DB = "bff_session_db";
    private static final String MIGRATOR = "bff_session_migrator";
    private static final String RUNTIME = "bff_session_runtime";
    private static final String SCHEMA = "bff_session";

    /** The fake token string used to prove no token reaches the browser. */
    private static final String FAKE_TOKEN = "fake-access-token-NOT-REAL";

    /** Issuer pinned via @DynamicPropertySource; the BCL controller validates against it. */
    private static final String ISSUER = "http://127.0.0.1:8180/realms/ev-local";

    /** Start instant of the shared MutableClock (every test is self-relative). */
    private static final Instant T0 =
            Instant.parse("2026-09-13T12:00:00Z").truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    private static final String TEST_SESSION_KEY_B64;
    private static final String TEST_CLIENT_KEY_PEM_PATH;

    /** Test IdP RSA key pair — the public JWK feeds the overridden JWKSource bean. */
    private static KeyPair idpKeyPair;

    static {
        byte[] aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);
        TEST_SESSION_KEY_B64 = Base64.getEncoder().encodeToString(aesKey);
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            idpKeyPair = generator.generateKeyPair();
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[] {'\n'})
                            .encodeToString(idpKeyPair.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            Path file = Files.createTempFile("bff-bcl-client-key", ".pem");
            Files.writeString(file, pem);
            TEST_CLIENT_KEY_PEM_PATH = file.toString();
        } catch (Exception e) {
            throw new IllegalStateException("BCL test key setup failed", e);
        }
    }

    @DynamicPropertySource
    static void identityTestProperties(DynamicPropertyRegistry registry) {
        registry.add("bff.session.encryption.keys.v1", () -> TEST_SESSION_KEY_B64);
        registry.add("bff.oauth.client-private-key-path", () -> TEST_CLIENT_KEY_PEM_PATH);
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
                () -> ISSUER);
        // Real PostgreSQL from the Testcontainers container (runtime role —
        // migrations already applied under the migrator role in @BeforeAll).
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://"
                + pg.getHost() + ":" + pg.getMappedPort(5432) + "/" + DB);
        registry.add("spring.datasource.username", () -> RUNTIME);
        registry.add("spring.datasource.password", () -> PW);
    }

    private static PostgreSQLContainer pg;
    private static MutableClock mutableClock;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SessionLifecycleService lifecycle;

    /** Direct SQL access for the row-count assertions required by items 7/8. */
    @Autowired
    private JdbcClient jdbcClient;

    /** Overridden Clock bean: every consumer reads the shared MutableClock. */
    @MockitoBean
    private Clock clock;

    /** Overridden IdP JWKS source: serves the test RSA public key (BCL verify). */
    @MockitoBean
    private JWKSource<SecurityContext> idpJwkSource;

    private MockMvc mockMvc;

    @BeforeAll
    static void startContainerAndMigrate() {
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
        mutableClock = MutableClock.at(T0.toString());
    }

    @AfterAll
    static void stopContainer() {
        if (pg != null) {
            pg.stop();
        }
    }

    @BeforeEach
    void setUpClockAndMockMvc() {
        Mockito.reset(clock);
        Mockito.when(clock.instant()).thenAnswer(invocation -> mutableClock.instant());
        Mockito.when(clock.getZone()).thenReturn(mutableClock.getZone());
        Mockito.when(clock.withZone(ArgumentMatchers.any())).thenReturn(mutableClock);
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ------------------------------------------------------------------
    // Session simulation helpers (real lifecycle service, real store)
    // ------------------------------------------------------------------

    /**
     * Creates an ACTIVE session row exactly the way the login success
     * handler does (createSession → rotate) and returns the CURRENT
     * (rotated) reference.
     */
    private String loginAs(String subject, String sid) {
        Instant now = mutableClock.instant();
        BffSession created = lifecycle.createSession(subject, sid,
                new TokenMaterial(FAKE_TOKEN, null, now.plus(Duration.ofMinutes(5))),
                "urn:evplatform:acr:basic", now);
        return lifecycle.rotate(created.sessionRef(), now);
    }

    /** Count of ACTIVE rows for a subject+sid pair (direct SQL, runtime role). */
    private int activeRowCount(String subject, String sid) {
        Integer count = jdbcClient.sql("""
                SELECT count(*)
                FROM bff_session.bff_session
                WHERE keycloak_subject = ? AND keycloak_sid = ?
                  AND revocation_state = 'ACTIVE'
                """)
                .param(subject)
                .param(sid)
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    /** Count of ACTIVE rows for a sid only (no subject filter). */
    private int activeRowCountBySid(String sid) {
        Integer count = jdbcClient.sql("""
                SELECT count(*)
                FROM bff_session.bff_session
                WHERE keycloak_sid = ? AND revocation_state = 'ACTIVE'
                """)
                .param(sid)
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    /** POSTs a form-urlencoded logout_token to the BCL receiver. */
    private MvcResult postLogoutToken(String logoutToken) throws Exception {
        return mockMvc.perform(post(BackChannelLogoutController.BCL_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", logoutToken))
                .andReturn();
    }

    // ------------------------------------------------------------------
    // Item 1 — valid token (sub+sid matching an existing session) →
    // 200 + row REVOKED
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void item1_validTokenRevokesTheMatchingSession() throws Exception {
        String ref = loginAs("subject-bcl-item1", "sid-bcl-item1");
        stubIdpJwkSource();

        assertThat(activeRowCount("subject-bcl-item1", "sid-bcl-item1")).isEqualTo(1);

        MvcResult result = postLogoutToken(
                signedLogoutToken("subject-bcl-item1", "sid-bcl-item1").serialize());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        // The row must be REVOKED, not merely expired or hidden: verify via
        // the store (findActiveBySubjectAndSid no longer resolves it) ...
        assertThat(lifecycle.findActiveBySubjectAndSid(
                "subject-bcl-item1", "sid-bcl-item1")).isEmpty();
        // ...and the row still exists with revocation_state = 'REVOKED'
        // (direct SQL — REVOKED is the required terminal state).
        String state = jdbcClient.sql("""
                SELECT revocation_state
                FROM bff_session.bff_session
                WHERE session_ref = ?
                """)
                .param(ref)
                .query(String.class)
                .single();
        assertThat(state).isEqualTo("REVOKED");
    }

    // ------------------------------------------------------------------
    // Item 2 — garbage string → 400
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void item2_garbageStringIsRejectedWith400() throws Exception {
        stubIdpJwkSource();
        assertThat(postLogoutToken("not-a-jwt").getResponse().getStatus())
                .isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // Item 3 — unsigned JWT → 400
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void item3_unsignedJwtIsRejectedWith400() throws Exception {
        stubIdpJwkSource();
        JWTClaimsSet unsignedClaims = new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject("subject-bcl-item3").claim("sid", "sid-bcl-item3")
                .claim("events", Map.of(BackChannelLogoutController.LOGOUT_EVENT_URI,
                        Map.of()))
                .build();
        assertThat(postLogoutToken(new PlainJWT(unsignedClaims).serialize())
                .getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // Item 4 — wrong iss → 400
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void item4_wrongIssuerIsRejectedWith400() throws Exception {
        RSAKey signingKey = stubIdpJwkSource();
        SignedJWT wrongIss = signedLogoutToken("subject-bcl-item4", "sid-bcl-item4",
                signingKey, "http://evil.example/realms/other", true, true);
        assertThat(postLogoutToken(wrongIss.serialize()).getResponse().getStatus())
                .isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // Item 5 — nonce present → 400 + row NOT revoked
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void item5_noncePresentIsRejectedWithoutRevocation() throws Exception {
        String ref = loginAs("subject-bcl-item5", "sid-bcl-item5");
        RSAKey signingKey = stubIdpJwkSource();

        JWTClaimsSet nonceClaims = baseLogoutClaims("subject-bcl-item5", "sid-bcl-item5");
        nonceClaims = new JWTClaimsSet.Builder(nonceClaims)
                .claim("nonce", "must-not-be-present").build();
        SignedJWT withNonce = sign(nonceClaims, signingKey);

        MvcResult result = postLogoutToken(withNonce.serialize());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        // The rejected notification must not have revoked the session.
        assertThat(lifecycle.findActiveBySubjectAndSid(
                "subject-bcl-item5", "sid-bcl-item5")).isPresent();
        assertThat(lifecycle.loadValidSession(ref, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Valid.class);
    }

    // ------------------------------------------------------------------
    // Item 6 — missing events → 400
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void item6_missingEventsClaimIsRejectedWith400() throws Exception {
        RSAKey signingKey = stubIdpJwkSource();
        JWTClaimsSet noEventsClaims = new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject("subject-bcl-item6").claim("sid", "sid-bcl-item6")
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT noEvents = sign(noEventsClaims, signingKey);
        assertThat(postLogoutToken(noEvents.serialize()).getResponse().getStatus())
                .isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // Item 7 — valid token for an UNKNOWN subject → 200 + 0 rows changed
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void item7_validTokenForUnknownSubjectAnswers200AndChangesNoRows() throws Exception {
        stubIdpJwkSource();
        String unknownSubject = "subject-bcl-item7-unknown";
        String unknownSid = "sid-bcl-item7-unknown";

        int before = activeRowCount(unknownSubject, unknownSid);
        assertThat(before).isZero();

        MvcResult result = postLogoutToken(
                signedLogoutToken(unknownSubject, unknownSid).serialize());

        // 200 even with zero matching rows: the endpoint is idempotent for
        // Keycloak retries (documented controller contract).
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        int after = activeRowCount(unknownSubject, unknownSid);
        assertThat(after).isZero();
    }

    // ------------------------------------------------------------------
    // Item 8 — sid-only token (no sub) → 200 + 0 rows changed
    // ------------------------------------------------------------------

    /**
     * Item 8 documents the CURRENT receiver behavior: a sid-only logout
     * token passes validation (OIDC BCL §2.4 allows sub and/or sid) but the
     * store's revocation SQL matches on BOTH {@code keycloak_subject} and
     * {@code keycloak_sid}, so with a null subject it matches 0 rows. The
     * endpoint answers 200 (idempotent) and nothing changes. This is a
     * known, documented limitation (prior handoff residual risk), not a
     * security hole: nothing is revoked that should not be.
     */
    @Test
    @Order(8)
    void item8_sidOnlyTokenAnswers200AndChangesNoRows() throws Exception {
        // An existing session whose sid matches the token — proving the
        // 0-row outcome is the null-subject match, not an absent session.
        String ref = loginAs("subject-bcl-item8", "sid-bcl-item8");
        RSAKey signingKey = stubIdpJwkSource();

        JWTClaimsSet sidOnlyClaims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience("ev-bff")
                .claim("sid", "sid-bcl-item8")
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("events", Map.of(BackChannelLogoutController.LOGOUT_EVENT_URI,
                        Map.of()))
                .build();
        SignedJWT sidOnly = sign(sidOnlyClaims, signingKey);

        int before = activeRowCountBySid("sid-bcl-item8");
        assertThat(before).isEqualTo(1);

        MvcResult result = postLogoutToken(sidOnly.serialize());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        // Documented behavior: 0 rows changed despite the sid matching an
        // ACTIVE session — the store match requires both subject and sid.
        int after = activeRowCountBySid("sid-bcl-item8");
        assertThat(after).isEqualTo(1);
        assertThat(lifecycle.loadValidSession(ref, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Valid.class);
    }

    // ------------------------------------------------------------------
    // Logout-token construction helpers (same wall-clock note as SecP01:
    // Nimbus validates exp against the JVM clock, not the MutableClock)
    // ------------------------------------------------------------------

    private JWTClaimsSet baseLogoutClaims(String subject, String sid) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience("ev-bff")
                .subject(subject)
                .claim("sid", sid)
                .issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("events", Map.of(BackChannelLogoutController.LOGOUT_EVENT_URI,
                        Map.of()))
                .build();
    }

    private SignedJWT sign(JWTClaimsSet claims, RSAKey signingKey) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) idpKeyPair.getPrivate()));
        return jwt;
    }

    /** A fully valid logout token signed with the test IdP key. */
    private SignedJWT signedLogoutToken(String subject, String sid) throws Exception {
        return signedLogoutToken(subject, sid, stubIdpJwkSource(), ISSUER, true, true);
    }

    private SignedJWT signedLogoutToken(String subject, String sid, RSAKey signingKey,
                                        String issuer, boolean withEvents,
                                        boolean withAudience) throws Exception {
        Instant now = Instant.now();
        var builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .claim("sid", sid)
                .issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.plusSeconds(300)));
        if (withAudience) {
            builder.audience("ev-bff");
        }
        if (withEvents) {
            builder.claim("events", Map.of(BackChannelLogoutController.LOGOUT_EVENT_URI,
                    Map.of()));
        }
        return sign(builder.build(), signingKey);
    }

    /**
     * Stubs the overridden {@code idpJwkSource} bean to serve the test IdP
     * RSA public key (RS256, signature use) — the key selector in
     * {@link BackChannelLogoutController} matches on algorithm/use.
     */
    private RSAKey stubIdpJwkSource() throws com.nimbusds.jose.KeySourceException {
        RSAKey signingKey = new RSAKey.Builder(
                (RSAPublicKey) idpKeyPair.getPublic())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("test-idp-key-1")
                .build();
        Mockito.when(idpJwkSource.get(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(List.<JWK>of(signingKey));
        return signingKey;
    }

    // ------------------------------------------------------------------
    // Identity stand-ins (same shape as SecP01/SecP02)
    // ------------------------------------------------------------------

    /** Stand-ins for the excluded OAuth2 client auto-configuration. */
    @TestConfiguration
    static class IdentityTestConfig {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(
                    org.springframework.security.oauth2.client.registration.ClientRegistration
                            .withRegistrationId("ev-bff")
                            .clientId("ev-bff")
                            .clientAuthenticationMethod(
                                    org.springframework.security.oauth2.core.ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                            .authorizationGrantType(
                                    org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("{baseUrl}/login/oauth2/code/ev-bff")
                            .authorizationUri(ISSUER + "/protocol/openid-connect/auth")
                            .tokenUri(ISSUER + "/protocol/openid-connect/token")
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
