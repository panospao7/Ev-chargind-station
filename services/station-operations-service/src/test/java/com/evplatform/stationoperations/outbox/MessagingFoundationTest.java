package com.evplatform.stationoperations.outbox;

import com.evplatform.libraries.testsupport.LocalDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * message_type → (dataschema $id, routing key) for every family the seed
     * emits; mirrors the registry and the dispatcher's map/derivation.
     */
    private static final Map<String, List<String>> FAMILY_BY_TYPE = Map.of(
            "com.evplatform.station.published.v1",
            List.of("https://schema-registry.example.com/events/station-published-event.json",
                    "station.published"),
            "com.evplatform.station.evse-configuration-changed.v1",
            List.of("https://schema-registry.example.com/events/evse-configuration-changed-event.json",
                    "station.evse-configuration-changed"),
            "com.evplatform.station.connector-configuration-changed.v1",
            List.of("https://schema-registry.example.com/events/connector-configuration-changed-event.json",
                    "station.connector-configuration-changed"),
            "com.evplatform.station.tariff-published.v1",
            List.of("https://schema-registry.example.com/events/tariff-published-event.json",
                    "station.tariff-published"));

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
        return new OutboxDispatcher(jdbc(RUNTIME), template, 3, 100, 2000, 30);
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

    /**
     * Appends one fresh StationPublished fact with the same envelope shape
     * the seeder emits (cloud-event.json + station-published-event.json
     * conformant), with a distinct aggregate_ref so the event-fact unique
     * constraint never collides with seed rows.
     */
    private static UUID appendFreshFact(OutboxWriter writer, UUID messageId) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("stationRef", messageId.toString());
        data.put("publicRef", "FIXSTA-" + messageId);
        data.put("displayName", "Messaging Fixture Station");
        data.put("addressLine", "Fixture Street 9");
        data.put("city", "Athens");
        data.put("postalCode", "10431");
        data.put("countryCode", "GR");
        data.put("latitude", 37.983810);
        data.put("longitude", 23.727540);

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", "com.evplatform.station.published.v1");
        envelope.put("source", "//station-operations-service");
        envelope.put("id", messageId.toString());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("subject", "station/" + data.get("publicRef").asText());
        envelope.set("data", data);

        writer.append(messageId, "EVENT", "com.evplatform.station.published.v1",
                "Station", messageId, 0, messageId, null, "BUSINESS",
                envelope, Instant.now());
        return messageId;
    }

    /** Declares the Order-4 POC topology on a fresh admin connection. */
    private static RabbitAdmin declarePocTopology() {
        RabbitTemplate template = rabbitTemplate(RABBIT.getHost(), RABBIT.getMappedPort(5672));
        RabbitAdmin admin = new RabbitAdmin(template);
        admin.declareExchange(new TopicExchange("ev.domain.v1", true, false));
        admin.declareQueue(new Queue(POC_QUEUE + ".dlq", true, false, false,
                java.util.Map.of("x-queue-type", "quorum")));
        admin.declareQueue(new Queue(POC_QUEUE, true, false, false,
                java.util.Map.of("x-queue-type", "quorum",
                        "x-dead-letter-exchange", "",
                        "x-dead-letter-routing-key", POC_QUEUE + ".dlq")));
        admin.declareBinding(new Binding(POC_QUEUE, DestinationType.QUEUE,
                "ev.domain.v1", "station.published", java.util.Map.of()));
        return admin;
    }

    private static String rowState(UUID messageId) throws Exception {
        return scalar("SELECT state FROM " + SCHEMA + ".outbox_message "
                + "WHERE message_id = '" + messageId + "'");
    }

    /**
     * Selects the §8.1 traceability columns for one outbox message so the
     * consumed wire envelope can be asserted against the authoritative
     * columns (I1-MSG-003).
     */
    private record OutboxColumns(UUID correlationId, UUID causationId,
                                 UUID aggregateRef, long aggregateVersion,
                                 String classification) {
    }

    private static OutboxColumns outboxColumns(UUID messageId) throws Exception {
        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT correlation_id, causation_id, aggregate_ref, aggregate_version, "
                             + "classification "
                             + "FROM " + SCHEMA + ".outbox_message WHERE message_id = ?")) {
            ps.setObject(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "outbox row must exist: " + messageId);
                long version = rs.getLong("aggregate_version");
                assertFalse(rs.wasNull(), "aggregate_version must not be null");
                UUID correlation = rs.getObject("correlation_id", UUID.class);
                UUID causation = rs.getObject("causation_id", UUID.class);
                UUID aggregateRef = rs.getObject("aggregate_ref", UUID.class);
                String classification = rs.getString("classification");
                return new OutboxColumns(correlation, causation, aggregateRef, version,
                        classification);
            }
        }
    }

    private static int outboxCount(String where) throws Exception {
        return count("outbox_message", where);
    }

    private static void deleteOutboxRow(UUID messageId) throws Exception {
        exec(MIGRATOR, "DELETE FROM " + SCHEMA + ".outbox_message "
                + "WHERE message_id = '" + messageId + "'");
    }

    private static void deleteInboxRow(String consumer, UUID messageId) throws Exception {
        exec(MIGRATOR, "DELETE FROM " + SCHEMA + ".inbox_message "
                + "WHERE consumer_name = '" + consumer + "' AND message_id = '" + messageId + "'");
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
        // one binding per seeded fact family: the dispatcher derives routing
        // keys from the message types, so every family must be bound or the
        // mandatory publishes would be returned as unroutable
        for (List<String> family : FAMILY_BY_TYPE.values()) {
            admin.declareBinding(new Binding(POC_QUEUE, Binding.DestinationType.QUEUE,
                    "ev.domain.v1", family.get(1), java.util.Map.of()));
        }

        OutboxDispatcher dispatcher = dispatcherFor(rabbit);
        int published = dispatcher.dispatchOnce();
        assertEquals(15, published, "all 15 seed facts must confirm in one batch");

        // PUBLISHED markers (confirms)
        assertEquals(15, count("outbox_message", "state = 'PUBLISHED'"));

        // consume all 15 messages (manual acknowledgement via explicit channel)
        int received = 0;
        var seenFamilies = new LinkedHashSet<String>();
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
                String messageType = envelope.get("type").asText();
                List<String> family = FAMILY_BY_TYPE.get(messageType);
                assertNotNull(family, "every delivered type must be a seeded family: "
                        + messageType);

                // I1-MSG-003: the wire envelope must carry the ARC-014 §2
                // extension attributes derived from the outbox columns,
                // including the per-family dataschema and the routing key
                // derived from the message type (registry naming).
                assertEquals(family.get(0), envelope.get("dataschema").asText(),
                        "dataschema must map the message type to the event schema $id");
                assertEquals(family.get(1), delivery.getEnvelope().getRoutingKey(),
                        "routing key must be derived from the message type per the registry");
                UUID consumedId = UUID.fromString(envelope.get("id").asText());
                OutboxColumns columns = outboxColumns(consumedId);
                assertEquals(columns.correlationId().toString(),
                        envelope.get("correlationid").asText(),
                        "correlationid must equal the outbox correlation_id column");
                assertEquals(columns.aggregateRef().toString(),
                        envelope.get("aggregateid").asText(),
                        "aggregateid must equal the outbox aggregate_ref column");
                assertEquals(columns.aggregateVersion(),
                        envelope.get("aggregateversion").asLong(),
                        "aggregateversion must equal the outbox aggregate_version column as a number");
                assertEquals(columns.classification(),
                        envelope.get("classification").asText(),
                        "classification must equal the outbox classification column");
                // seed facts have NULL causation_id → the attribute must be
                // absent entirely (never serialized as null), and traceparent
                // is never emitted (AC-01 honest-disclosure assertions).
                assertNull(envelope.get("causationid"),
                        "causationid must be absent for NULL causation_id, not null-valued");
                assertNull(envelope.get("traceparent"),
                        "traceparent must never be emitted by the dispatcher");

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
                        .param(messageType)
                        .update() > 0);
                assertTrue(first, "at-least-once duplicates must be deduplicated by the inbox");
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                seenFamilies.add(messageType);
                received++;
            }
        }
        assertEquals(15, received, "all 15 seed facts must reach the quorum queue");
        assertEquals(FAMILY_BY_TYPE.keySet(), seenFamilies,
                "all four seeded fact families must be observed on the wire");
    }

    @Test
    @Order(5)
    void publishFailureRetriesThenQuarantines() throws Exception {
        // dispatcher pointed at a closed port: every send fails deterministically
        RabbitTemplate dead = rabbitTemplate("127.0.0.1", 1);
        OutboxDispatcher failing = new OutboxDispatcher(jdbc(RUNTIME), dead, 3, 0, 2000, 30);
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

    /**
     * AC-01 (review finding M1): an unroutable mandatory publish — acked by
     * the broker but returned because no queue is bound — must be treated as
     * a dispatch failure: never PUBLISHED, attempts increment with the
     * UNROUTABLE failure category, and after max attempts the row is
     * QUARANTINED.
     *
     * The POC queue binding declared by Order 4 persists in this shared
     * container, so this test unbinds it for the duration and re-binds it in
     * a finally block; later orders rely on the restored binding. (The
     * routing key is derived from the message type, so per-message rerouting
     * is not an option.)
     */
    @Test
    @Order(8)
    void dispatchUnroutablePublishRetriesThenQuarantines() throws Exception {
        // unbind the POC queue so the mandatory publish is returned as unroutable
        try (com.rabbitmq.client.Connection conn = new CachingConnectionFactory(
                RABBIT.getHost(), RABBIT.getMappedPort(5672)).getRabbitConnectionFactory()
                .newConnection();
             com.rabbitmq.client.Channel channel = conn.createChannel()) {
            channel.queueUnbind(POC_QUEUE, "ev.domain.v1", "station.published");
        }

        RabbitTemplate rabbit = rabbitTemplate(RABBIT.getHost(), RABBIT.getMappedPort(5672));
        // maxAttempts=3, backoff=0: after every failed pass the row is
        // immediately due again (markAttempt overwrites available_at with
        // now()+0), so repeated dispatchOnce() passes exercise the retry path
        OutboxDispatcher dispatcher = new OutboxDispatcher(jdbc(RUNTIME), rabbit, 3, 0, 2000, 30);
        var writer = new OutboxWriter(jdbc(RUNTIME));
        UUID fact = UUID.fromString("00000000-0000-0000-0000-000000000501");
        try {
            appendFreshFact(writer, fact);

            // pass 1: unroutable → attempt 1, still PENDING, UNROUTABLE
            dispatcher.dispatchOnce();
            assertEquals("PENDING", rowState(fact),
                    "an unroutable publish must never be marked PUBLISHED");
            assertEquals(1, outboxCount("message_id = '" + fact + "' AND attempt_count = 1 "
                    + "AND failure_category = 'UNROUTABLE'"),
                    "pass 1 must record attempt 1 with the UNROUTABLE category");

            // pass 2: still PENDING, attempt 2
            dispatcher.dispatchOnce();
            assertEquals("PENDING", rowState(fact),
                    "pass 2 must not publish an unroutable fact");
            assertEquals(1, outboxCount("message_id = '" + fact + "' AND attempt_count = 2 "
                    + "AND failure_category = 'UNROUTABLE'"),
                    "pass 2 must record attempt 2 with the UNROUTABLE category");

            // pass 3: attempts exhausted → QUARANTINED
            dispatcher.dispatchOnce();
            assertEquals("QUARANTINED", rowState(fact),
                    "exhausted unroutable retries must quarantine the row");
            assertEquals(1, outboxCount("message_id = '" + fact + "' AND attempt_count = 3 "
                    + "AND failure_category = 'UNROUTABLE'"),
                    "quarantined row records three UNROUTABLE attempts");
            assertEquals(0, outboxCount("message_id = '" + fact + "' AND state = 'PUBLISHED'"),
                    "the unroutable fact must never reach PUBLISHED");
        } finally {
            // restore the binding so later orders (and suites) are unaffected
            declarePocTopology();
            deleteOutboxRow(fact);
        }
    }

    /**
     * AC-02 (review finding M2): a forced duplicate delivery of the same
     * message is deduplicated by the inbox — the second delivery is
     * recognized via envelope.isRedelivered() and the inbox insert yields
     * first=false, leaving exactly one inbox row.
     */
    @Test
    @Order(9)
    void duplicateDeliveryIsDeduplicatedByInbox() throws Exception {
        // ensure the POC binding exists (restored by Order 8's finally)
        declarePocTopology();
        // drain any leftovers so this test observes only its own message
        drainPocQueue();

        RabbitTemplate rabbit = rabbitTemplate(RABBIT.getHost(), RABBIT.getMappedPort(5672));
        OutboxDispatcher dispatcher = dispatcherFor(rabbit);
        var writer = new OutboxWriter(jdbc(RUNTIME));
        UUID fact = UUID.fromString("00000000-0000-0000-0000-000000000601");
        String consumer = "poc-dedup-fixture";
        try {
            appendFreshFact(writer, fact);
            assertEquals(1, dispatcher.dispatchOnce(), "the fresh fact must confirm");

            var cf = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getMappedPort(5672));
            cf.setUsername("guest");
            cf.setPassword("guest");
            try (com.rabbitmq.client.Connection conn = cf.getRabbitConnectionFactory()
                    .newConnection();
                 com.rabbitmq.client.Channel channel = conn.createChannel()) {

                // delivery #1
                var first = pollDelivery(channel, fact);
                JsonNode envelope = MAPPER.readTree(first.getBody());
                UUID messageId = UUID.fromString(envelope.get("id").asText());
                assertEquals(fact, messageId, "the delivered id must be the appended fact");
                assertFalse(first.getEnvelope().isRedeliver(),
                        "first delivery must not be flagged redelivered");
                boolean firstInsert = Boolean.TRUE.equals(jdbc(RUNTIME).sql("""
                                INSERT INTO station_operations.inbox_message
                                    (consumer_name, message_id, message_type, processing_outcome)
                                VALUES (?, ?, ?, 'COMPLETED')
                                ON CONFLICT (consumer_name, message_id) DO NOTHING
                                """)
                        .param(consumer)
                        .param(messageId)
                        .param(envelope.get("type").asText())
                        .update() > 0);
                assertTrue(firstInsert, "the first delivery must be processed (first=true)");
                // REQUEUE (not dead-letter): forces a genuine duplicate delivery
                channel.basicNack(first.getEnvelope().getDeliveryTag(), false, true);

                // delivery #2: the requeued message comes back redelivered
                var second = pollDelivery(channel, fact);
                JsonNode envelope2 = MAPPER.readTree(second.getBody());
                assertEquals(messageId, UUID.fromString(envelope2.get("id").asText()),
                        "the duplicate delivery must carry the same message id");
                assertTrue(second.getEnvelope().isRedeliver(),
                        "the requeued duplicate must be flagged redelivered");
                boolean duplicateInsert = Boolean.TRUE.equals(jdbc(RUNTIME).sql("""
                                INSERT INTO station_operations.inbox_message
                                    (consumer_name, message_id, message_type, processing_outcome)
                                VALUES (?, ?, ?, 'COMPLETED')
                                ON CONFLICT (consumer_name, message_id) DO NOTHING
                                """)
                        .param(consumer)
                        .param(messageId)
                        .param(envelope2.get("type").asText())
                        .update() > 0);
                assertFalse(duplicateInsert,
                        "the duplicate delivery must be deduplicated (first=false)");
                channel.basicAck(second.getEnvelope().getDeliveryTag(), false);

                assertEquals(1, count("inbox_message", "consumer_name = '" + consumer
                                + "' AND message_id = '" + messageId + "'"),
                        "exactly one inbox row must exist for the duplicated message");
            }
        } finally {
            deleteOutboxRow(fact);
            deleteInboxRow(consumer, fact);
        }
    }

    /**
     * AC-03 (review finding M3): two concurrent dispatcher instances must not
     * double-publish — the FOR UPDATE SKIP LOCKED claim makes each row
     * claimable by exactly one instance, so every fact is sent and marked
     * exactly once.
     */
    @Test
    @Order(10)
    void twoDispatcherInstancesDoNotDoublePublish() throws Exception {
        // ensure the POC binding exists (restored by Order 8's finally)
        declarePocTopology();
        drainPocQueue();

        var writer = new OutboxWriter(jdbc(RUNTIME));
        List<UUID> facts = new ArrayList<>();
        for (long n = 701; n <= 703; n++) {
            UUID fact = UUID.fromString("00000000-0000-0000-0000-0000000007"
                    + String.format("%02d", n - 701));
            facts.add(fact);
            appendFreshFact(writer, fact);
        }
        try {
            OutboxDispatcher one = new OutboxDispatcher(
                    jdbc(RUNTIME), rabbitTemplate(RABBIT.getHost(), RABBIT.getMappedPort(5672)),
                    3, 0, 2000, 2);
            OutboxDispatcher two = new OutboxDispatcher(
                    jdbc(RUNTIME), rabbitTemplate(RABBIT.getHost(), RABBIT.getMappedPort(5672)),
                    3, 0, 2000, 2);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            var futures = List.of(
                    pool.submit(() -> runPasses(one, start)),
                    pool.submit(() -> runPasses(two, start)));
            start.countDown();
            for (var future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "pool must terminate");

            for (UUID fact : facts) {
                assertEquals("PUBLISHED", rowState(fact),
                        "each fact must reach exactly one PUBLISHED outcome");
                assertEquals(1, outboxCount("message_id = '" + fact
                                + "' AND attempt_count = 1 AND state = 'PUBLISHED'"),
                        "a fact marked by both instances would show attempt_count > 1");
                assertEquals(0, outboxCount("message_id = '" + fact
                                + "' AND state = 'QUARANTINED'"),
                        "no fact may be quarantined by the race");
            }

            // The DB assertions above are necessary but NOT sufficient to
            // detect double-publish: the actual detector is this queue
            // drain — exactly 3 messages with 3 distinct ids. A duplicate
            // send would surface here as a fourth delivery repeating an
            // already-seen message_id. (attempt_count only proves the
            // CAS-guarded markPublished ran once per row, not that the
            // broker received the message once.)
            Set<String> ids = new LinkedHashSet<>();
            int total = 0;
            var cf = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getMappedPort(5672));
            cf.setUsername("guest");
            cf.setPassword("guest");
            try (com.rabbitmq.client.Connection conn = cf.getRabbitConnectionFactory()
                    .newConnection();
                 com.rabbitmq.client.Channel channel = conn.createChannel()) {
                while (true) {
                    var delivery = channel.basicGet(POC_QUEUE, false);
                    if (delivery == null) break;
                    JsonNode envelope = MAPPER.readTree(delivery.getBody());
                    ids.add(envelope.get("id").asText());
                    total++;
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }
            }
            assertEquals(3, total, "exactly three messages must be delivered");
            assertEquals(3, ids.size(), "delivered message ids must be distinct");
        } finally {
            for (UUID fact : facts) {
                deleteOutboxRow(fact);
            }
        }
    }

    /** Runs up to 10 dispatch passes after the start gate opens. */
    private static void runPasses(OutboxDispatcher dispatcher, CountDownLatch start) {
        try {
            assertTrue(start.await(30, TimeUnit.SECONDS), "start gate must open");
            for (int i = 0; i < 10; i++) {
                dispatcher.dispatchOnce();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Polls the POC queue for a delivery of the given fact id (max ~5s). */
    private static com.rabbitmq.client.GetResponse pollDelivery(
            com.rabbitmq.client.Channel channel, UUID fact) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            var delivery = channel.basicGet(POC_QUEUE, false);
            if (delivery != null) {
                JsonNode envelope = MAPPER.readTree(delivery.getBody());
                if (fact.toString().equals(envelope.get("id").asText())) {
                    return delivery;
                }
                // not ours: acknowledge and keep polling
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no delivery of " + fact + " within 5s");
    }

    /** Acknowledges and discards everything currently in the POC queue. */
    private static void drainPocQueue() throws Exception {
        var cf = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getMappedPort(5672));
        cf.setUsername("guest");
        cf.setPassword("guest");
        try (com.rabbitmq.client.Connection conn = cf.getRabbitConnectionFactory()
                .newConnection();
             com.rabbitmq.client.Channel channel = conn.createChannel()) {
            while (true) {
                var delivery = channel.basicGet(POC_QUEUE, false);
                if (delivery == null) break;
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        }
    }

    /**
     * AC-06 (review finding m2): the ON CONFLICT clause must target the
     * event-fact uniqueness constraint explicitly — re-inserting the SAME
     * fact identity (same aggregate_ref, different message_id) stays a
     * silent no-op, while a message_id collision on a DIFFERENT fact
     * surfaces as a primary-key violation instead of being masked.
     */
    @Test
    @Order(11)
    void outboxConflictTargetSeparatesFactIdempotencyFromPkViolations() throws Exception {
        var writer = new OutboxWriter(jdbc(RUNTIME));
        UUID fact = UUID.fromString("00000000-0000-0000-0000-000000000801");
        UUID sameFactOtherMessage = UUID.fromString("00000000-0000-0000-0000-000000000802");
        UUID otherFact = UUID.fromString("00000000-0000-0000-0000-000000000803");
        try {
            appendFreshFact(writer, fact); // message_id 801 = fact, aggregate_ref = fact
            int before = outboxCount(null);

            // same fact identity (aggregate_type/ref/version/message_type)
            // as the first fact, different message_id → silent no-op, total
            // outbox count unchanged. aggregate_ref is deliberately the
            // FIRST fact's ref: appendFreshFact derives aggregate_ref from
            // the message id, which would insert a new fact (802) instead
            // of exercising the event-fact conflict target at all.
            assertDoesNotThrow(() -> {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("stationRef", fact.toString());
                data.put("publicRef", "FIXSTA-" + fact);
                data.put("displayName", "Messaging Fixture Station");
                data.put("addressLine", "Fixture Street 9");
                data.put("city", "Athens");
                data.put("postalCode", "10431");
                data.put("countryCode", "GR");
                data.put("latitude", 37.983810);
                data.put("longitude", 23.727540);
                ObjectNode envelope = MAPPER.createObjectNode();
                envelope.put("specversion", "1.0");
                envelope.put("type", "com.evplatform.station.published.v1");
                envelope.put("source", "//station-operations-service");
                envelope.put("id", sameFactOtherMessage.toString());
                envelope.put("time", Instant.now().toString());
                envelope.put("datacontenttype", "application/json");
                envelope.put("subject", "station/" + data.get("publicRef").asText());
                envelope.set("data", data);
                // message_id = 802, aggregate_ref = fact (801): the SAME fact
                writer.append(sameFactOtherMessage, "EVENT",
                        "com.evplatform.station.published.v1",
                        "Station", fact, 0, fact, null, "BUSINESS",
                        envelope, Instant.now());
            });
            assertEquals(before, outboxCount(null),
                    "a duplicate fact insert must be a silent no-op (total outbox count unchanged)");

            // different fact (different aggregate_ref) reusing message_id M1
            // → PK violation must surface (the fact-conflict target does not
            // match, so the collision reaches the primary key)
            assertThrows(Exception.class, () -> {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("stationRef", otherFact.toString());
                data.put("publicRef", "FIXSTA-" + otherFact);
                data.put("displayName", "Messaging Fixture Station");
                data.put("countryCode", "GR");
                data.put("latitude", 37.983810);
                data.put("longitude", 23.727540);
                ObjectNode envelope = MAPPER.createObjectNode();
                envelope.put("specversion", "1.0");
                envelope.put("type", "com.evplatform.station.published.v1");
                envelope.put("source", "//station-operations-service");
                envelope.put("id", fact.toString()); // M1 reused
                envelope.set("data", data);
                // message_id = fact (M1), aggregate_ref = otherFact
                writer.append(fact, "EVENT", "com.evplatform.station.published.v1",
                        "Station", otherFact, 0, otherFact, null, "BUSINESS",
                        envelope, Instant.now());
            }, "reusing a message_id for a different fact must raise a PK violation");
        } finally {
            deleteOutboxRow(fact);
            deleteOutboxRow(sameFactOtherMessage);
            deleteOutboxRow(otherFact);
        }
    }

    /**
     * AC-06 (review finding m4): the outbox.* configuration keys bind through
     * the environment — OUTBOX_MAX_ATTEMPTS reaches the dispatcher bean's
     * maxAttempts via the ${OUTBOX_MAX_ATTEMPTS:3} placeholder in
     * application.yml.
     */
    @Test
    @Order(12)
    void outboxMaxAttemptsOverrideReachesDispatcher() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("OUTBOX_MAX_ATTEMPTS:7")
                .withUserConfiguration(ConfigBindingFixture.class)
                .run(context -> {
                    assertNotNull(context.getBean(OutboxDispatcher.class),
                            "the dispatcher bean must be registered");
                    OutboxDispatcher dispatcher = context.getBean(OutboxDispatcher.class);
                    assertEquals(7, maxAttemptsOf(dispatcher),
                            "OUTBOX_MAX_ATTEMPTS must override outbox.max-attempts");
                });
    }

    /** Minimal configuration: dispatcher + stub collaborators, no broker/db. */
    @Configuration
    static class ConfigBindingFixture {
        @Bean
        OutboxDispatcher outboxDispatcher(org.springframework.jdbc.core.simple.JdbcClient jdbc,
                                          @Value("${outbox.max-attempts:3}") int maxAttempts,
                                          @Value("${outbox.backoff-ms:2000}") long backoffMs,
                                          @Value("${outbox.confirm-timeout-ms:5000}") long confirmTimeoutMs,
                                          @Value("${outbox.claim-lease-seconds:30}") int claimLeaseSeconds) {
            // the dispatcher never runs in this test — only constructor binding matters
            return new OutboxDispatcher(jdbc, Mockito.mock(RabbitTemplate.class),
                    maxAttempts, backoffMs, confirmTimeoutMs, claimLeaseSeconds);
        }

        @Bean
        org.springframework.jdbc.core.simple.JdbcClient jdbcClient() {
            return Mockito.mock(org.springframework.jdbc.core.simple.JdbcClient.class);
        }
    }

    private static int maxAttemptsOf(OutboxDispatcher dispatcher) {
        try {
            Field field = OutboxDispatcher.class.getDeclaredField("maxAttempts");
            field.setAccessible(true);
            return field.getInt(dispatcher);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * MINOR-7: an outbox row whose message_type has no dataschema mapping
     * must never be published — dispatch marks it DATASCHEMA_UNMAPPED
     * (fail-fast, I1-MSG-003 closure) and, after the max attempt budget, the
     * row is QUARANTINED instead of shipping a schema-less fact.
     *
     * The row is inserted directly (a bare INSERT, not OutboxWriter, whose
     * envelope validation would refuse nothing here but whose fact-identity
     * conflict target is irrelevant for an unmapped type) so the test
     * exercises exactly the dispatcher's unmapped-type branch. Backoff is 0
     * so three dispatchOnce() passes exhaust the budget deterministically;
     * the POC topology is untouched because nothing is ever sent.
     */
    @Test
    @Order(13)
    void unmappedMessageTypeIsNeverPublishedAndQuarantines() throws Exception {
        UUID fact = UUID.fromString("00000000-0000-0000-0000-000000000901");
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", "com.evplatform.station.unknown.v1");
        envelope.put("source", "//station-operations-service");
        envelope.put("id", fact.toString());
        envelope.set("data", MAPPER.createObjectNode());

        try {
            jdbc(RUNTIME).sql("""
                            INSERT INTO station_operations.outbox_message
                                (message_id, kind, message_type, aggregate_type, aggregate_ref,
                                 aggregate_version, workflow_ref, correlation_id, causation_id,
                                 classification, payload, available_at)
                            VALUES (?, 'EVENT', 'com.evplatform.station.unknown.v1', 'Station',
                                    ?, 0, NULL, ?, NULL, 'BUSINESS', ?::jsonb, now() - interval '1 second')
                            """)
                    .param(fact)
                    .param(fact)
                    .param(fact)
                    .param(envelope.toString())
                    .update();

            // backoff 0 → every pass is immediately due again
            OutboxDispatcher dispatcher = new OutboxDispatcher(
                    jdbc(RUNTIME), rabbitTemplate(RABBIT.getHost(), RABBIT.getMappedPort(5672)),
                    3, 0, 2000, 30);

            // pass 1: unmapped → attempt 1, still PENDING, never published
            assertEquals(0, dispatcher.dispatchOnce(),
                    "an unmapped message type must not be published");
            assertEquals("PENDING", rowState(fact),
                    "pass 1 must leave the unmapped row PENDING");
            assertEquals(1, outboxCount("message_id = '" + fact
                            + "' AND attempt_count = 1 AND failure_category = 'DATASCHEMA_UNMAPPED'"),
                    "pass 1 must record attempt 1 with the DATASCHEMA_UNMAPPED category");

            // pass 2: attempt 2, still PENDING
            dispatcher.dispatchOnce();
            assertEquals("PENDING", rowState(fact), "pass 2 must leave the row PENDING");
            assertEquals(1, outboxCount("message_id = '" + fact
                            + "' AND attempt_count = 2 AND failure_category = 'DATASCHEMA_UNMAPPED'"),
                    "pass 2 must record attempt 2 with the DATASCHEMA_UNMAPPED category");

            // pass 3: budget exhausted → QUARANTINED, never PUBLISHED
            dispatcher.dispatchOnce();
            assertEquals("QUARANTINED", rowState(fact),
                    "exhausted retries must quarantine the unmapped row");
            assertEquals(1, outboxCount("message_id = '" + fact
                            + "' AND attempt_count = 3 AND failure_category = 'DATASCHEMA_UNMAPPED'"),
                    "quarantined row records three DATASCHEMA_UNMAPPED attempts");
            assertEquals(0, outboxCount("message_id = '" + fact + "' AND state = 'PUBLISHED'"),
                    "the unmapped fact must never reach PUBLISHED");
        } finally {
            deleteOutboxRow(fact);
        }
    }

    private static String scalar(String sql) throws Exception {
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
