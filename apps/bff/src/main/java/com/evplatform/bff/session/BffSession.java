package com.evplatform.bff.session;

import java.time.Instant;

/**
 * Immutable model of one {@code bff_session.bff_session} row (SEC-001 §5.3,
 * SEC-P01). A plain value object — no Java serialization anywhere (SEC-001
 * §5.3: arbitrary Java serialization of sessions is prohibited); this class
 * is carried between the store, the lifecycle service and the security
 * context repository only.
 *
 * @param sessionRef              opaque high-entropy session reference (PK;
 *                                ≥ 32 chars per the V2 CHECK constraint)
 * @param keycloakSubject         Keycloak subject identifier (IdP-owned
 *                                identity string; no Account Service binding
 *                                in this slice)
 * @param keycloakSid             Keycloak session id (back-channel logout
 *                                mapping; may be null when the IdP did not
 *                                provide one)
 * @param encryptedTokenMaterial  AES-GCM ciphertext (12-byte IV prepended);
 *                                plaintext is the OAuth token JSON
 * @param tokenEncryptionKeyId    key-ring id used for the ciphertext
 * @param acr                     authentication context class reference
 * @param authnTime               authentication time
 * @param createdAt               row creation time
 * @param lastActivityAt          last activity time
 * @param idleExpiresAt           idle expiry boundary
 * @param absoluteExpiresAt       absolute expiry boundary
 * @param revocationState         ACTIVE or REVOKED
 * @param securityEventMetadata   security-event metadata JSON document
 *                                (SEC-001 §5.3; also carries the
 *                                session-bound CSRF synchronizer token,
 *                                SEC-P02 §6.1)
 */
public record BffSession(
        String sessionRef,
        String keycloakSubject,
        String keycloakSid,
        byte[] encryptedTokenMaterial,
        String tokenEncryptionKeyId,
        String acr,
        Instant authnTime,
        Instant createdAt,
        Instant lastActivityAt,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt,
        RevocationState revocationState,
        String securityEventMetadata) {

    public enum RevocationState {
        ACTIVE,
        REVOKED
    }
}
