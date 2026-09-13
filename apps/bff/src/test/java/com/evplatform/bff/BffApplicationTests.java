package com.evplatform.bff;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Smoke: the BFF application context starts.
 *
 * I1-IAM-001 added the session-store persistence stack (JDBC/PostgreSQL) and
 * the OAuth2 client configuration to the module. This smoke test and the
 * proxy tests exercise Web MVC behavior without a database or identity
 * provider; DataSource and OAuth2Client auto-configuration are excluded here
 * (the OAuth2 client auto-configuration eagerly resolves the Keycloak issuer
 * at startup, which requires a running realm). The session store is covered
 * by dedicated Testcontainers integration tests (real PostgreSQL via the
 * shared provisioning script) and the login flow by dedicated session
 * integration tests with a stubbed IdP, so no test asserts against a live
 * developer database or identity provider.
 *
 * <p>I1-IAM-001 phase 2 part 2 (context-wiring completion): the session
 * stack is now eagerly wired (JdbcSessionStore → JdbcClient, SessionKeyRing,
 * ev-bff private_key_jwt signing key), so the DB-free/IdP-free smoke context
 * must supply: a mocked {@link JdbcClient} (never invoked without a session
 * cookie), a generated AES-256 test key for the key ring, a generated PKCS#8
 * RSA test key for {@code bff.oauth.client-private-key-path} (fail-fast
 * beans), and in-memory {@link ClientRegistrationRepository} +
 * {@link OAuth2AuthorizedClientService} stand-ins for the excluded
 * auto-configuration. Test keys are generated at class load — no key
 * material is committed (AGENTS.md §4).</p>
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration"
})
class BffApplicationTests {

    private static final String TEST_SESSION_KEY_B64;
    private static final String TEST_CLIENT_KEY_PEM_PATH;

    static {
        byte[] aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);
        TEST_SESSION_KEY_B64 = Base64.getEncoder().encodeToString(aesKey);
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            // getEncoded() on the RSA private key is PKCS#8 by default.
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[] {'\n'})
                            .encodeToString(pair.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            Path file = Files.createTempFile("bff-smoke-client-key", ".pem");
            Files.writeString(file, pem);
            TEST_CLIENT_KEY_PEM_PATH = file.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Smoke-test key setup failed", e);
        }
    }

    @DynamicPropertySource
    static void identityTestProperties(DynamicPropertyRegistry registry) {
        registry.add("bff.session.encryption.keys.v1", () -> TEST_SESSION_KEY_B64);
        registry.add("bff.oauth.client-private-key-path", () -> TEST_CLIENT_KEY_PEM_PATH);
    }

    @MockitoBean
    private JdbcClient jdbcClient;

    @Test
    void contextLoads() {
    }

    /** Stand-ins for the excluded OAuth2 client auto-configuration. */
    @TestConfiguration
    static class IdentityTestConfig {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(
                    ClientRegistration.withRegistrationId("ev-bff")
                            .clientId("ev-bff")
                            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("{baseUrl}/login/oauth2/code/ev-bff")
                            .authorizationUri("http://127.0.0.1:8180/realms/ev-local/protocol/openid-connect/auth")
                            .tokenUri("http://127.0.0.1:8180/realms/ev-local/protocol/openid-connect/token")
                            .scope("openid", "profile")
                            .build());
        }

        @Bean
        OAuth2AuthorizedClientService authorizedClientService(
                ClientRegistrationRepository repository) {
            return new InMemoryOAuth2AuthorizedClientService(repository);
        }
    }
}
