package com.evplatform.bff.exchange;

import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.BffSessionProperties;
import com.evplatform.bff.session.SessionLifecycleService;
import com.evplatform.bff.session.TokenEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Per-session, per-audience cache for exchanged downstream access tokens
 * (I1-IAM-002, SEC-P03 §7.3): an exchanged token is stored ENCRYPTED in the
 * session row's {@code security_event_metadata} jsonb under the
 * {@code exchangedTokens} key so a server restart does not force a new
 * exchange per downstream call within the session lifetime.
 *
 * <p>Structure of the metadata fragment (one audience entry per session):
 * {@code {"exchangedTokens":{"<aud>":{"ciphertext":"<base64>","keyId":"v1",
 * "expiresAt":"<ISO-8601>"}}}}. The ciphertext is bound to
 * {@code sessionRef + ":" + aud} as AES-GCM Additional Authenticated Data —
 * a ciphertext copied between sessions OR between audiences fails GCM
 * authentication ({@link javax.crypto.AEADBadTagException}) and the entry is
 * treated as absent.</p>
 *
 * <p>Concurrency: the cache entry is written through
 * {@link SessionLifecycleService#mergeMetadata} — a single-statement jsonb
 * merge — so a concurrent CSRF write to a DISJOINT metadata key cannot be
 * lost. A last-writer-wins on the SAME audience key is benign: both writers
 * exchange against the same IdP and hold equally valid tokens.</p>
 *
 * <p>Token material (subject token, exchanged token, ciphertext) is NEVER
 * logged (SEC-001 §5.3, ARC-SEC-21): log lines carry only the audience and
 * the outcome class.</p>
 */
@Component
public class ExchangedTokenCache {

    /** Metadata document key carrying the per-audience exchanged-token entries. */
    public static final String METADATA_KEY = "exchangedTokens";

    /** Default expiry skew: a cached token within this window of expiry is re-exchanged. */
    private static final Duration DEFAULT_EXPIRY_SKEW = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(ExchangedTokenCache.class);

    private final SessionLifecycleService lifecycle;
    private final TokenEncryptionService encryption;
    private final TokenExchangeClient exchangeClient;
    private final Clock clock;
    private final Duration expirySkew;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExchangedTokenCache(SessionLifecycleService lifecycle,
                               TokenEncryptionService encryption,
                               TokenExchangeClient exchangeClient,
                               Clock clock,
                               BffSessionProperties properties) {
        this.lifecycle = lifecycle;
        this.encryption = encryption;
        this.exchangeClient = exchangeClient;
        this.clock = clock;
        this.expirySkew = properties != null && properties.exchange() != null
                && properties.exchange().cacheExpirySkew() != null
                ? properties.exchange().cacheExpirySkew()
                : DEFAULT_EXPIRY_SKEW;
    }

    /** Successful cache/exchange outcome: the downstream bearer token. */
    public record Cached(String accessToken) implements Result {
    }

    /** Failed exchange outcome: OAuth error code/description or transport class. */
    public record ExchangeFailure(String error, String errorDescription)
            implements Result {
    }

    /** Typed get-or-exchange outcome. */
    public sealed interface Result permits Cached, ExchangeFailure {
    }

    /**
     * Returns a valid cached exchanged token for {@code aud}, or performs a
     * fresh RFC 8693 exchange using the session's decrypted subject access
     * token and caches the result encrypted under {@code sessionRef + ":" + aud}.
     *
     * <ul>
     *   <li>session revoked/expired/not found → {@link ExchangeFailure}
     *       with {@code session_invalid} (no exchange is attempted with
     *       invalid material; callers map this to 401 at the BFF edge)</li>
     *   <li>cache hit ({@code now < expiresAt - skew}) → cached token</li>
     *   <li>cache miss/stale/corrupt → exchange; on success the encrypted
     *       entry is merged into metadata and the token returned</li>
     *   <li>exchange failure → {@link ExchangeFailure} (typed, no exception
     *       carrying token material)</li>
     * </ul>
     */
    public Result getOrExchange(String sessionRef, String aud) {
        Instant now = Instant.now(clock);
        SessionLifecycleService.Loaded loaded =
                lifecycle.loadValidSession(sessionRef, now);
        if (!(loaded instanceof SessionLifecycleService.Loaded.Valid valid)) {
            return new ExchangeFailure("session_invalid",
                    "The BFF session is not valid.");
        }
        Optional<String> cached = readCachedToken(valid.session(), aud, now);
        if (cached.isPresent()) {
            return new Cached(cached.get());
        }
        Optional<SessionLifecycleService.TokenMaterial> material =
                lifecycle.decryptTokens(valid.session());
        if (material.isEmpty() || isBlank(material.get().accessToken())) {
            log.info("Exchanged-token cache could not obtain subject access token for audience");
            return new ExchangeFailure("session_invalid",
                    "The session token material could not be used.");
        }
        TokenExchangeClient.ExchangeResult result =
                exchangeClient.exchange(material.get().accessToken(), aud);
        if (result instanceof TokenExchangeClient.Exchanged exchanged) {
            return storeExchanged(valid.session(), aud, exchanged);
        }
        TokenExchangeClient.Failure failure = (TokenExchangeClient.Failure) result;
        return new ExchangeFailure(failure.error(), failure.errorDescription());
    }

    /**
     * Removes the cached entry for {@code aud} by merging a JSON-null value
     * for the audience key (jsonb merge replaces the previous entry with
     * JSON null; readers treat non-object entries as absent). Used by
     * {@link DownstreamAuthClient} when a downstream service rejects the
     * cached bearer token.
     */
    public void invalidate(String sessionRef, String aud) {
        Map<String, Object> audFragment = new LinkedHashMap<>();
        audFragment.put(aud, null);
        Map<String, Object> fragment = new LinkedHashMap<>();
        fragment.put(METADATA_KEY, audFragment);
        String json;
        try {
            json = objectMapper.writeValueAsString(fragment);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Exchanged-token invalidation serialization failed", e);
        }
        lifecycle.mergeMetadata(sessionRef, json);
        log.info("Exchanged-token cache entry invalidated for audience");
    }

    /**
     * Reads the cached entry for {@code aud} and returns its decrypted
     * access token when it exists and is not within the expiry-skew window.
     * Any structural or cryptographic problem (missing key, JSON null,
     * wrong shape, AEAD tag failure, unknown key id) is treated as a cache
     * miss — never as an error surfaced to the caller.
     */
    private Optional<String> readCachedToken(BffSession session, String aud, Instant now) {
        String metadata = session.securityEventMetadata();
        if (metadata == null || metadata.isBlank()) {
            return Optional.empty();
        }
        JsonNode entry;
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode audNode = root.path(METADATA_KEY).path(aud);
            entry = audNode.isObject() ? audNode : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Optional.empty();
        }
        if (entry == null) {
            return Optional.empty();
        }
        JsonNode expiresAtNode = entry.path("expiresAt");
        if (!expiresAtNode.isTextual()) {
            return Optional.empty();
        }
        Instant expiresAt;
        try {
            expiresAt = Instant.parse(expiresAtNode.asText());
        } catch (java.time.format.DateTimeParseException e) {
            return Optional.empty();
        }
        if (!now.isBefore(expiresAt.minus(expirySkew))) {
            return Optional.empty();
        }
        JsonNode ciphertextNode = entry.path("ciphertext");
        JsonNode keyIdNode = entry.path("keyId");
        if (!ciphertextNode.isTextual() || !keyIdNode.isTextual()) {
            return Optional.empty();
        }
        byte[] plaintext;
        try {
            byte[] ciphertext = java.util.Base64.getDecoder()
                    .decode(ciphertextNode.asText());
            plaintext = encryption.decryptWithAad(ciphertext, keyIdNode.asText(),
                    aadBinding(session.sessionRef(), aud));
        } catch (IllegalArgumentException | IllegalStateException
                | java.security.GeneralSecurityException e) {
            // Corrupt/stale/mis-bound entry: treat as a cache miss and let
            // the caller re-exchange. No token material in the message.
            log.info("Exchanged-token cache entry unreadable (outcome=cache_miss)");
            return Optional.empty();
        }
        return Optional.of(new String(plaintext, StandardCharsets.UTF_8));
    }

    /**
     * Encrypts the exchanged token with {@code sessionRef + ":" + aud} as
     * AAD and merges the entry into the session metadata; returns the
     * plaintext token on success.
     */
    private Result storeExchanged(BffSession session, String aud,
                                  TokenExchangeClient.Exchanged exchanged) {
        long expiresIn = Math.max(0, exchanged.expiresIn());
        Instant expiresAt = Instant.now(clock).plusSeconds(expiresIn);
        byte[] plaintext = exchanged.accessToken().getBytes(StandardCharsets.UTF_8);
        TokenEncryptionService.Encrypted encrypted =
                encryption.encryptWithAad(plaintext, aadBinding(session.sessionRef(), aud));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ciphertext", java.util.Base64.getEncoder().encodeToString(encrypted.ciphertext()));
        entry.put("keyId", encrypted.keyId());
        entry.put("expiresAt", expiresAt.toString());
        Map<String, Object> audFragment = new LinkedHashMap<>();
        audFragment.put(aud, entry);
        Map<String, Object> fragment = new LinkedHashMap<>();
        fragment.put(METADATA_KEY, audFragment);
        String json;
        try {
            json = objectMapper.writeValueAsString(fragment);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Exchanged-token cache serialization failed", e);
        }
        lifecycle.mergeMetadata(session.sessionRef(), json);
        return new Cached(exchanged.accessToken());
    }

    private static String aadBinding(String sessionRef, String aud) {
        return sessionRef + ":" + aud;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
