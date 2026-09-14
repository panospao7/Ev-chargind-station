package com.evplatform.bff.security;

import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.JdbcSessionStore;
import com.evplatform.bff.session.TokenEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.stereotype.Component;

/**
 * Store-backed {@link AuthorizationRequestRepository} (I1-IAM-001 closeout
 * M-3, SEC-001 §5.1/§5.3): the OAuth2 authorization request for the
 * Authorization Code + PKCE login is persisted as an ENCRYPTED pre-auth row
 * in {@code bff_session_db} (keyed by the {@code state} parameter) instead
 * of the servlet HTTP session, because the BFF runs with
 * {@code SessionCreationPolicy.NEVER} and must not create a JSESSIONID.
 *
 * <p>Storage model (reuses the V2 session table — no schema change):
 * {@code session_ref = state} (the state is the high-entropy Spring
 * Security value), {@code keycloak_subject = '__pre_auth__'} sentinel (the
 * security-context repository's {@code findByRef} excludes it, so a pre-auth
 * row can never authenticate), {@code keycloak_sid = NULL}, and the
 * serialized authorization-request JSON as AES-GCM ciphertext with the
 * {@code state} as Additional Authenticated Data — the same AAD binding the
 * session token material uses, so a ciphertext cannot be moved between
 * state values.</p>
 *
 * <p>The row expires 10 minutes after creation (authorization requests are
 * short-lived); an expired row is treated as absent and lazily deleted.
 * {@link #removeAuthorizationRequest(HttpServletRequest, HttpServletResponse)}
 * makes the authorization request single-use: the callback leg deletes the
 * row after loading it.</p>
 *
 * <p>Privacy/logging: the serialized document contains OAuth protocol
 * parameters only (no account, driver, or vehicle identifiers — AGENTS.md
 * §4 Discovery-projection rule is respected here as a matter of hygiene);
 * nothing is logged except outcomes.</p>
 */
