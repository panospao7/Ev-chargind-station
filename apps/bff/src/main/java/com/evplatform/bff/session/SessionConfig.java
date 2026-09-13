package com.evplatform.bff.session;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the BFF session stack. {@link SessionKeyRing} is registered as
 * an eager bean so a deployment without configured encryption keys fails
 * fast at startup (SEC-001 §5.3); tests supply the key property
 * ({@code bff.session.encryption.keys.v1}) before the context boots.
 */
@Configuration
@EnableConfigurationProperties(BffSessionProperties.class)
public class SessionConfig {

    /** Injectable clock (SEC-001 §5.2 expiry evaluation); tests override with a MutableClock. */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SessionKeyRing sessionKeyRing(BffSessionProperties properties) {
        return new SessionKeyRing(
                properties.session() != null && properties.session().encryption() != null
                        ? properties.session().encryption().keys()
                        : java.util.Map.of());
    }
}
