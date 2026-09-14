package com.evplatform.bff.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.BffSessionProperties;
import com.evplatform.bff.session.JdbcSessionStore;
import com.evplatform.bff.session.SessionKeyRing;
import com.evplatform.bff.session.TokenEncryptionService;
import com.evplatform.libraries.testsupport.LocalDependencies;
import com.evplatform.libraries.testsupport.MutableClock;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * I1-IAM-001 closeout (M-3): {@link StoreBackedAuthorizationRequestRepository}
 * behavior on REAL PostgreSQL 18 (Testcontainers + the shared provisioning
 * script), following the {@code JdbcSessionStoreTests} pattern — static
 * container, migrator-role Flyway provisioning, runtime-role JdbcClient.
 *
 * <p>Coverage: (a) save → pre-auth row exists with the sentinel subject,
 * stored ciphertext differs from the plaintext bytes, and NO servlet session
 * is created (no JSESSIONID source); (b) load → full round-trip equality of
 * the reconstructed {@link OAuth2AuthorizationRequest} (state, clientId,
 * redirectUri, scopes, code_verifier attribute, code_challenge additional
 * parameter); (c) remove → returns the request AND deletes the row;
 * (d) second load → null (single use); (e) {@code findByRef(state)} → empty
 * (sentinel filtering — a pre-auth row can never authenticate); (f) an
 * expired row → load returns null.</p>
 *
 * <p>The repository is constructed with a test 32-byte AES key via the REAL
 * {@link SessionKeyRing} and {@link TokenEncryptionService}; no key material
 * is committed (AGENTS.md §4).</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StoreBackedAuthorizationRequestRepositoryTests {

    private static final String PW = "evplatform_dev_only";
    private static final String DB = "bff_session_db";
    private static final String MIGRATOR = "bff_session_migrator";
    private static final String RUNTIME = "bff_session_runtime";
    private static final String SCHEMA = "bff_session";

    /** Start instant of the shared MutableClock (every test is self-relative). */
    private static final Instant T0 =
            Instant.parse("2026-09-13T12:00:00Z").truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    /**
     * Base64-encoded 32-byte AES-256 TEST key (generated at class load; no
     * key material committed — AGENTS.md §4).
     */
    private static final String TEST_KEY_B64;

    static {
        byte[] aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);
        TEST_KEY_B64 = Base64.getEncoder().encodeToString(aesKey);
    }

    private static PostgreSQLContainer pg;
    private static MutableClock mutableClock;
    private static JdbcSessionStore store;
    private static JdbcClient jdbcClient;
    private static StoreBackedAuthorizationRequestRepository repository;
    private static TokenEncryptionService crypto;

    /** State values must satisfy the V2 CHECK (char_length >= 32). */
    private static String state(String tag) {
        return "test-state-" + tag + "-00000000000000000000000000";
    }

    /** A representative authorization-code + PKCE authorization request. */
    private static OAuth2AuthorizationRequest authRequest(String stateValue) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .clientId("ev-bff")
                .authorizationUri("https://idp.example/realms/ev-local/protocol/openid-connect/auth")
                .redirectUri("http://localhost:8080/login/oauth2/code/ev-bff")
                .scope("openid", "profile")
                .state(stateValue)
                .additionalParameters(Map.of("code_challenge", "test-code-challenge-value",
                        "code_challenge_method", "S256"))
                .attributes(Map.of("code_verifier", "test-code-verifier-value"))
                .build();
    }

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

        String url = "jdbc:postgresql://" + pg.getHost() + ":"
                + pg.getMappedPort(5432) + "/" + DB;
        DataSource runtimeDataSource = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.postgresql.Driver(), url, RUNTIME, PW);
        jdbcClient = JdbcClient.create(runtimeDataSource);
        mutableClock = MutableClock.at(T0.toString());
        store = new JdbcSessionStore(
                jdbcClient,
                mutableClock,
                new BffSessionProperties(
                        new BffSessionProperties.Session(
                                Duration.ofMinutes(30), Duration.ofHours(8),
                                "__Host-evsession", List.of(), null),
                        new BffSessionProperties.OAuth(null)));
        SessionKeyRing keyRing = new SessionKeyRing(Map.of("v1", TEST_KEY_B64));
        crypto = new TokenEncryptionService(keyRing);
        repository = new StoreBackedAuthorizationRequestRepository(
                store, crypto, mutableClock);
    }

    @AfterAll
    static void stopContainer() {
        if (pg != null) {
            pg.stop();
        }
    }

    // ------------------------------------------------------------------
    // (a) save → pre-auth row with sentinel subject, encrypted at rest,
    //     no servlet session created
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void savePersistsEncryptedPreAuthRowWithoutCreatingAServletSession() throws Exception {
        String stateValue = state("save");
        OAuth2AuthorizationRequest authReq = authRequest(stateValue);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/ev-bff");
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(authReq, request, response);

        // NO servlet session may be created by the save path (the BFF runs
        // SessionCreationPolicy.NEVER; there is no JSESSIONID source).
        assertNull(request.getSession(false),
                "saveAuthorizationRequest must not create an HTTP session");

        // The pre-auth row exists with the sentinel subject.
        Optional<BffSession> row = store.findPreAuthByRef(stateValue);
        assertTrue(row.isPresent(), "the pre-auth row must exist after save");
        assertEquals(StoreBackedAuthorizationRequestRepository.PRE_AUTH_SUBJECT,
                row.get().keycloakSubject(),
                "the row must carry the '__pre_auth__' sentinel subject");
        assertNull(row.get().keycloakSid(), "pre-auth rows have no sid");
        assertEquals(BffSession.RevocationState.ACTIVE, row.get().revocationState());
        assertEquals(T0.plus(Duration.ofMinutes(10)), row.get().absoluteExpiresAt(),
                "the pre-auth TTL is 10 minutes");

        // The stored material is NOT the plaintext JSON bytes: the
        // serialized document must not be recoverable from the row.
        byte[] stored = row.get().encryptedTokenMaterial();
        assertTrue(stored.length > 12,
                "the stored material must include the GCM IV + ciphertext + tag");
        String plaintextCandidate =
                new String(stored, java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(plaintextCandidate.contains("ev-bff"),
                "the stored bytes must not contain the plaintext document");
        assertFalse(plaintextCandidate.contains("code_verifier"),
                "the stored bytes must not contain the plaintext attributes");
        // Decrypting with the state as AAD returns the original document —
        // proving the AAD binding is the state value.
        byte[] plain = crypto.decrypt(stored, row.get().tokenEncryptionKeyId(), stateValue);
        assertNotNull(plain);
    }

    // ------------------------------------------------------------------
    // (b) load → round-trip equality of the authorization request
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void loadReconstructsTheAuthorizationRequestIncludingPkceMaterial() {
        String stateValue = state("load");
        OAuth2AuthorizationRequest original = authRequest(stateValue);
        repository.saveAuthorizationRequest(original,
                new MockHttpServletRequest("GET", "/oauth2/authorization/ev-bff"),
                new MockHttpServletResponse());

        MockHttpServletRequest callback = new MockHttpServletRequest(
                "GET", "/login/oauth2/code/ev-bff");
        callback.setParameter("state", stateValue);

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(callback);

        assertNotNull(loaded, "the authorization request must round-trip");
        assertEquals(original.getState(), loaded.getState());
        assertEquals(original.getClientId(), loaded.getClientId());
        assertEquals(original.getRedirectUri(), loaded.getRedirectUri());
        assertEquals(original.getScopes(), loaded.getScopes());
        assertEquals(original.getAuthorizationUri(), loaded.getAuthorizationUri());
        assertEquals(original.getAuthorizationRequestUri(),
                loaded.getAuthorizationRequestUri(),
                "the verbatim authorizationRequestUri must survive the round-trip");
        assertEquals(original.getGrantType(), loaded.getGrantType());
        assertEquals(original.getResponseType(), loaded.getResponseType());
        assertEquals("test-code-verifier-value", loaded.getAttribute("code_verifier"),
                "the PKCE code_verifier attribute must survive the round-trip");
        assertEquals("test-code-challenge-value",
                loaded.getAdditionalParameters().get("code_challenge"),
                "the PKCE code_challenge additional parameter must survive the round-trip");
        assertEquals("S256", loaded.getAdditionalParameters().get("code_challenge_method"));
        // Full structural equality with the original (Spring's equals
        // compares all fields).
        assertEquals(original, loaded,
                "the reconstructed request must equal the original");
    }

    // ------------------------------------------------------------------
    // (c) remove → returns the request + deletes the row (single use)
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void removeReturnsTheRequestAndDeletesTheRow() {
        String stateValue = state("remove");
        OAuth2AuthorizationRequest original = authRequest(stateValue);
        repository.saveAuthorizationRequest(original,
                new MockHttpServletRequest("GET", "/oauth2/authorization/ev-bff"),
                new MockHttpServletResponse());
        assertTrue(store.findPreAuthByRef(stateValue).isPresent());

        MockHttpServletRequest callback = new MockHttpServletRequest(
                "GET", "/login/oauth2/code/ev-bff");
        callback.setParameter("state", stateValue);

        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(
                callback, new MockHttpServletResponse());

        assertNotNull(removed, "remove must return the loaded request");
        assertEquals(original, removed);
        assertTrue(store.findPreAuthByRef(stateValue).isEmpty(),
                "the pre-auth row must be deleted by remove (single use)");
    }

    // ------------------------------------------------------------------
    // (d) second load → null (single-use)
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void secondLoadReturnsNull() {
        String stateValue = state("second");
        OAuth2AuthorizationRequest original = authRequest(stateValue);
        repository.saveAuthorizationRequest(original,
                new MockHttpServletRequest("GET", "/oauth2/authorization/ev-bff"),
                new MockHttpServletResponse());

        MockHttpServletRequest callback = new MockHttpServletRequest(
                "GET", "/login/oauth2/code/ev-bff");
        callback.setParameter("state", stateValue);

        assertNotNull(repository.loadAuthorizationRequest(callback));
        // remove (as the callback leg does) deletes the row...
        repository.removeAuthorizationRequest(callback, new MockHttpServletResponse());
        // ...so a second load resolves nothing.
        assertNull(repository.loadAuthorizationRequest(callback),
                "a second load must return null (single-use authorization request)");
    }

    // ------------------------------------------------------------------
    // (e) findByRef(state) → empty (sentinel filter — a pre-auth row can
    //     never authenticate)
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void findByRefDoesNotResolveAPreAuthRow() {
        String stateValue = state("sentinel");
        repository.saveAuthorizationRequest(authRequest(stateValue),
                new MockHttpServletRequest("GET", "/oauth2/authorization/ev-bff"),
                new MockHttpServletResponse());

        assertTrue(store.findPreAuthByRef(stateValue).isPresent(),
                "the pre-auth row exists for the authorization-request path");
        assertTrue(store.findByRef(stateValue).isEmpty(),
                "findByRef must NOT resolve a pre-auth row (sentinel filter) — "
                        + "the security-context repository can never authenticate it");
    }

    // ------------------------------------------------------------------
    // (f) expired row → load returns null
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void loadReturnsNullForAnExpiredPreAuthRow() {
        String stateValue = state("expired");
        repository.saveAuthorizationRequest(authRequest(stateValue),
                new MockHttpServletRequest("GET", "/oauth2/authorization/ev-bff"),
                new MockHttpServletResponse());
        assertTrue(store.findPreAuthByRef(stateValue).isPresent());

        // Advance the shared clock beyond the 10-minute TTL.
        mutableClock.advance(Duration.ofMinutes(11));

        MockHttpServletRequest callback = new MockHttpServletRequest(
                "GET", "/login/oauth2/code/ev-bff");
        callback.setParameter("state", stateValue);

        assertNull(repository.loadAuthorizationRequest(callback),
                "an expired authorization request must load as null");
        assertTrue(store.findPreAuthByRef(stateValue).isEmpty(),
                "the expired row must be lazily deleted on load");
    }
}
