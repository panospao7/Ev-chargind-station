package com.evplatform.bff.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evplatform.libraries.testsupport.LocalDependencies;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * I1-IAM-001 phase 2 (D1 cluster): {@link JdbcSessionStore} behavior on
 * REAL PostgreSQL 18 (Testcontainers + the shared provisioning script).
 * The store is constructed over a {@link JdbcClient} bound to the
 * RUNTIME-role connection (matching production grants); rotation runs
 * inside a {@link TransactionTemplate} on the same DataSource so the two
 * statements of {@code rotateRef} execute in ONE transaction, as the
 * Spring {@code @Transactional} proxy arranges in production.
 *
 * <p>Coverage: full-field round-trip, touch semantics (idle moves,
 * absolute never extends), atomic reference rotation with the PASSED
 * (re-encrypted) ciphertext/key id, rotation of a non-ACTIVE reference
 * fails without side effects, decrypt-after-rotation round-trip (new
 * ciphertext authenticates against the NEW reference as AAD, old ciphertext
 * no longer authenticates), single revocation, subject+sid-scoped
 * revocation, ACTIVE-only lookup, and the pre-auth sentinel-subject
 * filtering contract (findByRef excludes '__pre_auth__' rows;
 * findPreAuthByRef/deletePreAuthByRef see them).</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JdbcSessionStoreTests {

    private static final String PW = "evplatform_dev_only";
    private static final String DB = "bff_session_db";
    private static final String MIGRATOR = "bff_session_migrator";
    private static final String RUNTIME = "bff_session_runtime";
    private static final String SCHEMA = "bff_session";

    private static final Instant T0 =
            Instant.parse("2026-09-13T12:00:00Z").truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    /**
     * Base64-encoded 32-byte AES-256 TEST key for the rotation
     * decrypt-after-rotation round-trip test (generated at class load; no
     * key material committed — AGENTS.md §4).
     */
    private static final String TEST_KEY_B64;

    static {
        byte[] aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);
        TEST_KEY_B64 = Base64.getEncoder().encodeToString(aesKey);
    }

    private static PostgreSQLContainer pg;
    private static DataSource runtimeDataSource;
    private static JdbcClient jdbcClient;
    private static JdbcSessionStore store;
    private static TransactionTemplate tx;

    /** Refs must satisfy the V2 CHECK (char_length >= 32). */
    private static String ref(String tag) {
        return "test-ref-" + tag + "-0000000000000000000000000000";
    }

    /** Parses a JSON document for semantic comparison (jsonb canonicalizes text). */
    private static com.fasterxml.jackson.databind.JsonNode readJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("invalid test json", e);
        }
    }

    private static BffSession activeSession(String sessionRef, String subject,
                                            String sid, Instant now) {
        return new BffSession(
                sessionRef,
                subject,
                sid,
                new byte[] {1, 2, 3, 4},
                "v1",
                "urn:evplatform:acr:basic",
                now,
                now,
                now,
                now.plus(Duration.ofMinutes(30)),
                now.plus(Duration.ofHours(8)),
                BffSession.RevocationState.ACTIVE,
                "{\"csrf\":\"token-" + sessionRef.substring(sessionRef.length() - 4) + "\"}");
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
        runtimeDataSource = new SimpleDriverDataSource(
                new org.postgresql.Driver(), url, RUNTIME, PW);
        jdbcClient = JdbcClient.create(runtimeDataSource);
        store = new JdbcSessionStore(
                jdbcClient,
                java.time.Clock.systemUTC(),
                new BffSessionProperties(
                        new BffSessionProperties.Session(
                                Duration.ofMinutes(30), Duration.ofHours(8),
                                "__Host-evsession", List.of(), null),
                        new BffSessionProperties.OAuth(null)));
        tx = new TransactionTemplate(new DataSourceTransactionManager(runtimeDataSource));
    }

    @AfterAll
    static void stopContainer() {
        if (pg != null) {
            pg.stop();
        }
    }

    @Test
    @Order(1)
    void insertAndFindByRefRoundTripPreservesAllFields() {
        BffSession session = activeSession(ref("rt"), "subject-rt", "sid-rt", T0);
        store.insert(session);

        Optional<BffSession> loaded = store.findByRef(session.sessionRef());
        assertTrue(loaded.isPresent(), "inserted session must be found by ref");
        BffSession s = loaded.get();
        assertEquals(session.sessionRef(), s.sessionRef());
        assertEquals("subject-rt", s.keycloakSubject());
        assertEquals("sid-rt", s.keycloakSid());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, s.encryptedTokenMaterial());
        assertEquals("v1", s.tokenEncryptionKeyId());
        assertEquals("urn:evplatform:acr:basic", s.acr());
        assertEquals(T0, s.authnTime());
        assertEquals(T0, s.createdAt());
        assertEquals(T0, s.lastActivityAt());
        assertEquals(T0.plus(Duration.ofMinutes(30)), s.idleExpiresAt());
        assertEquals(T0.plus(Duration.ofHours(8)), s.absoluteExpiresAt());
        assertEquals(BffSession.RevocationState.ACTIVE, s.revocationState());
        // jsonb does not preserve exact text formatting (PostgreSQL
        // canonicalizes whitespace), so compare parsed content, not strings
        assertEquals(readJson(session.securityEventMetadata()), readJson(s.securityEventMetadata()));
    }

    @Test
    @Order(2)
    void touchAdvancesActivityAndIdleButNeverTheAbsoluteExpiry() {
        BffSession session = activeSession(ref("touch"), "subject-touch", "sid-touch", T0);
        store.insert(session);
        Instant absoluteBefore = store.findByRef(session.sessionRef()).orElseThrow()
                .absoluteExpiresAt();

        Instant later = T0.plus(Duration.ofMinutes(10));
        store.touch(session.sessionRef(), later);

        BffSession after = store.findByRef(session.sessionRef()).orElseThrow();
        assertEquals(later, after.lastActivityAt(),
                "touch must advance last_activity_at");
        assertEquals(later.plus(Duration.ofMinutes(30)), after.idleExpiresAt(),
                "touch must re-derive idle_expires_at from now");
        assertEquals(absoluteBefore, after.absoluteExpiresAt(),
                "touch must NEVER extend the absolute expiry (SEC-001 §5.2)");
    }

    @Test
    @Order(3)
    void rotateRefMarksOldRevokedAndInsertsNewActiveAtomically() {
        BffSession original = activeSession(ref("rot"), "subject-rot", "sid-rot", T0);
        store.insert(original);
        String newRef = ref("rot-new");
        byte[] reEncrypted = new byte[] {9, 9, 8, 8, 7};

        tx.executeWithoutResult(status -> store.rotateRef(
                original.sessionRef(), newRef, T0, reEncrypted, "v2"));

        Optional<BffSession> oldRow = store.findByRef(original.sessionRef());
        assertTrue(oldRow.isPresent(), "the old row must still exist (marked, not deleted)");
        assertEquals(BffSession.RevocationState.REVOKED, oldRow.get().revocationState(),
                "the old reference must be REVOKED after rotation");

        Optional<BffSession> newRow = store.findByRef(newRef);
        assertTrue(newRow.isPresent(), "the new row must exist after rotation");
        assertEquals(BffSession.RevocationState.ACTIVE, newRow.get().revocationState(),
                "the new reference must be ACTIVE after rotation");
        // identity carries over; the PASSED ciphertext/keyId are stored
        // (the lifecycle service re-encrypts under the new ref before
        // calling rotateRef — the ciphertext is never copied between rows);
        // activity windows reset. The session-bound CSRF synchronizer token
        // does NOT carry over (SEC-P02 §6.1: the token rotates with the
        // session), so the new row starts with an empty metadata document.
        assertEquals("subject-rot", newRow.get().keycloakSubject());
        assertEquals("sid-rot", newRow.get().keycloakSid());
        assertArrayEquals(reEncrypted, newRow.get().encryptedTokenMaterial(),
                "the new row must carry the PASSED re-encrypted material, "
                        + "not a copy of the old ciphertext");
        assertFalse(java.util.Arrays.equals(original.encryptedTokenMaterial(),
                        newRow.get().encryptedTokenMaterial()),
                "the stored ciphertext must CHANGE across rotation "
                        + "(re-encryption under the new ref as AAD)");
        assertEquals("v2", newRow.get().tokenEncryptionKeyId(),
                "the new row must carry the PASSED key id");
        assertEquals("urn:evplatform:acr:basic", newRow.get().acr(),
                "acr must carry over from the old row");
        assertEquals(readJson("{}"), readJson(newRow.get().securityEventMetadata()),
                "rotation must reset security_event_metadata (stale CSRF token must not survive)");
        assertEquals(T0, newRow.get().lastActivityAt());
        assertEquals(T0.plus(Duration.ofMinutes(30)), newRow.get().idleExpiresAt());
        assertEquals(T0.plus(Duration.ofHours(8)), newRow.get().absoluteExpiresAt());

        // the rotated-away reference can never resolve to an ACTIVE row
        assertTrue(store.findActiveBySubjectAndSid("subject-rot", "sid-rot").isEmpty()
                        || !ref("rot").equals(store.findActiveBySubjectAndSid(
                                "subject-rot", "sid-rot").orElseThrow().sessionRef()),
                "the old reference must not be the ACTIVE row for the subject+sid pair");
    }

    @Test
    @Order(4)
    void rotatingAnAlreadyRevokedRefFailsWithoutSideEffects() {
        BffSession original = activeSession(ref("rot2"), "subject-rot2", "sid-rot2", T0);
        store.insert(original);
        String newRef = ref("rot2-new");
        // revoke first so the old ref is no longer ACTIVE
        assertTrue(store.revoke(original.sessionRef()));

        assertThrows(IllegalStateException.class,
                () -> tx.executeWithoutResult(status ->
                        store.rotateRef(original.sessionRef(), newRef, T0,
                                new byte[] {1}, "v1")),
                "rotating a non-ACTIVE reference must fail");

        // no partial state: the old row is unchanged and no new row exists
        assertEquals(BffSession.RevocationState.REVOKED,
                store.findByRef(original.sessionRef()).orElseThrow().revocationState());
        assertTrue(store.findByRef(newRef).isEmpty(),
                "a failed rotation must not leave the new row behind");
    }

    @Test
    @Order(5)
    void revokeMarksSingleRowRevoked() {
        BffSession session = activeSession(ref("rvk"), "subject-rvk", "sid-rvk", T0);
        store.insert(session);

        assertTrue(store.revoke(session.sessionRef()),
                "revoking an ACTIVE session must report success");
        assertEquals(BffSession.RevocationState.REVOKED,
                store.findByRef(session.sessionRef()).orElseThrow().revocationState());
        // revoking again is a no-op (the row is no longer ACTIVE)
        assertFalse(store.revoke(session.sessionRef()),
                "revoking an already-REVOKED session must report no rows changed");
    }

    @Test
    @Order(6)
    void revokeBySubjectAndSidRevokesOnlyMatchingRows() {
        // A+sid1, A+sid2, B+sid1 — revoking (A, sid1) must touch ONLY that row
        BffSession a1 = activeSession(ref("m-a1"), "subject-multi-A", "sid-1", T0);
        BffSession a2 = activeSession(ref("m-a2"), "subject-multi-A", "sid-2", T0);
        BffSession b1 = activeSession(ref("m-b1"), "subject-multi-B", "sid-1", T0);
        store.insert(a1);
        store.insert(a2);
        store.insert(b1);

        int revoked = store.revokeBySubjectAndSid("subject-multi-A", "sid-1");
        assertEquals(1, revoked, "exactly one row must match (A, sid1)");

        assertEquals(BffSession.RevocationState.REVOKED,
                store.findByRef(a1.sessionRef()).orElseThrow().revocationState(),
                "the (A, sid1) row must be REVOKED");
        assertEquals(BffSession.RevocationState.ACTIVE,
                store.findByRef(a2.sessionRef()).orElseThrow().revocationState(),
                "the (A, sid2) row must stay ACTIVE");
        assertEquals(BffSession.RevocationState.ACTIVE,
                store.findByRef(b1.sessionRef()).orElseThrow().revocationState(),
                "the (B, sid1) row must stay ACTIVE");
    }

    @Test
    @Order(7)
    void findActiveBySubjectAndSidReturnsOnlyActiveRows() {
        // The committed store API resolves at most ONE ACTIVE row per
        // subject+sid pair (rotation atomically revokes the previous
        // reference, so two simultaneous ACTIVE rows for one pair cannot
        // arise operationally). The test asserts exactly that contract:
        // a REVOKED row for the pair never resolves; the ACTIVE one does.
        BffSession current = activeSession(ref("act1"), "subject-active", "sid-active", T0);
        store.insert(current);
        assertTrue(store.findActiveBySubjectAndSid("subject-active", "sid-active").isPresent(),
                "the ACTIVE row must resolve for the pair");

        assertTrue(store.revoke(current.sessionRef()),
                "revoking the ACTIVE row must succeed");
        assertTrue(store.findActiveBySubjectAndSid("subject-active", "sid-active").isEmpty(),
                "a REVOKED row must never resolve via findActiveBySubjectAndSid");

        // a fresh ACTIVE row for the same pair resolves again (re-login)
        BffSession reLogin = activeSession(ref("act2"), "subject-active", "sid-active",
                T0.plusSeconds(1));
        store.insert(reLogin);
        Optional<BffSession> reResolved = store.findActiveBySubjectAndSid(
                "subject-active", "sid-active");
        assertTrue(reResolved.isPresent(), "the new ACTIVE row must resolve");
        assertEquals(reLogin.sessionRef(), reResolved.get().sessionRef(),
                "the resolved row must be the ACTIVE one, not the REVOKED one");

        // unknown pairs resolve to empty
        assertTrue(store.findActiveBySubjectAndSid("subject-active", "sid-unknown").isEmpty());
    }

    // ------------------------------------------------------------------
    // Rotation re-encryption contract (I1-IAM-001 closeout M-1)
    // ------------------------------------------------------------------

    /**
     * Decrypt-after-rotation round-trip: the lifecycle contract is that
     * rotation re-encrypts the token material under the NEW reference as
     * AAD. This test drives the REAL crypto stack (TokenEncryptionService
     * over a test key ring): encrypt under the old ref → insert → rotate
     * via the new 5-arg rotateRef with re-encryption under the new ref →
     * load → decrypt with the new ref succeeds and returns the original
     * plaintext; the OLD ciphertext no longer authenticates against the new
     * ref (AAD binding) and the new ciphertext no longer authenticates
     * against the old ref.
     */
    @Test
    @Order(8)
    void rotateRefStoresReEncryptedMaterialThatDecryptsWithTheNewRef() throws Exception {
        SessionKeyRing keyRing = new SessionKeyRing(
                java.util.Map.of("v1", TEST_KEY_B64));
        TokenEncryptionService crypto = new TokenEncryptionService(keyRing);
        String plaintext = "{\"accessToken\":\"rt-plain-token\",\"refreshToken\":null,"
                + "\"expiresAt\":\"2026-09-13T20:00:00Z\"}";

        BffSession original = activeSession(ref("crypt"), "subject-crypt", "sid-crypt", T0);
        byte[] oldCiphertext =
                crypto.encrypt(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        original.sessionRef()).ciphertext();
        // The fixed activeSession fixture uses a static byte[] placeholder,
        // so the row is inserted first and the ciphertext is then replaced
        // via a raw UPDATE with the real old-ref-bound ciphertext.
        store.insert(original);
        jdbcClient.sql("""
                UPDATE bff_session.bff_session
                SET encrypted_token_material = ?
                WHERE session_ref = ?
                """)
                .param(oldCiphertext)
                .param(original.sessionRef())
                .update();

        String newRef = ref("crypt-new");
        byte[] newCiphertext =
                crypto.encrypt(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        newRef).ciphertext();
        tx.executeWithoutResult(status -> store.rotateRef(
                original.sessionRef(), newRef, T0, newCiphertext, "v1"));

        BffSession rotated = store.findByRef(newRef).orElseThrow();
        assertArrayEquals(newCiphertext, rotated.encryptedTokenMaterial(),
                "the rotated row must carry the re-encrypted material");
        // decrypt with the NEW ref → original plaintext
        byte[] decrypted = crypto.decrypt(rotated.encryptedTokenMaterial(),
                rotated.tokenEncryptionKeyId(), rotated.sessionRef());
        assertEquals(plaintext,
                new String(decrypted, java.nio.charset.StandardCharsets.UTF_8),
                "the re-encrypted material must decrypt with the NEW reference as AAD");
        // the OLD ciphertext must NOT authenticate against the new ref
        // (AAD binding) — the copy-the-ciphertext defect would break here
        assertThrows(java.security.GeneralSecurityException.class,
                () -> crypto.decrypt(oldCiphertext, "v1", newRef),
                "the old ciphertext is bound to the old ref as AAD and must fail "
                        + "against the new ref");
        // and the new ciphertext must NOT authenticate against the old ref
        assertThrows(java.security.GeneralSecurityException.class,
                () -> crypto.decrypt(newCiphertext, "v1", original.sessionRef()),
                "the new ciphertext is bound to the new ref as AAD and must fail "
                        + "against the old ref");
    }

    // ------------------------------------------------------------------
    // Pre-auth sentinel-subject filtering (I1-IAM-001 closeout M-3)
    // ------------------------------------------------------------------

    /**
     * The security-context repository must NEVER resolve a pre-auth row:
     * {@code findByRef} excludes the '__pre_auth__' sentinel subject, while
     * {@code findPreAuthByRef} and {@code deletePreAuthByRef} operate on
     * exactly those rows.
     */
    @Test
    @Order(9)
    void findByRefExcludesPreAuthRowsWhilePreAuthAccessorsSeeThem() {
        String state = ref("preauth"); // ≥32 chars, satisfies the CHECK
        BffSession preAuth = new BffSession(
                state,
                "__pre_auth__",
                null,
                new byte[] {5, 6, 7, 8},
                "v1",
                null,
                T0,
                T0,
                T0,
                T0.plus(Duration.ofMinutes(10)),
                T0.plus(Duration.ofMinutes(10)),
                BffSession.RevocationState.ACTIVE,
                "{}");
        store.insert(preAuth);

        // the security-context lookup must NOT match the pre-auth row
        assertTrue(store.findByRef(state).isEmpty(),
                "findByRef must exclude '__pre_auth__' rows (the security-context "
                        + "repository must never resolve an authorization request)");
        // the authorization-request accessors DO see it
        Optional<BffSession> viaPreAuth = store.findPreAuthByRef(state);
        assertTrue(viaPreAuth.isPresent(), "findPreAuthByRef must match the pre-auth row");
        assertEquals("__pre_auth__", viaPreAuth.get().keycloakSubject());
        // single-use delete
        assertTrue(store.deletePreAuthByRef(state), "the pre-auth row must delete");
        assertFalse(store.deletePreAuthByRef(state),
                "a second delete is a no-op (single-use authorization request)");
        assertTrue(store.findPreAuthByRef(state).isEmpty(),
                "the row must be gone after deletePreAuthByRef");
        // a normal row is untouched by the pre-auth delete
        BffSession normal = activeSession(ref("preauth-normal"), "subject-pa", "sid-pa", T0);
        store.insert(normal);
        assertFalse(store.deletePreAuthByRef(normal.sessionRef()),
                "deletePreAuthByRef must never delete a real session row");
        assertTrue(store.findByRef(normal.sessionRef()).isPresent(),
                "the real session row must survive");
    }
}
