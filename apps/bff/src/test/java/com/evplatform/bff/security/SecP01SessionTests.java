package com.evplatform.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import jakarta.servlet.http.Cookie;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * I1-IAM-001 cluster D2 (AC-03): SEC-001 §5.4 required tests for the BFF
 * opaque session (SEC-P01), executed through the FULL Spring context —
 * real SecurityFilterChain, real session stack (store, lifecycle, CSRF
 * repository, controllers), real PostgreSQL via Testcontainers with the
 * shared provisioning script (same path as JdbcSessionStoreTests) — with
 * three controlled substitutions:
 *
 * <ul>
 *   <li>the OAuth2 client auto-configuration is excluded (it would eagerly
 *       resolve the Keycloak issuer at startup); in-memory stand-ins supply
 *       the client registration and authorized-client service (same pattern
 *       as BffApplicationTests/PublicProxyControllerTests);</li>
 *   <li>the identity test keys are supplied via {@code @DynamicPropertySource}
 *       (generated at class load; no key material committed — AGENTS.md §4);
 *       the Keycloak issuer is pinned to a constant the BCL controller
 *       validates against;</li>
 *   <li>the {@code Clock} bean is overridden with a Mockito delegating to a
 *       {@link MutableClock} (test-support technical primitive) so expiry
 *       tests are deterministic, and the IdP {@code JWKSource} bean is
 *       overridden with a mock serving the test RSA public key so
 *       back-channel logout tokens can be signed in-process.</li>
 * </ul>
 *
 * <p>Flyway auto-configuration is disabled ({@code spring.flyway.enabled=false})
 * because migrations run here under the MIGRATOR role in {@code @BeforeAll}
 * (identical to JdbcSessionStoreTests); letting the application context run
 * Flyway would execute DDL under the RUNTIME role, violating the
 * runtime/migrator role separation (AGENTS.md persistence rules).</p>
 *
 * <p>Authenticated sessions are simulated by driving the REAL
 * {@link SessionLifecycleService#createSession} + {@code rotate} (the same
 * calls the login success handler makes) and presenting the CURRENT
 * reference as the {@code __Host-evsession} cookie.</p>
 *
 * <p>Numbered test methods map 1:1 to the SEC-001 §5.4 required-test items
 * (task packet AC-03). Item 4 (step-up rotation) is SEC-P07 scope, deferred
 * by the task packet — recorded NOT_RUN in the evidence, not written here.</p>
 */
