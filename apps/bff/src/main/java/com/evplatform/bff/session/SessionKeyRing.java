package com.evplatform.bff.session;

import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.InitializingBean;

/**
 * Versioned AES key ring for BFF session token-material encryption
 * (SEC-001 §5.3 / SEC-P09). Keys arrive as Base64-encoded 32-byte values
 * keyed by version id ({@code bff.session.encryption.keys.*}); the only
 * committed default is EMPTY — key material is never committed
 * (AGENTS.md §4; SEC-001 §5.3).
 *
 * <p>Fail-fast: {@link #afterPropertiesSet()} throws a clear
 * {@link IllegalStateException} when no key is configured, so a misconfigured
 * deployment (or a test that forgot the property) cannot silently run
 * without encryption. Tests satisfy this by setting the {@code v1} property
 * to a Base64-encoded 32-byte test key.</p>
 */
public final class SessionKeyRing implements InitializingBean {

    /** AES-256 key length in bytes. */
    static final int KEY_LENGTH_BYTES = 32;

    private final Map<String, byte[]> keys;

    public SessionKeyRing(Map<String, String> base64Keys) {
        this.keys = new java.util.LinkedHashMap<>();
        if (base64Keys != null) {
            base64Keys.forEach((keyId, base64) -> {
                if (base64 == null || base64.isBlank()) {
                    return; // empty env default — reported by fail-fast below
                }
                byte[] key = Base64.getDecoder().decode(base64);
                if (key.length != KEY_LENGTH_BYTES) {
                    throw new IllegalStateException(
                            "BFF session encryption key '" + keyId
                                    + "' must decode to exactly " + KEY_LENGTH_BYTES
                                    + " bytes (AES-256), got " + key.length);
                }
                this.keys.put(keyId, key.clone());
            });
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "No BFF session encryption keys configured (bff.session.encryption.keys.*). "
                            + "Set BFF_SESSION_KEY_V1 to a Base64-encoded 32-byte AES-256 key. "
                            + "Refusing to start without token-material encryption (SEC-001 §5.3).");
        }
    }

    /** Returns the raw key bytes for the given key id. */
    public byte[] keyFor(String keyId) {
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw new IllegalStateException(
                    "Unknown BFF session encryption key id: " + keyId);
        }
        return key.clone();
    }

    /** All configured key ids (e.g. {@code [v1]}). */
    public java.util.Set<String> keyIds() {
        return java.util.Collections.unmodifiableSet(keys.keySet());
    }

    /**
     * The key id used for new encryptions: the lexicographically greatest
     * configured id (v1 today; a future v2 supersedes it).
     */
    public String currentKeyId() {
        return keys.keySet().stream()
                .sorted()
                .reduce((a, b) -> b)
                .orElseThrow(() -> new IllegalStateException("Key ring is empty"));
    }
}
