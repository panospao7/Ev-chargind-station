package com.evplatform.bff.session;

import com.evplatform.bff.session.BffSession.RevocationState;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC store for {@code bff_session.bff_session} (SEC-001 §5.3, V2
 * migration). All SQL is parameterized (AGENTS.md §10); this component owns
 * no business logic beyond row mechanics — validity decisions belong to
 * {@link SessionLifecycleService}.
 *
 * <p>{@link #rotateRef(String, String, Instant)} is a single transaction:
 * the old row is marked REVOKED and the new row inserted atomically, so a
 * stolen pre-rotation reference can never resolve to an ACTIVE row.</p>
 */
@Component
public class JdbcSessionStore {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;

    public JdbcSessionStore(JdbcClient jdbc,
                            Clock clock,
                            BffSessionProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.idleTimeout = properties.session().idleTimeout();
        this.absoluteTimeout = properties.session().absoluteTimeout();
    }

    /** Inserts a fully-formed session row. */
    public void insert(BffSession session) {
        jdbc.sql("""
                INSERT INTO bff_session.bff_session
                    (session_ref, keycloak_subject, keycloak_sid,
                     encrypted_token_material, token_encryption_key_id, acr,
                     authn_time, created_at, last_activity_at,
                     idle_expires_at, absolute_expires_at, revocation_state,
                     security_event_metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """)
                .param(session.sessionRef())
                .param(session.keycloakSubject())
                .param(session.keycloakSid())
                .param(session.encryptedTokenMaterial())
                .param(session.tokenEncryptionKeyId())
                .param(session.acr())
                .param(Timestamp.from(session.authnTime()))
                .param(Timestamp.from(session.createdAt()))
                .param(Timestamp.from(session.lastActivityAt()))
                .param(Timestamp.from(session.idleExpiresAt()))
                .param(Timestamp.from(session.absoluteExpiresAt()))
                .param(session.revocationState().name())
                .param(session.securityEventMetadata() == null
                        ? "{}" : session.securityEventMetadata())
                .update();
    }

    /** Finds a session row by its opaque reference. */
    public Optional<BffSession> findByRef(String sessionRef) {
        return jdbc.sql("""
                SELECT session_ref, keycloak_subject, keycloak_sid,
                       encrypted_token_material, token_encryption_key_id, acr,
                       authn_time, created_at, last_activity_at,
                       idle_expires_at, absolute_expires_at, revocation_state,
                       security_event_metadata::text
                FROM bff_session.bff_session
                WHERE session_ref = ?
                """)
                .param(sessionRef)
                .query((rs, i) -> mapRow(rs))
                .optional();
    }

    /** Finds the ACTIVE session for a subject+sid pair (back-channel logout mapping). */
    public Optional<BffSession> findActiveBySubjectAndSid(String subject, String sid) {
        return jdbc.sql("""
                SELECT session_ref, keycloak_subject, keycloak_sid,
                       encrypted_token_material, token_encryption_key_id, acr,
                       authn_time, created_at, last_activity_at,
                       idle_expires_at, absolute_expires_at, revocation_state,
                       security_event_metadata::text
                FROM bff_session.bff_session
                WHERE keycloak_subject = ? AND keycloak_sid = ?
                  AND revocation_state = 'ACTIVE'
                """)
                .param(subject)
                .param(sid)
                .query((rs, i) -> mapRow(rs))
                .optional();
    }

    /**
     * Touches activity: advances {@code last_activity_at} and re-derives
     * {@code idle_expires_at} from {@code now}. The absolute expiry is never
     * extended by activity (SEC-001 §5.2).
     */
    public void touch(String sessionRef, Instant now) {
        Instant idleExpiresAt = now.plus(idleTimeout);
        jdbc.sql("""
                UPDATE bff_session.bff_session
                SET last_activity_at = ?, idle_expires_at = ?
                WHERE session_ref = ? AND revocation_state = 'ACTIVE'
                """)
                .param(Timestamp.from(now))
                .param(Timestamp.from(idleExpiresAt))
                .param(sessionRef)
                .update();
    }

    /**
     * Updates the security-event metadata JSON document for a session.
     * Used by the CSRF synchronizer repository (SEC-P02 §6.1: the
     * session-bound token lives in the row, so it rotates with the session).
     */
    public void updateSecurityEventMetadata(String sessionRef, String metadataJson) {
        jdbc.sql("""
                UPDATE bff_session.bff_session
                SET security_event_metadata = ?::jsonb
                WHERE session_ref = ?
                """)
                .param(metadataJson)
                .param(sessionRef)
                .update();
    }

    /**
     * Marks a single session REVOKED.
     *
     * @return true when an ACTIVE row was found and revoked
     */
    public boolean revoke(String sessionRef) {
        return jdbc.sql("""
                UPDATE bff_session.bff_session
                SET revocation_state = 'REVOKED'
                WHERE session_ref = ? AND revocation_state = 'ACTIVE'
                """)
                .param(sessionRef)
                .update() == 1;
    }

    /**
     * Revokes every ACTIVE session of a subject+sid pair (back-channel
     * logout / SEC-P08).
     *
     * @return number of rows revoked
     */
    public int revokeBySubjectAndSid(String subject, String sid) {
        return jdbc.sql("""
                UPDATE bff_session.bff_session
                SET revocation_state = 'REVOKED'
                WHERE keycloak_subject = ? AND keycloak_sid = ?
                  AND revocation_state = 'ACTIVE'
                """)
                .param(subject)
                .param(sid)
                .update();
    }

    /**
     * Atomic rotation (SEC-P01 §5.1 step 10: session ID rotates after
     * authentication): the old reference is marked REVOKED and the new row
     * inserted in ONE transaction, so an observer can never see both
     * references ACTIVE. The new row starts with an EMPTY
     * {@code security_event_metadata}: the session-bound CSRF synchronizer
     * token (SEC-P02 §6.1) must NOT carry over — a stale token from the
     * pre-rotation session must fail after rotation ("token rotates after
     * login and session rotation").
     */
    @Transactional
    public void rotateRef(String oldRef, String newRef, Instant now) {
        int revoked = jdbc.sql("""
                UPDATE bff_session.bff_session
                SET revocation_state = 'REVOKED'
                WHERE session_ref = ? AND revocation_state = 'ACTIVE'
                """)
                .param(oldRef)
                .update();
        if (revoked != 1) {
            throw new IllegalStateException(
                    "Session rotation failed: old session is not ACTIVE");
        }
        Instant idleExpiresAt = now.plus(idleTimeout);
        Instant absoluteExpiresAt = now.plus(absoluteTimeout);
        jdbc.sql("""
                INSERT INTO bff_session.bff_session
                    (session_ref, keycloak_subject, keycloak_sid,
                     encrypted_token_material, token_encryption_key_id, acr,
                     authn_time, created_at, last_activity_at,
                     idle_expires_at, absolute_expires_at, revocation_state,
                     security_event_metadata)
                SELECT ?, keycloak_subject, keycloak_sid,
                       encrypted_token_material, token_encryption_key_id, acr,
                       authn_time, ?, ?,
                       ?, ?, 'ACTIVE',
                       '{}'::jsonb
                FROM bff_session.bff_session
                WHERE session_ref = ?
                """)
                .param(newRef)
                .param(Timestamp.from(now))
                .param(Timestamp.from(now))
                .param(Timestamp.from(idleExpiresAt))
                .param(Timestamp.from(absoluteExpiresAt))
                .param(oldRef)
                .update();
    }

    private static BffSession mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BffSession(
                rs.getString("session_ref"),
                rs.getString("keycloak_subject"),
                rs.getString("keycloak_sid"),
                rs.getBytes("encrypted_token_material"),
                rs.getString("token_encryption_key_id"),
                rs.getString("acr"),
                rs.getTimestamp("authn_time").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_activity_at").toInstant(),
                rs.getTimestamp("idle_expires_at").toInstant(),
                rs.getTimestamp("absolute_expires_at").toInstant(),
                RevocationState.valueOf(rs.getString("revocation_state")),
                rs.getString("security_event_metadata"));
    }
}
