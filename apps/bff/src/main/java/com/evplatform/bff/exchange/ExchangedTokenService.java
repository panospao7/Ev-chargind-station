package com.evplatform.bff.exchange;

import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.SessionLifecycleService;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Facade for downstream service authentication (SEC-001 §7.1–7.2): returns
 * the cached audience-limited token for the session when valid, exchanges
 * and caches otherwise.
 *
 * Session validity is enforced here (not only at the security-context
 * layer): a REVOKED or expired session can never reach an exchange or a
 * cached token (§7.3 "revoked session cannot refresh or exchange",
 * packet AC-03).
 */
@Service
public class ExchangedTokenService {

    private final SessionLifecycleService lifecycle;
    private final TokenExchangeClient exchangeClient;
    private final ExchangedTokenCache cache;

    public ExchangedTokenService(SessionLifecycleService lifecycle,
                                 TokenExchangeClient exchangeClient,
                                 ExchangedTokenCache cache) {
        this.lifecycle = lifecycle;
        this.exchangeClient = exchangeClient;
        this.cache = cache;
    }

    /** One downstream-token resolution. */
    public record DownstreamToken(String accessToken, Instant expiresAt, boolean fromCache) {
    }

    /**
     * @param sessionRef the originating BFF session (must be ACTIVE)
     * @param targetAudience exactly one downstream svc-* client id
     * @param now clock injection point (tests use a MutableClock)
     * @return a valid downstream access token
     * @throws IllegalStateException when the session is absent/revoked/expired
     * @throws TokenExchangeClient.ExchangeFailedException when the exchange
     *         is refused by the IdP
     */
    public DownstreamToken tokenFor(String sessionRef, String targetAudience, Instant now) {
        SessionLifecycleService.Loaded loaded = lifecycle.loadValidSession(sessionRef, now);
        if (!(loaded instanceof SessionLifecycleService.Loaded.Valid valid)) {
            throw new IllegalStateException("session is not usable (absent, expired or revoked)");
        }
        BffSession session = valid.session();

        ExchangedTokenCache.CachedToken cached =
                cache.get(sessionRef, targetAudience, now);
        if (cached != null) {
            return new DownstreamToken(cached.accessToken(), cached.expiresAt(), true);
        }

        // the subject token presented for exchange is the session's IdP
        // access token (decrypted here, never logged)
        String subjectToken = lifecycle.decryptTokens(session)
                .map(SessionLifecycleService.TokenMaterial::accessToken)
                .orElseThrow(() -> new IllegalStateException(
                        "session token material could not be decrypted"));
        try {
            // KC 26 STX-v2: the exchange builds its token from the restricted
            // request scopes — requesting the exchange-audience client scope is
            // what makes the target audience available (W1 convention:
            // "exchange-<target>" scopes on ev-bff, attached by the realm fixture)
            String exchangeScope = "openid exchange-" + targetAudience;
            TokenExchangeClient.ExchangedToken exchanged =
                    exchangeClient.exchange(subjectToken, targetAudience, exchangeScope);
            Instant expiresAt = now.plusSeconds(
                    exchanged.expiresInSeconds() > 0 ? exchanged.expiresInSeconds() : 60);
            cache.put(sessionRef, targetAudience, exchanged.accessToken(), expiresAt, now);
            return new DownstreamToken(exchanged.accessToken(), expiresAt, false);
        } catch (TokenExchangeClient.ExchangeFailedException e) {
            throw e; // §7.3 negatives assert on these semantics
        } catch (Exception e) {
            throw new IllegalStateException("token exchange invocation failed", e);
        }
    }
}
