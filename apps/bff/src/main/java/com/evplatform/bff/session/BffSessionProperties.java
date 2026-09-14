package com.evplatform.bff.session;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BFF session/OAuth custom properties (I1-IAM-001), bound from the
 * {@code bff.*} block of application.yml.
 *
 * <p>Encryption keys are supplied via environment only
 * ({@code BFF_SESSION_KEY_V1} → {@code bff.session.encryption.keys.v1});
 * the committed default is empty and the key ring fails fast at startup
 * when no key is configured (SEC-001 §5.3, SEC-P09: key material is never
 * committed). Token-lifetime values are the SEC-001 §5.2 W1 provisional
 * values (idle 30m, absolute 8h).</p>
 */
@ConfigurationProperties(prefix = "bff")
public record BffSessionProperties(Session session, OAuth oauth, Exchange exchange) {

    public record Session(
            Duration idleTimeout,
            Duration absoluteTimeout,
            String cookieName,
            List<String> allowedOrigins,
            Encryption encryption) {

        public record Encryption(Map<String, String> keys) {
        }
    }

    public record OAuth(String clientPrivateKeyPath) {
    }

    /**
     * Token-exchange cache + downstream target configuration (I1-IAM-002,
     * SEC-P03). {@code targets} maps a downstream audience (the target
     * service client id) to its base URL; {@code cache-expiry-skew} is
     * subtracted from a cached token's {@code expiresAt} so a token that is
     * within the skew window of expiry is re-exchanged rather than sent
     * (avoids racing the downstream validator's clock).
     */
    public record Exchange(Duration cacheExpirySkew, Map<String, Target> targets) {

        public record Target(String baseUrl) {
        }
    }
}
