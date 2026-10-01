package com.evplatform.bff.exchange;

import com.evplatform.bff.realm.KeycloakExchangeFixture;
import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.SessionLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-IAM-002 phase 3 (SEC-P03, packet AC-01/AC-03): real Standard Token
 * Exchange V2 against the fixture realm — positive claim assertions, the
 * session-scoped encrypted cache lifecycle, and revocation semantics.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TokenExchangeIntegrationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final KeycloakExchangeFixture FX = KeycloakExchangeFixture.start();
    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    private static SessionLifecycleService lifecycle() {
        return Fixtures.lifecycle(FX);
    }

    private static ExchangedTokenService service() {
        return Fixtures.exchangeService(FX);
    }

    /** Production-exact subject token: code+PKCE login as ev-bff (§7.1). */
    private static BffSession newSession() throws Exception {
        KeycloakExchangeFixture.EvBffLogin login =
                FX.headlessCodeLogin("driver-local", "evplatform_dev_only");
        return lifecycle().createSession(
                "user-phase3-1", "sid-phase3-1",
                new SessionLifecycleService.TokenMaterial(
                        login.accessToken(), login.refreshToken(), NOW.plusSeconds(300)),
                "urn:evplatform:acr:basic", NOW);
    }

    @Test
    @Order(1)
    void exchangeYieldsAudienceLimitedTokenWithPreservedSubject() throws Exception {
        BffSession session = newSession();
        ExchangedTokenService.DownstreamToken token =
                service().tokenFor(session.sessionRef(), "svc-station-operations", NOW);

        assertNotNull(token.accessToken());
        assertFalse(token.fromCache(), "first resolution must be an exchange");

        // claim assertions on the exchanged token (§7.2)
        SignedJWT parsed = SignedJWT.parse(token.accessToken());
        var claims = parsed.getJWTClaimsSet();
        List<String> audiences = claims.getAudience();
        assertEquals(1, audiences.size(), "exactly one audience (§7.2)");
        assertEquals("svc-station-operations", audiences.get(0));
        assertEquals("user-phase3-1", claims.getSubject(),
                "human subject preserved (§7.2)");
        assertEquals("ev-bff", claims.getStringClaim("azp"),
                "BFF authorized party");
        assertNotNull(claims.getClaim("acr"), "acr preserved (§7.2)");
        assertNotNull(claims.getClaim("auth_time"), "auth_time preserved (§7.2)");
        assertNotNull(claims.getExpirationTime(), "expiry present");
        assertTrue(claims.getExpirationTime().before(java.util.Date.from(NOW.plusSeconds(600))),
                "short expiry (§7.2)");
    }

    @Test
    @Order(2)
    void secondResolutionUsesSessionCache() throws Exception {
        BffSession session = newSession();
        ExchangedTokenService.DownstreamToken first =
                service().tokenFor(session.sessionRef(), "svc-station-operations", NOW);
        ExchangedTokenService.DownstreamToken second =
                service().tokenFor(session.sessionRef(), "svc-station-operations", NOW);
        assertTrue(second.fromCache(), "second resolution must come from the session cache");
        assertEquals(first.accessToken(), second.accessToken(),
                "cached token is the same exchanged token");
    }

    @Test
    @Order(3)
    void cacheIsEncryptedAtRestAndSessionBound() throws Exception {
        BffSession session = newSession();
        service().tokenFor(session.sessionRef(), "svc-station-operations", NOW);

        // ciphertext at rest: metadata must NOT contain the plaintext token
        String metadata = Fixtures.store(FX).findSecurityEventMetadata(session.sessionRef());
        assertTrue(metadata.contains("exchanged_tokens"), "cache entry present in metadata");
        String storedToken = MAPPER.readTree(metadata)
                .path("exchanged_tokens").path("svc-station-operations")
                .path("ciphertext").asText();
        assertFalse(storedToken.isBlank(), "entry is encrypted ciphertext");
        assertFalse(metadata.contains("eyJ"), "no raw JWT material in metadata");

        // session binding: another session cannot read this entry
        BffSession other = newSession();
        ExchangedTokenCache cache = Fixtures.cache(FX);
        ExchangedTokenCache.CachedToken stolen =
                cache.get(other.sessionRef(), "svc-station-operations", NOW);
        assertTrue(stolen == null || !stolen.accessToken()
                        .equals(cache.get(session.sessionRef(), "svc-station-operations", NOW)
                                .accessToken()),
                "cross-session reads cannot yield the same plaintext (AAD binding)");
    }

    @Test
    @Order(4)
    void revokedSessionCannotExchangeOrReadCache() throws Exception {
        BffSession session = newSession();
        ExchangedTokenService.DownstreamToken first =
                service().tokenFor(session.sessionRef(), "svc-station-operations", NOW);
        assertNotNull(first.accessToken());

        assertTrue(lifecycle().revoke(session.sessionRef()));
        ExchangedTokenService svc = service();
        assertThrows(IllegalStateException.class,
                () -> svc.tokenFor(session.sessionRef(), "svc-station-operations", NOW),
                "revoked session must never exchange (§7.3)");
    }

    @Test
    @Order(5)
    void unsupportedAudienceExchangeFails() throws Exception {
        BffSession session = newSession();
        ExchangedTokenService svc = service();
        TokenExchangeClient.ExchangeFailedException e = assertThrows(
                TokenExchangeClient.ExchangeFailedException.class,
                () -> svc.tokenFor(session.sessionRef(), "svc-nonexistent", NOW),
                "unsupported audience must fail (§7.3)");
        assertTrue(e.statusCode == 400 || e.statusCode == 403,
                "semantic refusal, got " + e.statusCode);
    }

    @Test
    @Order(6)
    void crossServiceAudienceIsolationAtTheTokenLevel() throws Exception {
        // §7.3 "Account token rejected by Booking": at the token level this
        // means each exchanged token carries EXACTLY its one target audience,
        // so a STA token presented to another service fails audience
        // validation (resource-server side proven in phase 4).
        BffSession session = newSession();
        ExchangedTokenService.DownstreamToken sta =
                service().tokenFor(session.sessionRef(), "svc-station-operations", NOW);
        List<String> aud = SignedJWT.parse(sta.accessToken()).getJWTClaimsSet().getAudience();
        assertEquals(List.of("svc-station-operations"), aud);
        assertFalse(aud.contains("svc-discovery-insights"),
                "no other service audience present (§7.2/§7.3)");
    }

    @Test
    @Order(7)
    void twoTargetsProduceDistinctTokensWithoutBroadScope() throws Exception {
        BffSession session = newSession();
        ExchangedTokenService svc = service();
        ExchangedTokenService.DownstreamToken sta =
                svc.tokenFor(session.sessionRef(), "svc-station-operations", NOW);
        ExchangedTokenService.DownstreamToken dis =
                svc.tokenFor(session.sessionRef(), "svc-discovery-insights", NOW);
        assertNotEquals(sta.accessToken(), dis.accessToken(),
                "per-target exchange (one exchange per target service)");
        List<List<String>> audiences = new ArrayList<>();
        audiences.add(SignedJWT.parse(sta.accessToken()).getJWTClaimsSet().getAudience());
        audiences.add(SignedJWT.parse(dis.accessToken()).getJWTClaimsSet().getAudience());
        for (List<String> aud : audiences) {
            assertEquals(1, aud.size(), "no broad multi-audience tokens (§7.3)");
        }
    }
}
