package com.evplatform.stationoperations.outbox;

import com.evplatform.libraries.testsupport.LocalDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.GenericContainer;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-MSG-001 / POC-04: RabbitMQ outbox/inbox behaviour on real PostgreSQL 18
 * and RabbitMQ 4.3 — at-least-once delivery with inbox deduplication,
 * publisher confirms, bounded retry then quarantine, append-only audit.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessagingFoundationTest {

    private static final String PW = "evplatform_dev_only";
    private static final String DB = "station_operations_db";
    private static final String MIGRATOR = "station_operations_migrator";
    private static final String RUNTIME = "station_operations_runtime";
    private static final String SCHEMA = "station_operations";
    private static final String POC_QUEUE = "poc.station.published";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final PostgreSQLContainer PG = StartOnce.PG;
    static final GenericContainer<?> RABBIT = StartOnce.RABBIT;

    private static Connection connect(String user) throws Exception {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB,
                user, PW);
    }

    private static org.springframework.jdbc.core.simple.JdbcClient jdbc(String user) {
        var ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.postgresql.Driver(),
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB,
                user, PW);
        return org.springframework.jdbc.core.simple.JdbcClient.create(ds);
    }

    private static RabbitTemplate rabbitTemplate(String host, int port) {
        var cf = new CachingConnectionFactory(host, port);
        cf.setUsername("guest");
        cf.setPassword("guest");
        cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        cf.setPublisherReturns(true);
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMandatory(true);
        return template;
    }

    private static OutboxDispatcher dispatcherFor(RabbitTemplate template) {
        return new OutboxDispatcher(jdbc(RUNTIME), template, 3, 100, 2000);
    }

    private static com.evplatform.stationoperations.seed.StationOperationsSeeder seeder() {
        var ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.postgresql.Driver(),
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB,
                RUNTIME, PW);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        return new com.evplatform.stationoperations.seed.StationOperationsSeeder(
                org.springframework.jdbc.core.simple.JdbcClient.create(ds),
                new OutboxWriter(org.springframework.jdbc.core.simple.JdbcClient.create(ds)),
                tx);
    }
    private static void exec(String user, String sql) throws Exception {
        try (Connection c = connect(user);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private static int count(String table, String where) throws Exception {
        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM " + SCHEMA + "." + table +
                             (where == null ? "" : " WHERE " + where));
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    @Order(1)
    void integrationTablesExist() throws Exception {
        for (String table : List.of("outbox_message", "inbox_message",
                "idempotency_record", "audit_event")) {
            try (Connection c = connect(MIGRATOR);
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT 1 FROM information_schema.tables "
                                 + "WHERE table_schema = ? AND table_name = ?")) {
                ps.setString(1, SCHEMA);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "missing integration table: " + table);
                }
            }
        }
    }

    @Test
    @Order(2)
    void seedEmitsStationPublishedFactsAtomically() throws Exception {
// seed with the production path: data + outbox facts in one transaction
        seeder().seed();
        assertEquals(2, count("outbox_message",
                "state = 'PENDING' AND message_type = 'com.evplatform.station.published.v1'"),
                "one StationPublished fact per published station");
        // re-seed: the unique event-fact constraint must prevent duplicates
        seeder().seed();
        assertEquals(2, count("outbox_message",
                "message_type = 'com.evplatform.station.published.v1'"),
                "re-seeding must not duplicate event facts");
    }

    @Test
    @Order(3)
    void outboxFactsAreUniquePerAggregateVersion() throws Exception {
        // inserting the same fact twice must be a no-op (§8.1 protection)
        var writer = new OutboxWriter(jdbc(RUNTIME));
        var envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", "com.evplatform.station.published.v1");
        envelope.put("source", "//station-operations-service");
        envelope.put("id", "00000000-0000-0000-0000-000000000301");
        writer.append(UUID.fromString("00000000-0000-0000-0000-000000000999"),
                "EVENT", "com.evplatform.station.published.v1", "Station",
                com.evplatform.stationoperations.seed.SeedDataset.STATION_A_REF, 0,
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                null, "BUSINESS", envelope, java.time.Instant.now());
        assertEquals(1, count("outbox_message",
                "aggregate_ref = '00000000-0000-0000-0000-000000000010'"),
                "unique event-fact protection must keep exactly one fact");
        exec(MIGRATOR, "DELETE FROM " + SCHEMA + ".outbox_message WHERE message_id = '00000000-0000-0000-0000-000000000999'");
    }

    @Test
    @Order(4)
    void dispatcherPublishesWithConfirmAndConsumerDeduplicates() throws Exception {
        RabbitTemplate rabbit = rabbitTemplate(RABBIT.getHost(), RABBIT.getMappedPort(5672));
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        admin.declareExchange(new TopicExchange("ev.domain.v1", true, false));
        // quorum queue with DLQ (ENG-001 doc §6.4)
        admin.declareQueue(new Queue(POC_QUEUE + ".dlq", true, false, false,
                java.util.Map.of("x-queue-type", "quorum")));
        admin.declareQueue(new Queue(POC_QUEUE, true, false, false,
                java.util.Map.of("x-queue-type", "quorum",
                        "x-dead-letter-exchange", "",
                        "x-dead-letter-routing-key", POC_QUEUE + ".dlq")));
        admin.declareBinding(new Binding(POC_QUEUE, Binding.DestinationType.QUEUE,
                "ev.domain.v1", "station.published", java.util.Map.of()));

        OutboxDispatcher dispatcher = dispatcherFor(rabbit);
        int published = dispatcher.dispatchOnce();
        assertEquals(2, published, "both StationPublished facts must confirm");

        // PUBLISHED markers (confirms)
        assertEquals(2, count("outbox_message", "state = 'PUBLISHED'"));

        // consume both messages (manual acknowledgement via explicit channel)
        int received = 0;
        var cf = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getMappedPort(5672));
        cf.setUsername("guest");
        cf.setPassword("guest");
        try (com.rabbitmq.client.Connection conn = cf.getRabbitConnectionFactory().newConnection();
             com.rabbitmq.client.Channel channel = conn.createChannel()) {
            channel.basicQos(1);
            while (true) {
                var delivery = channel.basicGet(POC_QUEUE, false);
                if (delivery == null) break;
                JsonNode envelope = MAPPER.readTree(delivery.getBody());
                assertEquals("1.0", envelope.get("specversion").asText());
                assertEquals("com.evplatform.station.published.v1",
                        envelope.get("type").asText());
                // inbox deduplication: first delivery processes, duplicates skip
                String consumer = "poc-discovery-projection";
                boolean first = Boolean.TRUE.equals(jdbc(RUNTIME).sql("""
                                INSERT INTO station_operations.inbox_message
                                    (consumer_name, message_id, message_type, processing_outcome)
                                VALUES (?, ?, ?, 'COMPLETED')
                                ON CONFLICT (consumer_name, message_id) DO NOTHING
                                """)
                        .param(consumer)
                        .param(UUID.fromString(envelope.get("id").asText()))
                        .param(envelope.get("type").asText())
                        .update() > 0);
                assertTrue(first, "at-least-once duplicates must be deduplicated by the inbox");
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                received++;
            }
        }
        assertEquals(2, received, "both published events must reach the quorum queue");
    }

    @Test
    @Order(5)
    void publishFailureRetriesThenQuarantines() throws Exception {
        // dispatcher pointed at a closed port: every send fails deterministically
        RabbitTemplate dead = rabbitTemplate("127.0.0.1", 1);
        OutboxDispatcher failing = new OutboxDispatcher(jdbc(RUNTIME), dead, 3, 0, 2000);
        var writer = new OutboxWriter(jdbc(RUNTIME));
        var envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", "com.evplatform.station.published.v1");
        envelope.put("source", "//station-operations-service");
        envelope.put("id", "00000000-0000-0000-0000-000000000401");
        UUID fact = UUID.fromString("00000000-0000-0000-0000-000000000401");
        writer.append(fact, "EVENT", "com.evplatform.station.published.v1",
                "Station", com.evplatform.stationoperations.seed.SeedDataset.STATION_A_REF,
                1, fact, null, "BUSINESS", envelope, java.time.Instant.now());
        // make the row immediately available despite backoff
        exec(RUNTIME, "UPDATE " + SCHEMA + ".outbox_message SET available_at = now() "
                + "WHERE message_id = '" + fact + "'");
        failing.dispatchOnce();
        failing.dispatchOnce();
        failing.dispatchOnce();
        assertEquals("QUARANTINED", scalar(
                "SELECT state FROM " + SCHEMA + ".outbox_message WHERE message_id = '" + fact + "'"),
                "exhausted retries must quarantine the row");
        assertEquals(1, count("outbox_message",
                "message_id = '" + fact + "' AND attempt_count = 3 AND failure_category IS NOT NULL"),
                "quarantined row records three attempts and a failure category");
        // cleanup so later suites start clean
        exec(MIGRATOR, "DELETE FROM " + SCHEMA + ".outbox_message WHERE message_id = '" + fact + "'");
    }

    @Test
    @Order(6)
    void auditEventIsAppendOnlyForRuntime() throws Exception {
        exec(RUNTIME, "INSERT INTO " + SCHEMA + ".audit_event "
                + "(audit_ref, actor, calling_service, action, target, outcome, classification) "
                + "VALUES (gen_random_uuid(), 'seed-fixture', 'station-operations-service', "
                + "'SEED_APPLIED', 'station_operations_db', 'SUCCESS', 'BUSINESS')");
        assertThrows(Exception.class, () -> exec(RUNTIME,
                "UPDATE " + SCHEMA + ".audit_event SET outcome = 'TAMPERED'"));
        assertThrows(Exception.class, () -> exec(RUNTIME,
                "DELETE FROM " + SCHEMA + ".audit_event"));
    }

    @Test
    @Order(7)
    void idempotencyRecordEnforcesScopeUniqueness() throws Exception {
        exec(RUNTIME, "INSERT INTO " + SCHEMA + ".idempotency_record "
                + "(idempotency_ref, principal_identity, operation, target_resource, "
                + "idempotency_key, state, expires_at) VALUES "
                + "(gen_random_uuid(), 'driver-1', 'CreateBooking', 'evse-1', 'idem-key-1', "
                + "'COMPLETED', now() + interval '24 hours')");
        // same scope must conflict
        assertThrows(Exception.class, () -> exec(RUNTIME,
                "INSERT INTO " + SCHEMA + ".idempotency_record "
                        + "(idempotency_ref, principal_identity, operation, target_resource, "
                        + "idempotency_key, state, expires_at) VALUES "
                        + "(gen_random_uuid(), 'driver-1', 'CreateBooking', 'evse-1', 'idem-key-1', "
                        + "'COMPLETED', now() + interval '24 hours')"));
        exec(MIGRATOR, "DELETE FROM " + SCHEMA + ".idempotency_record "
                + "WHERE idempotency_key = 'idem-key-1'");
    }

    private String scalar(String sql) throws Exception {
        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    /** Containers shared by the ordered suite. */
    private static class StartOnce {
        static final PostgreSQLContainer PG = startPg();
        static final GenericContainer<?> RABBIT = LocalDependencies.newRabbitMq();

        static PostgreSQLContainer startPg() {
            PostgreSQLContainer c = LocalDependencies.newPostgresWithProvisioning(
                    Path.of("..", "..", "infra", "local", "postgres"));
            c.start();
            
            Flyway.configure()
                    .dataSource("jdbc:postgresql://" + c.getHost() + ":" + c.getMappedPort(5432) + "/" + DB, MIGRATOR, PW)
                    .locations("filesystem:" + Path.of("src", "main", "resources", "db", "migration").toAbsolutePath().normalize())
                    .schemas(SCHEMA)
                    .defaultSchema(SCHEMA)
                    .load()
                    .migrate();
            return c;
        }

        static {
            RABBIT.start();
        }
    }
}
