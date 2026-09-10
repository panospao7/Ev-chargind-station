package com.evplatform.libraries.testsupport;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ENG-001 M1 criterion: Testcontainers can start real PostgreSQL 18 and
 * RabbitMQ 4.3, pinned to the release-manifest digests.
 */
class LocalDependenciesTest {

    @Test
    void postgres18StartsAndAnswersQueries() throws Exception {
        try (PostgreSQLContainer pg = LocalDependencies.newPostgres()) {
            pg.start();
            try (Connection c = pg.createConnection("");
                 PreparedStatement ps = c.prepareStatement("select version()");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String version = rs.getString(1);
                assertTrue(version.contains("PostgreSQL 18"), "expected PostgreSQL 18, got: " + version);
            }
        }
    }

    @Test
    void rabbitMq43StartsAndExposesAmqpPort() {
        try (RabbitMQContainer rmq = LocalDependencies.newRabbitMq()) {
            rmq.start();
            assertTrue(rmq.isRunning());
            Integer amqpPort = rmq.getAmqpPort();
            assertNotNull(amqpPort, "AMQP port must be mapped");
            assertTrue(amqpPort > 0);
        }
    }
}
