package com.evplatform.bff.exchange;

import com.evplatform.bff.realm.KeycloakExchangeFixture;
import com.evplatform.bff.session.BffSessionProperties;
import com.evplatform.bff.session.JdbcSessionStore;
import com.evplatform.bff.session.SessionKeyRing;
import com.evplatform.bff.session.SessionLifecycleService;
import com.evplatform.bff.session.TokenEncryptionService;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Phase-3 test wiring: assembles the real session stack (store, key ring,
 * encryption, lifecycle — the I1-IAM-001 construction) and the exchange
 * stack (client, cache, service) over one PostgreSQL container (BFF
 * migrations) plus the {@link KeycloakExchangeFixture} realm, without a
 * Spring context. No Spring wiring duplication: constructor shapes mirror
 * the JdbcSessionStoreTests pattern.
 */
final class Fixtures {

    private Fixtures() {
    }

    static final String PW = "evplatform_dev_only";
    static final String DB = "bff_session_db";
    static final String MIGRATOR = "bff_session_migrator";

    static final org.testcontainers.postgresql.PostgreSQLContainer PG = PgHolder.PG;

    static JdbcClient bffJdbc() {
        var ds = new SimpleDriverDataSource(new org.postgresql.Driver(),
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB,
                MIGRATOR, PW);
        return JdbcClient.create(ds);
    }

    static JdbcSessionStore store(KeycloakExchangeFixture fx) {
        return new JdbcSessionStore(bffJdbc(), Clock.systemUTC(), sessionProperties());
    }

    static SessionKeyRing keyRing() {
        // test-only dev key (base64 of 32 bytes), mirroring the IAM-001 test pattern
        return new SessionKeyRing(java.util.Map.of("v1", Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes())));
    }

    static BffSessionProperties sessionProperties() {
        return new BffSessionProperties(
                new BffSessionProperties.Session(
                        Duration.ofMinutes(30), Duration.ofHours(8),
                        "__Host-evsession", List.of(),
                        new BffSessionProperties.Session.Encryption(java.util.Map.of(
                                "v1", Base64.getEncoder()
                                        .encodeToString("0123456789abcdef0123456789abcdef".getBytes())))),
                new BffSessionProperties.OAuth(null));
    }

    static SessionLifecycleService lifecycle(KeycloakExchangeFixture fx) {
        return new SessionLifecycleService(store(fx),
                new TokenEncryptionService(keyRing()),
                Clock.systemUTC(), keyRing(), sessionProperties());
    }

    static TokenExchangeClient exchangeClient(KeycloakExchangeFixture fx) {
        return new TokenExchangeClient(fx.evBffRsaKey(), fx.issuer(), "ev-bff", 60);
    }

    static ExchangedTokenCache cache(KeycloakExchangeFixture fx) {
        return new ExchangedTokenCache(store(fx),
                new TokenEncryptionService(keyRing()), 30);
    }

    static ExchangedTokenService exchangeService(KeycloakExchangeFixture fx) {
        return new ExchangedTokenService(lifecycle(fx), exchangeClient(fx), cache(fx));
    }

    /** Migrates the BFF schema once per class-load; container lives for the suite. */
    static {
        forceStart();
        Flyway.configure()
                .dataSource("jdbc:postgresql://" + PgHolder.PG.getHost() + ":"
                        + PgHolder.PG.getMappedPort(5432) + "/" + DB, MIGRATOR, PW)
                .locations("filesystem:" + Path.of("..", "..", "apps", "bff",
                        "src", "main", "resources", "db", "migration")
                        .toAbsolutePath().normalize())
                .schemas("bff_session")
                .defaultSchema("bff_session")
                .load()
                .migrate();
    }

    private static void forceStart() {
        PgHolder.PG.getHost();
    }

    private static final class PgHolder {
        static final org.testcontainers.postgresql.PostgreSQLContainer PG = start();

        static org.testcontainers.postgresql.PostgreSQLContainer start() {
            org.testcontainers.postgresql.PostgreSQLContainer c =
                    com.evplatform.libraries.testsupport.LocalDependencies
                            .newPostgresWithProvisioning(
                                    java.nio.file.Path.of("..", "..", "infra", "local",
                                            "postgres"));
            c.start();
            return c;
        }
    }
}