@SpringBootTest(classes = { BffApplication.class,
        SecP01SessionTests.IdentityTestConfig.class }, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration",
        "spring.flyway.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecP01SessionTests {

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
            Path file = Files.createTempFile("bff-secp01-client-key", ".pem");
            Files.writeString(file, pem);
            TEST_CLIENT_KEY_PEM_PATH = file.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SEC-P01 test key setup failed", e);
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
     * handler does (createSession → rotate, SEC-P01 §5.1 steps 5-6), with
     * the fake token string as the encrypted access token, and returns the
     * CURRENT (rotated) reference.
     */
    private String loginAs(String subject, String sid) {
        Instant now = mutableClock.instant();
        BffSession created = lifecycle.createSession(subject, sid,
                new TokenMaterial(FAKE_TOKEN, null, now.plus(Duration.ofMinutes(5))),
                "urn:evplatform:acr:basic", now);
        return lifecycle.rotate(created.sessionRef(), now);
    }

    private Cookie sessionCookie(String ref) {
        return new Cookie(BffSessionSecurityContextRepository.COOKIE_NAME, ref);
    }

    // ------------------------------------------------------------------
    // §5.4 item 1 — browser storage contains no OAuth token
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void item1_sessionResponseAndCookiesNeverContainTheAccessToken() throws Exception {
        String ref = loginAs("subject-item1", "sid-item1");

        MvcResult result = mockMvc.perform(get("/api/v1/session")
                        .cookie(sessionCookie(ref))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(FAKE_TOKEN);
        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        for (String value : setCookies) {
            assertThat(value).doesNotContain(FAKE_TOKEN);
        }
        // Structural guarantee: the principal handed to controllers
        // (BffSessionPrincipal) has no token-material field at all — the
        // encrypted material never leaves the store un-decrypted here.
    }

    // ------------------------------------------------------------------
    // §5.4 item 2 — cookie contract, attribute-exact
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void item2_loginSuccessHandlerEmitsTheExactCookieContract() throws Exception {
        // Drive the REAL BffLoginSuccessHandler bean (a public @Component —
        // no security/** refactor needed) with a fake OidcUser and an
        // authorized-client payload carrying the fake token.
        var successHandler = webApplicationContext.getBean(
                SecurityConfig.BffLoginSuccessHandler.class);

        Instant now = mutableClock.instant();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, FAKE_TOKEN, now,
                now.plus(Duration.ofMinutes(5)));
        ClientRegistration registration = ClientRegistration.withRegistrationId("ev-bff")
                .clientId("ev-bff")
                .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/ev-bff")
                .authorizationUri(ISSUER + "/protocol/openid-connect/auth")
                .tokenUri(ISSUER + "/protocol/openid-connect/token")
                .scope("openid", "profile")
                .build();
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                registration, "subject-item2", accessToken);
        OAuth2AuthorizedClientService authorizedClientService =
                webApplicationContext.getBean(OAuth2AuthorizedClientService.class);
        authorizedClientService.saveAuthorizedClient(authorizedClient,
                oauth2Authentication("subject-item2"));

        Instant iat = now.minusSeconds(1);
        OidcIdToken idToken = new OidcIdToken("id-token-value-not-real",
                iat, now.plus(Duration.ofHours(5)),
                Map.of(
                        "iss", ISSUER,
                        "sub", "subject-item2",
                        "aud", "ev-bff",
                        "sid", "sid-item2",
                        "acr", "urn:evplatform:acr:basic",
                        "iat", iat.getEpochSecond()));
        var oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_AUTHENTICATED"), idToken);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request, response,
                new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "ev-bff"));

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith(BffSessionSecurityContextRepository.COOKIE_NAME + "=");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Max-Age=28800"); // 8h absolute timeout in seconds
        assertThat(setCookie).doesNotContain("Domain");
        // The emitted cookie value is the ROTATED reference (never the token).
        assertThat(setCookie).doesNotContain(FAKE_TOKEN);
        String rotatedRef = setCookie.substring(
                setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        // The rotated reference from the login response authenticates.
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(rotatedRef)))
                .andExpect(status().isOk());
    }

    /** Builds an OAuth2AuthenticationToken whose getName() is the given subject. */
    private static OAuth2AuthenticationToken oauth2Authentication(String subject) {
        OidcIdToken idToken = new OidcIdToken("id-token-value-not-real",
                mutableClock.instant(), mutableClock.instant().plus(Duration.ofHours(5)),
                Map.of("iss", ISSUER, "sub", subject, "aud", "ev-bff"));
        var oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_AUTHENTICATED"), idToken);
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "ev-bff");
    }

    // ------------------------------------------------------------------
    // §5.4 item 3 — session ID changes after login
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void item3_sessionIdChangesAfterLoginAndOldRefStopsAuthenticating() {
        Instant now = mutableClock.instant();
        BffSession created = lifecycle.createSession("subject-item3", "sid-item3",
                new TokenMaterial(FAKE_TOKEN, null, now.plus(Duration.ofMinutes(5))),
                "urn:evplatform:acr:basic", now);
        String ref1 = created.sessionRef();
        String ref2 = lifecycle.rotate(ref1, now);

        assertThat(ref2).isNotEqualTo(ref1);
        // The old reference must no longer resolve to an ACTIVE session...
        assertThat(lifecycle.loadValidSession(ref1, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Expired.class);
        // ...while the new one does.
        assertThat(lifecycle.loadValidSession(ref2, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Valid.class);
    }

    // ------------------------------------------------------------------
    // §5.4 item 5 — idle expiry (item 4 = step-up rotation, SEC-P07
    // deferred by the task packet — recorded NOT_RUN, not written here)
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void item5_idleExpiryAnswers401SessionExpired() throws Exception {
        String ref = loginAs("subject-item5", "sid-item5");

        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(ref)))
                .andExpect(status().isOk());

        // Advance 31 minutes (idle timeout is 30m): the next request must be
        // rejected as expired with the SESSION_EXPIRED problem code.
        mutableClock.advance(Duration.ofMinutes(31));

        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(ref)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("SESSION_EXPIRED"));
    }

    // ------------------------------------------------------------------
    // §5.4 item 6 — absolute expiry is NOT extended by activity
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void item6_absoluteExpiryCannotBeExtendedByActivity() throws Exception {
        String ref = loginAs("subject-item6", "sid-item6");

        // Touch every 25 minutes (19 touches = 7h55m): every load re-derives
        // the idle window (30m), so the session stays strictly inside its
        // idle window — the 25m cadence never lands exactly on the idle
        // boundary (validity is strictly-before).
        for (int i = 1; i <= 19; i++) {
            mutableClock.advance(Duration.ofMinutes(25));
            mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(ref)))
                    .andExpect(status().isOk());
        }
        // At +7h55m the last touch re-derived idle to +8h25m — PAST the
        // absolute boundary — so the final rejection can only be caused by
        // the absolute cap, not by idleness.
        mutableClock.advance(Duration.ofMinutes(10));
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(ref)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("SESSION_EXPIRED"));
    }

    // ------------------------------------------------------------------
    // §5.4 items 7 + 8 — logout invalidates; refresh after logout fails
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void item7_item8_logoutInvalidatesAndRefreshAfterLogoutFails() throws Exception {
        String ref = loginAs("subject-item7", "sid-item7");

        // CSRF fetch first (the synchronizer token is session-bound).
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/session/csrf")
                        .cookie(sessionCookie(ref)))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = com.jayway.jsonpath.JsonPath.read(
                csrfResult.getResponse().getContentAsString(), "$.token");
        assertThat(csrfToken).isNotBlank();

        mockMvc.perform(post("/api/v1/session/logout")
                        .cookie(sessionCookie(ref))
                        .header("X-CSRF-TOKEN", csrfToken)
                        .header("Origin", "http://127.0.0.1:4200")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // item 7: the same cookie no longer authenticates.
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(ref)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("SESSION_EXPIRED"));

        // item 8: a "refresh" (second request with the same cookie) also
        // fails — the row is REVOKED in the store, not merely hidden.
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(ref)))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // §5.4 item 9 — stolen pre-rotation session reference
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    void item9_stolenPreRotationReferenceFailsAfterRotation() throws Exception {
        Instant now = mutableClock.instant();
        BffSession created = lifecycle.createSession("subject-item9", "sid-item9",
                new TokenMaterial(FAKE_TOKEN, null, now.plus(Duration.ofMinutes(5))),
                "urn:evplatform:acr:basic", now);
        String stolenRef = created.sessionRef(); // the attacker's copy
        String currentRef = lifecycle.rotate(stolenRef, now);

        // The current reference works...
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(currentRef)))
                .andExpect(status().isOk());
        // ...and the stolen pre-rotation reference does not.
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(stolenRef)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("SESSION_EXPIRED"));
    }

    // ------------------------------------------------------------------
    // §5.4 item 10 — valid back-channel logout token revokes the session
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void item10_validBackChannelLogoutTokenRevokesTheMappedSession() throws Exception {
        String ref = loginAs("subject-item10", "sid-item10");
        stubIdpJwkSource();

        SignedJWT logoutToken = signedLogoutToken("subject-item10", "sid-item10");

        mockMvc.perform(post(BackChannelLogoutController.BCL_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", logoutToken.serialize()))
                .andExpect(status().isOk());

        // The store row must be REVOKED...
        assertThat(lifecycle.loadValidSession(ref, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Expired.class);
        // ...and the browser session is dead.
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(ref)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("SESSION_EXPIRED"));
    }

    // ------------------------------------------------------------------
    // §5.4 item 11 — malformed logout tokens are rejected without revoking
    // ------------------------------------------------------------------

    @Test
    @Order(11)
    void item11_malformedLogoutTokensAreRejectedWithoutRevocation() throws Exception {
        String ref = loginAs("subject-item11", "sid-item11");
        RSAKey signingKey = stubIdpJwkSource();

        // (a) garbage string
        mockMvc.perform(post(BackChannelLogoutController.BCL_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", "not-a-jwt"))
                .andExpect(status().isBadRequest());

        // (b) unsigned JWT (alg=none, two segments)
        JWTClaimsSet unsignedClaims = new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject("subject-item11").claim("sid", "sid-item11")
                .claim("events", Map.of(BackChannelLogoutController.LOGOUT_EVENT_URI,
                        Map.of()))
                .build();
        mockMvc.perform(post(BackChannelLogoutController.BCL_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", new PlainJWT(unsignedClaims).serialize()))
                .andExpect(status().isBadRequest());

        // (c) wrong issuer (valid signature, iss ≠ configured issuer)
        SignedJWT wrongIss = signedLogoutToken("subject-item11", "sid-item11",
                signingKey, "http://evil.example/realms/other", true, true);
        mockMvc.perform(post(BackChannelLogoutController.BCL_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", wrongIss.serialize()))
                .andExpect(status().isBadRequest());

        // (d) nonce present (logout tokens MUST NOT carry one)
        JWTClaimsSet nonceClaims = baseLogoutClaims("subject-item11", "sid-item11");
        nonceClaims = new JWTClaimsSet.Builder(nonceClaims)
                .claim("nonce", "must-not-be-present").build();
        SignedJWT withNonce = sign(nonceClaims, signingKey);
        mockMvc.perform(post(BackChannelLogoutController.BCL_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", withNonce.serialize()))
                .andExpect(status().isBadRequest());

        // (e) missing events claim
        JWTClaimsSet noEventsClaims = new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject("subject-item11").claim("sid", "sid-item11")
                .build();
        SignedJWT noEvents = sign(noEventsClaims, signingKey);
        mockMvc.perform(post(BackChannelLogoutController.BCL_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", noEvents.serialize()))
                .andExpect(status().isBadRequest());

        // None of the rejections may have revoked the session.
        assertThat(lifecycle.loadValidSession(ref, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Valid.class);
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(ref)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // §5.4 item 12 — another subject's session is untouched by a BCL revoke
    // ------------------------------------------------------------------

    @Test
    @Order(12)
    void item12_backChannelLogoutForSubjectBDoesNotTouchSubjectA() throws Exception {
        String refA = loginAs("subject-item12-A", "sid-item12-A");
        String refB = loginAs("subject-item12-B", "sid-item12-B");
        stubIdpJwkSource();

        SignedJWT logoutToken = signedLogoutToken("subject-item12-B", "sid-item12-B");

        mockMvc.perform(post(BackChannelLogoutController.BCL_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", logoutToken.serialize()))
                .andExpect(status().isOk());

        // B is revoked...
        assertThat(lifecycle.loadValidSession(refB, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Expired.class);
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(refB)))
                .andExpect(status().isUnauthorized());
        // ...and A is untouched.
        assertThat(lifecycle.loadValidSession(refA, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Valid.class);
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(refA)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // §5.4 item 13 — authenticated responses use Cache-Control: private, no-store
    // ------------------------------------------------------------------

    @Test
    @Order(13)
    void item13_authenticatedResponsesUsePrivateNoStore() throws Exception {
        String ref = loginAs("subject-item13", "sid-item13");
        mockMvc.perform(get("/api/v1/session").cookie(sessionCookie(ref)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    // ------------------------------------------------------------------
    // Back-channel logout token construction helpers
    // ------------------------------------------------------------------

    private JWTClaimsSet baseLogoutClaims(String subject, String sid) {
        // iat/exp use the JVM wall clock: Nimbus's DefaultJWTProcessor runs
        // its exp/max-age checks against Clock.systemUTC() (not injectable),
        // so a token stamped with the test MutableClock instant would be
        // "expired" from the processor's perspective.
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
        // Same wall-clock note as baseLogoutClaims: Nimbus validates exp
        // against the JVM clock.
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
    // Identity stand-ins (same shape as the smoke/proxy tests)
    // ------------------------------------------------------------------

    /** Stand-ins for the excluded OAuth2 client auto-configuration. */
    @TestConfiguration
    static class IdentityTestConfig {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(
                    ClientRegistration.withRegistrationId("ev-bff")
                            .clientId("ev-bff")
                            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
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
