package com.evplatform.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evplatform.bff.BffApplication;
import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.SessionLifecycleService;
import com.evplatform.bff.session.SessionLifecycleService.TokenMaterial;
import com.evplatform.libraries.testsupport.LocalDependencies;
import com.evplatform.libraries.testsupport.MutableClock;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * I1-IAM-001 cluster D3 (AC-04): SEC-P02 §6.4 required tests for the
 * session-bound synchronizer CSRF protection, executed through the FULL
 * Spring context — real SecurityFilterChain (OriginFilter → CsrfFilter →
 * authorization), real session stack, real PostgreSQL via Testcontainers
 * with the shared provisioning script — with the SAME controlled
 * substitutions as {@link SecP01SessionTests} (identity test keys via
 * {@code @DynamicPropertySource}, OAuth2 client auto-configuration
 * excluded with in-memory stand-ins, Clock overridden with a Mockito
 * delegate to the shared {@link MutableClock}, IdP JWKSource overridden
 * with a test RSA key). No OAuth token material appears anywhere in this
 * suite: the logout mutation carries no token payload and sessions are
 * simulated through the REAL {@link SessionLifecycleService#createSession}
 * + {@code rotate} calls.
 *
 * <p>Numbered test methods map 1:1 to the SEC-P02 §6.4 required-test items
 * (task packet AC-04). Item 10 (wildcard CORS) is asserted at the
 * configuration level: there is no CorsConfigurationSource bean in the
 * context and no {@code .cors()} DSL usage in SecurityConfig, so the
 * observable proof is that a CORS preflight from a hostile origin receives
 * no {@code Access-Control-Allow-Origin} header at all (MockMvc OPTIONS
 * with Origin + Access-Control-Request-Method).</p>
 */
@SpringBootTest(classes = { BffApplication.class,
        SecP02CsrfTests.IdentityTestConfig.class }, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration",
        "spring.flyway.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecP02CsrfTests {

    private static final String PW = "evplatform_dev_only";
    private static final String DB = "bff_session_db";
    private static final String MIGRATOR = "bff_session_migrator";
    private static final String RUNTIME = "bff_session_runtime";
    private static final String SCHEMA = "bff_session";

    /** Issuer pinned via @DynamicPropertySource (unused by BCL here; needed by the BFF context). */
    private static final String ISSUER = "http://127.0.0.1:8180/realms/ev-local";

    /** Start instant of the shared MutableClock (every test is self-relative). */
    private static final Instant T0 =
            Instant.parse("2026-09-13T12:00:00Z").truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    private static final String TEST_SESSION_KEY_B64;
    private static final String TEST_CLIENT_KEY_PEM_PATH;

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
            Path file = Files.createTempFile("bff-secp02-client-key", ".pem");
            Files.writeString(file, pem);
            TEST_CLIENT_KEY_PEM_PATH = file.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SEC-P02 test key setup failed", e);
        }
    }

    @DynamicPropertySource
    static void identityTestProperties(DynamicPropertyRegistry registry) {
        registry.add("bff.session.encryption.keys.v1", () -> TEST_SESSION_KEY_B64);
        registry.add("bff.oauth.client-private-key-path", () -> TEST_CLIENT_KEY_PEM_PATH);
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
                () -> ISSUER);
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

    /** Overridden IdP JWKS source: serves the test RSA public key (context requirement). */
    @MockitoBean
    private com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> idpJwkSource;

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
                new TokenMaterial("fake-access-token-NOT-REAL", null,
                        now.plus(Duration.ofMinutes(5))),
                "urn:evplatform:acr:basic", now);
        return lifecycle.rotate(created.sessionRef(), now);
    }

    private Cookie sessionCookie(String ref) {
        return new Cookie(BffSessionSecurityContextRepository.COOKIE_NAME, ref);
    }

    /** Fetches the session-bound CSRF token through the real endpoint. */
    private String csrfTokenFor(String ref) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/session/csrf")
                        .cookie(sessionCookie(ref))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.token");
    }

    // ------------------------------------------------------------------
    // §6.4 item 1 — token issuance: GET /api/v1/session/csrf
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void item1_csrfEndpointIssuesSessionBoundTokenWithHeaderName() throws Exception {
        String ref = loginAs("subject-p02-item1", "sid-p02-item1");

        mockMvc.perform(get("/api/v1/session/csrf")
                        .cookie(sessionCookie(ref))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value(
                        BffSessionCsrfTokenRepository.CSRF_HEADER));
    }

    // ------------------------------------------------------------------
    // §6.4 item 2 — valid token succeeds
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void item2_mutationWithValidCsrfTokenSucceeds() throws Exception {
        String ref = loginAs("subject-p02-item2", "sid-p02-item2");
        String csrfToken = csrfTokenFor(ref);

        mockMvc.perform(post("/api/v1/session/logout")
                        .cookie(sessionCookie(ref))
                        .header(BffSessionCsrfTokenRepository.CSRF_HEADER, csrfToken)
                        .header("Origin", "http://127.0.0.1:4200")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // The mutation really happened: the session row is revoked.
        assertThat(lifecycle.loadValidSession(ref, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Expired.class);
    }

    // ------------------------------------------------------------------
    // §6.4 item 3 — missing token fails
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void item3_mutationWithoutCsrfTokenFailsWithCsrfValidationFailed() throws Exception {
        String ref = loginAs("subject-p02-item3", "sid-p02-item3");

        MvcResult result = mockMvc.perform(post("/api/v1/session/logout")
                        .cookie(sessionCookie(ref))
                        .header("Origin", "http://127.0.0.1:4200")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("CSRF_VALIDATION_FAILED"))
                .andReturn();

        // Generic failure body: never echoes the session reference or subject.
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(ref);
        assertThat(body).doesNotContain("subject-p02-item3");
    }

    // ------------------------------------------------------------------
    // §6.4 item 4 — token from another session fails
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void item4_tokenFromAnotherSessionFails() throws Exception {
        String refA = loginAs("subject-p02-item4-A", "sid-p02-item4-A");
        String refB = loginAs("subject-p02-item4-B", "sid-p02-item4-B");
        String csrfTokenB = csrfTokenFor(refB);

        MvcResult result = mockMvc.perform(post("/api/v1/session/logout")
                        .cookie(sessionCookie(refA))
                        .header(BffSessionCsrfTokenRepository.CSRF_HEADER, csrfTokenB)
                        .header("Origin", "http://127.0.0.1:4200")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("CSRF_VALIDATION_FAILED"))
                .andReturn();

        // A's session must be untouched (the forged mutation did nothing).
        assertThat(lifecycle.loadValidSession(refA, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Valid.class);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(refA);
        assertThat(body).doesNotContain(refB);
    }

    // ------------------------------------------------------------------
    // §6.4 item 5 — stale token after session rotation fails
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void item5_staleTokenFailsAfterRotation() throws Exception {
        String ref1 = loginAs("subject-p02-item5", "sid-p02-item5");
        String staleToken = csrfTokenFor(ref1);
        String ref2 = lifecycle.rotate(ref1, mutableClock.instant());

        // The NEW reference must be presented (a rotated session is only
        // usable through its current reference) — the stale TOKEN is what
        // must now fail.
        MvcResult result = mockMvc.perform(post("/api/v1/session/logout")
                        .cookie(sessionCookie(ref2))
                        .header(BffSessionCsrfTokenRepository.CSRF_HEADER, staleToken)
                        .header("Origin", "http://127.0.0.1:4200")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("CSRF_VALIDATION_FAILED"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(staleToken);
        assertThat(body).doesNotContain(ref1);
        assertThat(body).doesNotContain(ref2);
    }

    // ------------------------------------------------------------------
    // §6.4 item 6 — hostile Origin fails
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void item6_hostileOriginFailsWithOriginNotAllowed() throws Exception {
        String ref = loginAs("subject-p02-item6", "sid-p02-item6");
        String csrfToken = csrfTokenFor(ref);

        mockMvc.perform(post("/api/v1/session/logout")
                        .cookie(sessionCookie(ref))
                        .header(BffSessionCsrfTokenRepository.CSRF_HEADER, csrfToken)
                        .header("Origin", "http://evil.example")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ORIGIN_NOT_ALLOWED"));

        // The rejected mutation did nothing: the session stays ACTIVE.
        assertThat(lifecycle.loadValidSession(ref, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Valid.class);
    }

    // ------------------------------------------------------------------
    // §6.4 item 7 — forged Referer does not bypass Origin
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void item7_forgedRefererCannotBypassHostileOrigin() throws Exception {
        String ref = loginAs("subject-p02-item7", "sid-p02-item7");
        String csrfToken = csrfTokenFor(ref);

        mockMvc.perform(post("/api/v1/session/logout")
                        .cookie(sessionCookie(ref))
                        .header(BffSessionCsrfTokenRepository.CSRF_HEADER, csrfToken)
                        .header("Origin", "http://evil.example")
                        .header("Referer", "http://127.0.0.1:4200/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ORIGIN_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------------
    // §6.4 item 8 — form-encoded mutation fails
    // ------------------------------------------------------------------

    @Test
    @Order(8)
    void item8_formEncodedMutationFailsWithCsrfValidationFailed() throws Exception {
        String ref = loginAs("subject-p02-item8", "sid-p02-item8");
        String csrfToken = csrfTokenFor(ref);

        // Content-type rule (SEC-P02 §6.2/§6.4): form-urlencoded is not an
        // approved browser-mutation content type. The form-urlencoded
        // allowance is scoped to the back-channel logout receiver (SEC-P08
        // IdP notification, validated by its signed logout_token) — that
        // path is covered by BackChannelLogoutTests, not here.
        mockMvc.perform(post("/api/v1/session/logout")
                        .cookie(sessionCookie(ref))
                        .header(BffSessionCsrfTokenRepository.CSRF_HEADER, csrfToken)
                        .header("Origin", "http://127.0.0.1:4200")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("CSRF_VALIDATION_FAILED"));

        // The rejected mutation did nothing: the session stays ACTIVE.
        assertThat(lifecycle.loadValidSession(ref, mutableClock.instant()))
                .isInstanceOf(SessionLifecycleService.Loaded.Valid.class);
    }

    // ------------------------------------------------------------------
    // §6.4 item 9 — CSRF failure does not reveal sensitive state
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    void item9_csrfFailureBodyRevealsNoSensitiveState() throws Exception {
        String ref = loginAs("subject-p02-item9", "sid-p02-item9");
        String csrfToken = csrfTokenFor(ref);

        MvcResult result = mockMvc.perform(post("/api/v1/session/logout")
                        .cookie(sessionCookie(ref))
                        .header(BffSessionCsrfTokenRepository.CSRF_HEADER, csrfToken + "-tampered")
                        .header("Origin", "http://127.0.0.1:4200")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("CSRF_VALIDATION_FAILED"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // The supplied (invalid) token value, the session reference and the
        // subject must never appear in the failure body.
        assertThat(body).doesNotContain(csrfToken);
        assertThat(body).doesNotContain(ref);
        assertThat(body).doesNotContain("subject-p02-item9");
    }

    // ------------------------------------------------------------------
    // §6.4 item 10 — wildcard CORS with credentials is impossible
    // ------------------------------------------------------------------

    /**
     * Item 10 is asserted at the configuration level, documented here:
     *
     * <ul>
     *   <li>the application context contains NO application-registered
     *       {@code CorsConfigurationSource} bean and SecurityConfig uses no
     *       {@code .cors()} DSL — so no CORS mapping with an allow-all
     *       origin can exist (structural guarantee). The only
     *       {@code CorsConfigurationSource}-typed bean Spring MVC itself
     *       registers is {@code mvcHandlerMappingIntrospector} (handler-
     *       mapping introspection, not an application CORS mapping); the
     *       test additionally proves it yields NO CORS configuration for a
     *       preflight request;</li>
     *   <li>behaviorally, a CORS preflight from a hostile origin receives NO
     *       {@code Access-Control-Allow-Origin} header at all. The preflight
     *       is unauthenticated, so the status is 401 (or 403 where the
     *       chain rejects earlier) — the security-relevant assertion is the
     *       ABSENCE of the ACAO header, not the exact rejection code.</li>
     * </ul>
     */
    @Test
    @Order(10)
    void item10_wildcardCorsWithCredentialsIsImpossible() throws Exception {
        // Structural: no APPLICATION-registered CorsConfigurationSource bean
        // exists. "mvcHandlerMappingIntrospector" is registered by Spring
        // MVC itself (WebMvcConfigurationSupport) purely for handler-mapping
        // introspection — it is not an application CORS mapping and must
        // yield no CORS configuration, which is asserted below.
        String[] beanNames = webApplicationContext.getBeanNamesForType(
                org.springframework.web.cors.CorsConfigurationSource.class);
        assertThat(beanNames)
                .as("no application CorsConfigurationSource bean may be registered "
                        + "(wildcard CORS with credentials must be impossible)")
                .allSatisfy(name -> assertThat(name)
                        .isEqualTo("mvcHandlerMappingIntrospector"));
        for (String name : beanNames) {
            var source = (org.springframework.web.cors.CorsConfigurationSource)
                    webApplicationContext.getBean(name);
            assertThat(source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest(
                            "OPTIONS", "/api/v1/session/logout")))
                    .as("the framework introspector must not produce a CORS "
                            + "configuration for the preflight")
                    .isNull();
        }

        // Behavioral: a hostile-origin preflight gets no ACAO header. The
        // preflight is unauthenticated (no session cookie), so the exact
        // status is 401 (authentication entry point) or 403 (chain rejection)
        // depending on where the filter chain stops it — the security
        // property under test is that NO Access-Control-Allow-Origin is
        // ever returned.
        mockMvc.perform(options("/api/v1/session/logout")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(result -> assertThat(
                        result.getResponse().getStatus())
                        .as("preflight must be rejected (401 unauthenticated or 403)")
                        .isIn(401, 403))
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader("Access-Control-Allow-Origin"))
                        .as("a hostile-origin preflight must never receive "
                                + "Access-Control-Allow-Origin (wildcard or otherwise)")
                        .isNull());
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
