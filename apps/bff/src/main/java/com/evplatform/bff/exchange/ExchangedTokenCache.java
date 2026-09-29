package com.evplatform.bff.exchange;

import com.evplatform.bff.session.JdbcSessionStore;
import com.evplatform.bff.session.TokenEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

/**
 * Session-scoped cache of exchanged downstream tokens (SEC-001 §7.2,
 * packet AC-03).
 *
 * Storage rides the existing {@code bff_session.security_event_metadata}
 * jsonb document under the {@code exchanged_tokens} key. Each entry's
 * ciphertext is the downstream access token encrypted with the session key
 * ring using the session reference as AAD — the cache is therefore bound to
 * its session row (another session cannot decrypt it), is encrypted at rest,
 * and dies with the row (revocation/expiry of the session removes the only
 * copy; a revoked session never reaches this cache again because the
 * security context rejects it before any downstream call).
 *
 * Refresh policy: an entry is used only while
 * {@code now < expiresAt - refreshMargin}; shortly-before-expiry entries are
 * treated as absent so the caller re-exchanges (§7.2 "only until shortly
 * before expiry").
 */
@Component
public class ExchangedTokenCache {

    /** Metadata document key carrying the cache map. */
    public static final String METADATA_KEY = "exchanged_tokens";

    /** Default refresh margin: entries expiring within 30s are stale. */
    public static final java.time.Duration DEFAULT_REFRESH_MARGIN = java.time.Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcSessionStore store;
    private final TokenEncryptionService encryption;
    private final java.time.Duration refreshMargin;

    public ExchangedTokenCache(JdbcSessionStore store,
                               TokenEncryptionService encryption,
                               @org.springframework.beans.factory.annotation.Value(
                                       "${bff.exchange.refresh-margin-seconds:30}") long refreshMarginSeconds) {
        this.store = store;
        this.encryption = encryption;
        this.refreshMargin = java.time.Duration.ofSeconds(refreshMarginSeconds);
    }

    /** One cache entry after decryption. */
    public record CachedToken(String accessToken, Instant expiresAt) {
    }

    /**
     * @return the cached token for {@code targetAudience} when present and
     *         not within the refresh margin; {@code null} otherwise.
     */
    public CachedToken get(String sessionRef, String targetAudience, Instant now) {
        String metadataJson = store.findSecurityEventMetadata(sessionRef);
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(metadataJson);
            JsonNode entry = root.path(METADATA_KEY).path(audienceKey(targetAudience));
            if (!entry.isObject()) {
                return null;
            }
            Instant expiresAt = Instant.parse(entry.path("expiresAt").asText());
            if (!now.plus(refreshMargin).isBefore(expiresAt)) {
                return null; // expiring shortly: treat as absent (§7.2)
            }
            byte[] plaintext = encryption.decrypt(
                    Base64.getDecoder().decode(entry.path("ciphertext").asText()),
                    entry.path("keyId").asText(),
                    sessionRef);
            return new CachedToken(new String(plaintext, StandardCharsets.UTF_8), expiresAt);
        } catch (Exception e) {
            // undecryptable/tampered entries are cache misses, never errors —
            // the caller re-exchanges (fail closed to fresh token acquisition)
            return null;
        }
    }

    /**
     * Encrypts and stores the exchanged token for {@code targetAudience} in
     * the session's metadata document (read-modify-write; caller is inside
     * the session's request flow so concurrent same-session mutation follows
     * the existing single-writer session semantics).
     */
    public void put(String sessionRef, String targetAudience,
                    String accessToken, Instant expiresAt, Instant now) {
        if (!now.isBefore(expiresAt)) {
            throw new IllegalArgumentException("refusing to cache an already-expired token");
        }
        TokenEncryptionService.Encrypted enc = encryption.encrypt(
                accessToken.getBytes(StandardCharsets.UTF_8), sessionRef);
        String metadataJson = store.findSecurityEventMetadata(sessionRef);
        try {
            ObjectNode root = metadataJson == null || metadataJson.isBlank()
                    ? MAPPER.createObjectNode()
                    : (ObjectNode) MAPPER.readTree(metadataJson);
            ObjectNode cache = root.withObjectProperty(METADATA_KEY);
            ObjectNode entry = cache.putObject(audienceKey(targetAudience));
            entry.put("ciphertext", Base64.getEncoder().encodeToString(
                    enc.ciphertext()));
            entry.put("keyId", enc.keyId());
            entry.put("expiresAt", expiresAt.toString());
            entry.put("cachedAt", now.toString());
            store.updateSecurityEventMetadata(sessionRef, MAPPER.writeValueAsString(root));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cache write failed", e);
        }
    }

    /**
     * Removes every cached exchanged token for the session (logout/
     * revocation support); metadata keeps its other entries.
     */
    public void evictAll(String sessionRef) {
        String metadataJson = store.findSecurityEventMetadata(sessionRef);
        if (metadataJson == null || metadataJson.isBlank()) {
            return;
        }
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(metadataJson);
            if (root.has(METADATA_KEY)) {
                root.remove(METADATA_KEY);
                store.updateSecurityEventMetadata(sessionRef,
                        MAPPER.writeValueAsString(root));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cache eviction failed", e);
        }
    }

    /** JSON-object-key form of an audience (dots are legal in jsonb keys). */
    private static String audienceKey(String targetAudience) {
        return targetAudience;
    }
}
