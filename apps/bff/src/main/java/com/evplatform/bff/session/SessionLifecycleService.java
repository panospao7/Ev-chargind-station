package com.evplatform.bff.session;

import com.evplatform.bff.session.BffSession.RevocationState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the BFF session lifecycle (SEC-P01): creation from an
 * authenticated OAuth token response, validity evaluation (ACTIVE + idle +
 * absolute), activity touch, atomic reference rotation and revocation.
 *
 * <p>Token material is serialized to a small JSON document with Jackson and
 * encrypted by {@link TokenEncryptionService} before it ever reaches the
 * store; token values are NEVER logged (SEC-001 §5.3, ARC-SEC-21).</p>
 */
@Service
public class SessionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SessionLifecycleService.class);
    private static final int REF_RANDOM_BYTES = 32;

    private final JdbcSessionStore store;
    private final TokenEncryptionService encryption;
    private final Clock clock;
    private final SessionKeyRing keyRing;
    private final BffSessionProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public SessionLifecycleService(JdbcSessionStore store,
                                   TokenEncryptionService encryption,
                                   Clock clock,
                                   SessionKeyRing keyRing,
                                   BffSessionProperties properties) {
        this.store = store;
        this.encryption = encryption;
        this.clock = clock;
        this.keyRing = keyRing;
        this.properties = properties;
    }

    /** Decrypted OAuth token material held only in memory (never logged). */
    public record TokenMaterial(String accessToken, String refreshToken,
                                Instant expiresAt) {
    }

    /** Result of a validity check against the store row. */
    public sealed interface Loaded {
        record Valid(BffSession session) implements Loaded {
        }

        record Expired(BffSession session) implements Loaded {
        }

        record NotFound() implements Loaded {
        }
    }

    /**
     * Creates a session row for an authenticated principal: token JSON is
     * encrypted with the session reference as AAD and stored; the row starts
     * ACTIVE with fresh idle/absolute windows.
     */
    public BffSession createSession(String subject, String sid,
                                    TokenMaterial tokens, String acr,
                                    Instant now) {
        String sessionRef = newSessionRef();
        String tokenJson = serializeTokens(tokens);
        TokenEncryptionService.Encrypted encrypted =
                encryption.encrypt(tokenJson.getBytes(StandardCharsets.UTF_8), sessionRef);
        BffSession session = new BffSession(
                sessionRef,
                subject,
                sid,
                encrypted.ciphertext(),
                encrypted.keyId(),
                acr,
                now,
                now,
                now,
                now.plus(properties.session().idleTimeout()),
                now.plus(properties.session().absoluteTimeout()),
                RevocationState.ACTIVE,
                "{}");
        store.insert(session);
        log.info("BFF session created for subject (ref length={} chars)",
                sessionRef.length());
        return session;
    }

    /**
     * Loads a session and evaluates validity: ACTIVE and {@code now} strictly
     * before both expiry boundaries. Expired/revoked rows are returned as
     * {@link Loaded.Expired} (the caller distinguishes the SESSION_EXPIRED
     * problem code from AUTHENTICATION_REQUIRED).
     */
    public Loaded loadValidSession(String sessionRef, Instant now) {
        return store.findByRef(sessionRef)
                .map(session -> {
                    if (session.revocationState() != RevocationState.ACTIVE
                            || !now.isBefore(session.idleExpiresAt())
                            || !now.isBefore(session.absoluteExpiresAt())) {
                        return (Loaded) new Loaded.Expired(session);
                    }
                    return (Loaded) new Loaded.Valid(session);
                })
                .orElseGet(Loaded.NotFound::new);
    }

    /** Touches activity for an ACTIVE session (idle window re-derived from now). */
    public void touch(String sessionRef, Instant now) {
        store.touch(sessionRef, now);
    }

    /**
     * Atomically rotates the session reference (after authentication /
     * privilege change, SEC-P01 §5.1): old row REVOKED, new row ACTIVE in one
     * transaction. Returns the new reference.
     *
     * <p>The token material is re-encrypted under the NEW reference: the old
     * row is loaded, its token JSON decrypted, and the plaintext re-encrypted
     * with the new reference as Additional Authenticated Data before the
     * store's atomic {@code rotateRef} runs. The ciphertext is therefore
     * never copied between rows — a copied ciphertext would fail GCM
     * authentication against the new reference's AAD binding (the old
     * ciphertext is bound to the OLD reference). The decrypt failure path
     * (AEADBadTagException → {@code Optional.empty()}) aborts the rotation
     * with {@link IllegalStateException} BEFORE any store mutation, so the
     * old row stays intact and the caller keeps a valid session.</p>
     */
    public String rotate(String oldRef, Instant now) {
        String newRef = newSessionRef();
        BffSession old = store.findByRef(oldRef)
                .orElseThrow(() -> new IllegalStateException(
                        "Session rotation failed: old session not found"));
        java.util.Optional<TokenMaterial> tokens = decryptTokens(old);
        if (tokens.isEmpty()) {
            // Abort BEFORE the atomic rotateRef: the old row must stay
            // intact (still ACTIVE) when its material cannot be
            // authenticated — no revocation, no new row.
            throw new IllegalStateException(
                    "Session rotation failed: stored token material failed "
                            + "authentication against the old reference");
        }
        String tokenJson = serializeTokens(tokens.get());
        TokenEncryptionService.Encrypted reEncrypted =
                encryption.encrypt(tokenJson.getBytes(StandardCharsets.UTF_8), newRef);
        store.rotateRef(oldRef, newRef, now,
                reEncrypted.ciphertext(), reEncrypted.keyId());
        log.info("BFF session rotated (new ref length={} chars)", newRef.length());
        return newRef;
    }

    /** Revokes a single session. */
    public boolean revoke(String sessionRef) {
        return store.revoke(sessionRef);
    }

    /** Revokes all ACTIVE sessions of a subject+sid pair (back-channel logout). */
    public int revokeBySubjectAndSid(String subject, String sid) {
        return store.revokeBySubjectAndSid(subject, sid);
    }

    /** Back-channel mapping: finds the ACTIVE session for subject+sid. */
    public java.util.Optional<BffSession> findActiveBySubjectAndSid(String subject, String sid) {
        return store.findActiveBySubjectAndSid(subject, sid);
    }

    /** Reads the raw security-event metadata JSON for a session row. */
    public String storeMetadata(String sessionRef) {
        return store.findByRef(sessionRef)
                .map(BffSession::securityEventMetadata)
                .orElse("{}");
    }

    /** Writes the security-event metadata JSON for a session row. */
    public void storeMetadataUpdate(String sessionRef, String metadataJson) {
        store.updateSecurityEventMetadata(sessionRef, metadataJson);
    }

    /**
     * Decrypts the stored token material for a session. Tamper/wrong-key
     * failures ({@link javax.crypto.AEADBadTagException}) are mapped to an
     * empty result — the caller treats the session as invalid rather than
     * surfacing crypto errors.
     */
    public java.util.Optional<TokenMaterial> decryptTokens(BffSession session) {
        try {
            byte[] plain = encryption.decrypt(
                    session.encryptedTokenMaterial(),
                    session.tokenEncryptionKeyId(),
                    session.sessionRef());
            return java.util.Optional.of(deserializeTokens(
                    new String(plain, StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            log.warn("Session token material failed authentication (session treated as invalid)");
            return java.util.Optional.empty();
        }
    }

    /** Generates a new opaque session reference: 32 random bytes, base64url (≥32 chars). */
    public String newSessionRef() {
        byte[] bytes = new byte[REF_RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String serializeTokens(TokenMaterial tokens) {
        try {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("accessToken", tokens.accessToken());
            doc.put("refreshToken", tokens.refreshToken());
            doc.put("expiresAt", tokens.expiresAt() == null
                    ? null : tokens.expiresAt().toString());
            return objectMapper.writeValueAsString(doc);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Token material serialization failed", e);
        }
    }

    private TokenMaterial deserializeTokens(String json) {
        try {
            var node = objectMapper.readTree(json);
            return new TokenMaterial(
                    textOrNull(node, "accessToken"),
                    textOrNull(node, "refreshToken"),
                    node.hasNonNull("expiresAt")
                            ? Instant.parse(node.get("expiresAt").asText())
                            : null);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Token material deserialization failed", e);
        }
    }

    private static String textOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
