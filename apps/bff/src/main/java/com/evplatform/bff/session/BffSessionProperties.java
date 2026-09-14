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
public record BffSessionProperties(Session session, OAuth oauth) {

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
}
