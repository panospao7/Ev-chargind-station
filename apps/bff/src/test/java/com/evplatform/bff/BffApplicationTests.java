package com.evplatform.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration"
})
class BffApplicationTests {

    @Test
    void contextLoads() {
    }
}
