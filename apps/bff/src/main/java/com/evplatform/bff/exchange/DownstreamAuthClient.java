package com.evplatform.bff.exchange;

import com.evplatform.bff.session.BffSessionProperties;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Authenticated call helper for downstream internal services (I1-IAM-002,
 * SEC-P03 §8.3): resolves a bearer token for a target audience through
 * {@link ExchangedTokenCache} and attaches it as
 * {@code Authorization: Bearer <token>} on requests built with the
 * per-audience {@link RestClient}.
 *
 * <p>Rest clients are built once per configured audience from
 * {@code bff.exchange.targets.<aud>.base-url}; an unknown audience or an
 * unconfigured base URL is a configuration error surfaced as
 * {@link IllegalStateException} (fail-fast, not a silent mis-route).</p>
 *
 * <p>On a downstream 401 the cached entry for that audience is invalidated
 * (JSON-null merge into {@code exchangedTokens}) and the exchange is retried
 * ONCE — a stale cached token gets one fresh exchange; a second 401 is
 * surfaced to the caller (the IdP, not the cache, is then the problem).
 * Token values are NEVER logged (SEC-001 §5.3, ARC-SEC-21).</p>
 */
@Component
public class DownstreamAuthClient {

    private static final Logger log = LoggerFactory.getLogger(DownstreamAuthClient.class);

    private final ExchangedTokenCache tokenCache;
    private final BffSessionProperties properties;
    private final Map<String, RestClient> clientsByAudience = new HashMap<>();

    public DownstreamAuthClient(ExchangedTokenCache tokenCache,
                                BffSessionProperties properties) {
        this.tokenCache = tokenCache;
        this.properties = properties;
    }

    /** Builds one RestClient per configured target audience (fail-fast on missing base URLs). */
    @PostConstruct
    void buildClients() {
        Map<String, BffSessionProperties.Exchange.Target> targets =
                properties.exchange() != null && properties.exchange().targets() != null
                        ? properties.exchange().targets()
                        : Map.of();
        targets.forEach((aud, target) -> {
            if (target == null || target.baseUrl() == null || target.baseUrl().isBlank()) {
                throw new IllegalStateException(
                        "bff.exchange.targets." + aud + ".base-url must be configured");
            }
            clientsByAudience.put(aud, RestClient.builder().baseUrl(target.baseUrl()).build());
        });
    }

    /**
     * Resolves the current bearer token for {@code aud} from the cache
     * (exchanging on miss) for the session identified by {@code sessionRef}.
     * Empty when the session is invalid or the exchange failed.
     */
    public Optional<String> bearerFor(String sessionRef, String aud) {
        ExchangedTokenCache.Result result = tokenCache.getOrExchange(sessionRef, aud);
        if (result instanceof ExchangedTokenCache.Cached cached) {
            return Optional.of(cached.accessToken());
        }
        ExchangedTokenCache.ExchangeFailure failure =
                (ExchangedTokenCache.ExchangeFailure) result;
        log.info("Downstream bearer resolution failed for audience (error={})",
                failure.error());
        return Optional.empty();
    }

    /**
     * Adds the {@code Authorization: Bearer} header to a request spec built
     * from the per-audience client. Exposed for callers that assemble their
     * own request pipeline (the spec is mutated in place and returned for
     * chaining).
     */
    public RestClient.RequestBodySpec attachAuth(RestClient.RequestBodySpec request,
                                                 String sessionRef, String aud) {
        Optional<String> bearer = bearerFor(sessionRef, aud);
        bearer.ifPresent(token ->
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        return request;
    }

    /**
     * Executes {@code executor} against the {@code aud} target with a
     * {@code Authorization: Bearer} header from the cache, retrying ONCE
     * after cache invalidation when the downstream rejects the token with
     * 401. The executor receives the per-audience client and the (possibly
     * refreshed) bearer token; returning {@code Integer} 401 signals token
     * rejection and triggers the invalidate-and-retry path — any other
     * outcome is returned as-is.
     */
    public <T> T exchangeAndCall(String sessionRef, String aud,
                                 RequestExecutor<T> executor) {
        T first = callWithToken(sessionRef, aud, executor);
        if (!isUnauthorized(first)) {
            return first;
        }
        // Downstream rejected the cached token: invalidate and retry once.
        log.info("Downstream returned 401 for audience (outcome=cache_invalidate_retry)");
        tokenCache.invalidate(sessionRef, aud);
        return callWithToken(sessionRef, aud, executor);
    }

    /** Callback receiving the per-audience client and the resolved bearer token. */
    @FunctionalInterface
    public interface RequestExecutor<T> {
        T execute(RestClient client, String bearerToken);
    }

    private <T> T callWithToken(String sessionRef, String aud,
                                RequestExecutor<T> executor) {
        Optional<String> bearer = bearerFor(sessionRef, aud);
        if (bearer.isEmpty()) {
            throw new DownstreamAuthException(
                    "No bearer token could be obtained for audience " + aud);
        }
        return executor.execute(clientFor(aud), bearer.get());
    }

    private RestClient clientFor(String aud) {
        RestClient client = clientsByAudience.get(aud);
        if (client == null) {
            throw new IllegalStateException(
                    "No downstream target configured for audience " + aud);
        }
        return client;
    }

    private static boolean isUnauthorized(Object result) {
        return result instanceof Integer status && status == 401;
    }

    /** Typed failure when no bearer token can be resolved for a target. */
    public static class DownstreamAuthException extends RuntimeException {
        public DownstreamAuthException(String message) {
            super(message);
        }
    }
}