@Component
public class StoreBackedAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    /** Sentinel subject marking a pre-auth (authorization-request) row. */
    public static final String PRE_AUTH_SUBJECT = "__pre_auth__";

    /** Lifetime of a stored authorization request (task packet: 10 minutes). */
    private static final Duration PRE_AUTH_TTL = Duration.ofMinutes(10);

    private static final Logger log =
            LoggerFactory.getLogger(StoreBackedAuthorizationRequestRepository.class);

    private final JdbcSessionStore store;
    private final TokenEncryptionService encryption;
    private final Clock clock;
    // Same pattern as SessionLifecycleService/BffSessionCsrfTokenRepository:
    // Boot 4.1's starter-web no longer exposes a Jackson ObjectMapper bean,
    // so the component constructs its own mapper.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StoreBackedAuthorizationRequestRepository(JdbcSessionStore store,
                                                     TokenEncryptionService encryption,
                                                     Clock clock) {
        this.store = store;
        this.encryption = encryption;
        this.clock = clock;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            return; // matches HttpSessionOAuth2AuthorizationRequestRepository semantics
        }
        String state = authorizationRequest.getState();
        if (state == null || state.isBlank()) {
            throw new IllegalStateException(
                    "Cannot persist an authorization request without a state parameter");
        }
        Instant now = Instant.now(clock);
        String json = serialize(authorizationRequest);
        TokenEncryptionService.Encrypted encrypted =
                encryption.encrypt(json.getBytes(StandardCharsets.UTF_8), state);
        BffSession row = new BffSession(
                state,
                PRE_AUTH_SUBJECT,
                null,
                encrypted.ciphertext(),
                encrypted.keyId(),
                null,
                now,
                now,
                now,
                now.plus(PRE_AUTH_TTL),
                now.plus(PRE_AUTH_TTL),
                BffSession.RevocationState.ACTIVE,
                "{}");
        store.insert(row);
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (state == null || state.isBlank()) {
            return null;
        }
        Optional<BffSession> row = store.findPreAuthByRef(state);
        if (row.isEmpty()) {
            return null;
        }
        BffSession preAuth = row.get();
        Instant now = Instant.now(clock);
        if (!now.isBefore(preAuth.absoluteExpiresAt())) {
            // Expired: treat as absent and lazily delete the single-use row.
            store.deletePreAuthByRef(state);
            log.info("Authorization request expired (state removed)");
            return null;
        }
        try {
            byte[] plain = encryption.decrypt(
                    preAuth.encryptedTokenMaterial(),
                    preAuth.tokenEncryptionKeyId(),
                    state);
            return deserialize(new String(plain, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            // Tampered/wrong-key/wrong-AAD material: treat as absent. The
            // row is left in place (single-use delete happens on remove);
            // it expires within the TTL window.
            log.info("Authorization request material failed authentication (treated as absent)");
            return null;
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        String state = request.getParameter("state");
        if (state == null || state.isBlank()) {
            return null;
        }
        OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
        store.deletePreAuthByRef(state);
        return loaded;
    }

    // ------------------------------------------------------------------
    // Serialization: authorization request → compact JSON document
    // ------------------------------------------------------------------

    /**
     * Serializes the authorization request to JSON. {@code
     * authorizationRequestUri} is included VERBATIM (it is the fully
     * encoded redirect target the browser must be sent to, including
     * PKCE/nonce query parameters) so the reconstructed request redirects
     * byte-for-byte identically after a round-trip.
     */
    private String serialize(OAuth2AuthorizationRequest authReq) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("authorizationRequestUri", authReq.getAuthorizationRequestUri());
        node.put("authorizationUri", authReq.getAuthorizationUri());
        node.put("clientId", authReq.getClientId());
        node.put("redirectUri", authReq.getRedirectUri());
        ArrayNode scopes = node.putArray("scopes");
        authReq.getScopes().forEach(scopes::add);
        node.put("state", authReq.getState());
        node.put("grantType", authReq.getGrantType() == null
                ? null : authReq.getGrantType().getValue());
        node.put("responseTypeId", authReq.getResponseType() == null
                ? null : authReq.getResponseType().getValue());
        ObjectNode additional = node.putObject("additionalParameters");
        if (authReq.getAdditionalParameters() != null) {
            authReq.getAdditionalParameters().forEach((key, value) -> {
                if (value instanceof CharSequence cs) {
                    additional.put(key, cs.toString());
                } else if (value != null) {
                    additional.put(key, String.valueOf(value));
                }
            });
        }
        ObjectNode attributes = node.putObject("attributes");
        if (authReq.getAttributes() != null) {
            authReq.getAttributes().forEach((key, value) -> {
                if (value instanceof CharSequence cs) {
                    attributes.put(key, cs.toString());
                } else if (value != null) {
                    attributes.put(key, String.valueOf(value));
                }
            });
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(
                    "Authorization request serialization failed", e);
        }
    }

    /**
     * Rebuilds the authorization request via the {@code authorizationCode()}
     * builder, setting every persisted field including the verbatim
     * {@code authorizationRequestUri}.
     */
    private OAuth2AuthorizationRequest deserialize(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            var builder = OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(textOrNull(node, "authorizationUri"))
                    .clientId(textOrNull(node, "clientId"))
                    .redirectUri(textOrNull(node, "redirectUri"))
                    .state(textOrNull(node, "state"));
            JsonNode scopes = node.get("scopes");
            if (scopes != null && scopes.isArray()) {
                Set<String> scopeSet = new LinkedHashSet<>();
                scopes.forEach(s -> scopeSet.add(s.asText()));
                builder.scopes(scopeSet);
            }
            String grantType = textOrNull(node, "grantType");
            if (grantType != null
                    && !"authorization_code".equals(grantType)) {
                throw new IllegalStateException(
                        "Unsupported persisted grant type: " + grantType);
            }
            builder.authorizationRequestUri(textOrNull(node, "authorizationRequestUri"));
            String responseType = textOrNull(node, "responseTypeId");
            // The authorization-code builder fixes the response type to
            // "code"; the persisted value is asserted equal by the tests.
            if (responseType != null
                    && !OAuth2AuthorizationResponseType.CODE.getValue().equals(responseType)) {
                throw new IllegalStateException(
                        "Unsupported persisted response type: " + responseType);
            }
            JsonNode additional = node.get("additionalParameters");
            if (additional != null && additional.isObject()) {
                Map<String, Object> map = new LinkedHashMap<>();
                additional.fieldNames().forEachRemaining(
                        name -> map.put(name, additional.get(name).asText()));
                builder.additionalParameters(map);
            }
            JsonNode attributes = node.get("attributes");
            if (attributes != null && attributes.isObject()) {
                Map<String, Object> map = new LinkedHashMap<>();
                attributes.fieldNames().forEachRemaining(
                        name -> map.put(name, attributes.get(name).asText()));
                builder.attributes(map);
            }
            return builder.build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(
                    "Authorization request deserialization failed", e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
