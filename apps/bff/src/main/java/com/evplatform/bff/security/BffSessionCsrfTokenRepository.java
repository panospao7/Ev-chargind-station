package com.evplatform.bff.security;

import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.SessionLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Session-bound synchronizer CSRF token repository (SEC-001 §6.1, SEC-P02
 * §6.1): the token lives in the {@code security_event_metadata} jsonb of the
 * BFF session row, so it rotates with the session and is never stored in the
 * browser. Header {@code X-CSRF-TOKEN}, parameter {@code _csrf} (SEC-P02
 * §6.1/§6.3).
 */
@Component
public class BffSessionCsrfTokenRepository implements CsrfTokenRepository {

    /** CSRF header name (SEC-P02 §6.1/§6.3). */
    public static final String CSRF_HEADER = "X-CSRF-TOKEN";
    /** CSRF parameter name. */
    public static final String CSRF_PARAMETER = "_csrf";
    /** Metadata document key carrying the token. */
    public static final String METADATA_KEY = "csrfToken";

    private static final int TOKEN_BYTES = 32;

    private final SessionLifecycleService lifecycle;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public BffSessionCsrfTokenRepository(SessionLifecycleService lifecycle, Clock clock) {
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new BffCsrfToken(token);
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request,
                          HttpServletResponse response) {
        Optional<String> ref = BffSessionSecurityContextRepository.sessionRef(request);
        if (ref.isEmpty()) {
            return; // no session → nothing to bind the token to
        }
        if (token == null) {
            // Null token means "remove" — clear the metadata key.
            updateMetadata(ref.get(), metadata -> {
                metadata.remove(METADATA_KEY);
            });
            return;
        }
        updateMetadata(ref.get(), metadata -> {
            metadata.put(METADATA_KEY, token.getToken());
        });
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        Optional<String> ref = BffSessionSecurityContextRepository.sessionRef(request);
        if (ref.isEmpty()) {
            return null;
        }
        SessionLifecycleService.Loaded loaded =
                lifecycle.loadValidSession(ref.get(), Instant.now(clock));
        if (loaded instanceof SessionLifecycleService.Loaded.Valid valid) {
            String token = csrfTokenFromMetadata(valid.session());
            return token == null ? null : new BffCsrfToken(token);
        }
        return null;
    }

    /** Reads the stored CSRF token from a session row's metadata document. */
    public String csrfTokenFromMetadata(BffSession session) {
        String metadata = session.securityEventMetadata();
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            return node.hasNonNull(METADATA_KEY) ? node.get(METADATA_KEY).asText() : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    private void updateMetadata(String sessionRef,
                                java.util.function.Consumer<Map<String, Object>> mutator) {
        String current = lifecycle.storeMetadata(sessionRef);
        Map<String, Object> doc = new LinkedHashMap<>();
        if (current != null && !current.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(current);
                node.fields().forEachRemaining(e -> doc.put(e.getKey(), e.getValue()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // start from an empty document
            }
        }
        mutator.accept(doc);
        try {
            lifecycle.storeMetadataUpdate(sessionRef,
                    objectMapper.writeValueAsString(doc));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("CSRF metadata serialization failed", e);
        }
    }

    /** CsrfToken implementation over the synchronizer value. */
    public record BffCsrfToken(String tokenValue) implements CsrfToken {

        @Override
        public String getHeaderName() {
            return CSRF_HEADER;
        }

        @Override
        public String getParameterName() {
            return CSRF_PARAMETER;
        }

        @Override
        public String getToken() {
            return tokenValue;
        }
    }
}
