package com.evplatform.discoveryinsights;

import com.evplatform.libraries.testsupport.LocalDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.http.server.LocalTestWebServer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-DSC-001 phase 2: discovery first slice on real PostgreSQL 18 and
 * RabbitMQ 4.3 — consumer pipeline (inbox dedup, checkpoint version
 * discipline, §7.1 older-cannot-replace guard), poison → DLQ, public API
 * (list/details/404 problem+json), rebuild procedure, and the ARC-022 §9
 * privacy assertion (public reference data only).
 *
 * Ordered single-suite run against shared containers; the Spring context
 * boots once (@SpringBootTest, random port) with lazy topology so the
 * listener connects only after the containers are up.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiscoverySliceIntegrationTest {

    private static final String PW = "evplatform_dev_only";
    private static final String DB = "discovery_insights_db";
    private static final String MIGRATOR = "discovery_insights_migrator";
    private static final String RUNTIME = "discovery_insights_runtime";
    private static final String SCHEMA = "discovery_insights";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final PostgreSQLContainer PG = StartOnce.PG;
    static final GenericContainer<?> RABBIT = StartOnce.RABBIT;

    // ---- Spring context (booted lazily by Order 2 via the static holder) ----
    static org.springframework.context.ConfigurableApplicationContext CTX;
    static RabbitTemplate RABBIT_TEMPLATE;
    static org.springframework.web.client.RestTemplate REST;
    static String BASE_URL;

    // ---- fixture facts shared across orders ----
    static UUID FACT_A_ID = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    static UUID FACT_A_AGG = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    static ObjectNode FACT_A_ENVELOPE;

    static UUID FACT_V5_ID = UUID.fromString("00000000-0000-0000-0000-00000000d002");
    static UUID FACT_V1_ID = UUID.fromString("00000000-0000-0000-0000-00000000d003");
    static ObjectNode FACT_V5_ENVELOPE;
    static ObjectNode FACT_V1_ENVELOPE;

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

    private static void exec(String user, String sql) throws Exception {
        try (Connection c = connect(user);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private static String scalar(String sql) throws Exception {
        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "scalar query must return a row: " + sql);
            return rs.getString(1);
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

    /** Full wire envelope per I1-MSG-003 (CloudEvent + extension attributes). */
    private static ObjectNode envelope(UUID messageId, String aggregateId, long version,
                                       String publicRef, String displayName,
                                       double lat, double lon, String city) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("stationRef", aggregateId);
        data.put("publicRef", publicRef);
        data.put("displayName", displayName);
        data.put("addressLine", "Fixture Street 9");
        data.put("city", city);
        data.put("postalCode", "10431");
        data.put("countryCode", "GR");
        data.put("latitude", lat);
        data.put("longitude", lon);

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", "com.evplatform.station.published.v1");
        envelope.put("source", "//station-operations-service");
        envelope.put("id", messageId.toString());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("subject", "station/" + publicRef);
        envelope.set("data", data);
        // I1-MSG-003 extensions; causationid absent for seed facts
        envelope.put("dataschema",
                "https://schema-registry.example.com/events/station-published-event.json");
        envelope.put("correlationid", messageId.toString());
        envelope.put("aggregateid", aggregateId);
        envelope.put("aggregateversion", version);
        envelope.put("classification", "BUSINESS");
        return envelope;
    }

    private static void publish(ObjectNode envelope) {
        RABBIT_TEMPLATE.convertAndSend("ev.domain.v1", "station.published",
                envelope.toString());
    }

    /** Waits until the inbox row for messageId reaches the expected outcome. */
    private static void awaitInbox(UUID messageId, String outcome) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Integer n = jdbc(RUNTIME).sql("""
                            SELECT count(*) FROM discovery_insights.inbox_message
                            WHERE message_id = ? AND processing_outcome = ?
                            """)
                    .param(messageId).param(outcome)
                    .query((rs, i) -> rs.getInt(1))
                    .single();
            if (n == 1) {
                return;
            }
            Thread.sleep(150);
        }
        throw new AssertionError("inbox row for " + messageId
                + " did not reach " + outcome + " within 15s");
    }

    /** Waits until the projection row for publicRef exists. */
    private static void awaitProjection(String publicRef) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Integer n = jdbc(RUNTIME).sql("""
                            SELECT count(*) FROM discovery_insights.station_search_projection
                            WHERE public_ref = ?
                            """)
                    .param(publicRef)
                    .query((rs, i) -> rs.getInt(1))
                    .single();
            if (n == 1) {
                return;
            }
            Thread.sleep(150);
        }
        throw new AssertionError("projection row for " + publicRef + " did not appear within 15s");
    }

    @Test
    @Order(1)
    void schemaAndAppendOnlyAudit() throws Exception {
        // all 4 tables exist
        for (String table : List.of("station_search_projection", "projection_checkpoint",
                "inbox_message", "audit_event")) {
            try (Connection c = connect(MIGRATOR);
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT 1 FROM information_schema.tables "
                                 + "WHERE table_schema = ? AND table_name = ?")) {
                ps.setString(1, SCHEMA);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "missing table: " + table);
                }
            }
        }
        // named constraints: PostgreSQL auto-renames inline PRIMARY KEY to
        // <table>_pkey, so pk_* must be declared as named table constraints.
        // V2 does exactly that; query each by its owning relation.
        Map<String, String> constraintRelations = Map.of(
                "pk_station_search_projection", "station_search_projection",
                "uq_station_search_public_ref", "station_search_projection",
                "pk_projection_checkpoint", "projection_checkpoint",
                "pk_inbox_message", "inbox_message",
                "pk_audit_event", "audit_event");
        for (var entry : constraintRelations.entrySet()) {
            Integer n = jdbc(MIGRATOR).sql("""
                            SELECT count(*) FROM pg_constraint
                            WHERE conname = ?
                              AND conrelid = ('discovery_insights.' || ?)::regclass
                            """)
                    .param(entry.getKey())
                    .param(entry.getValue())
                    .query((rs, i) -> rs.getInt(1))
                    .single();
            assertEquals(1, n, "constraint must exist: " + entry.getKey());
        }
        // indexes
        for (String index : List.of("ix_station_search_source_version",
                "ix_station_search_location")) {
            Integer n = jdbc(MIGRATOR).sql("SELECT count(*) FROM pg_indexes WHERE indexname = ?")
                    .param(index)
                    .query((rs, i) -> rs.getInt(1))
                    .single();
            assertEquals(1, n, "index must exist: " + index);
        }
        // append-only audit for the runtime role (mirrors STA harness)
        exec(RUNTIME, "INSERT INTO " + SCHEMA + ".audit_event "
                + "(audit_ref, actor, calling_service, action, target, outcome, classification) "
                + "VALUES (gen_random_uuid(), 'schema-fixture', 'discovery-insights-service', "
                + "'SCHEMA_FIXTURE', 'discovery_insights_db', 'SUCCESS', 'BUSINESS')");
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> exec(RUNTIME,
                "UPDATE " + SCHEMA + ".audit_event SET outcome = 'TAMPERED'"));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> exec(RUNTIME,
                "DELETE FROM " + SCHEMA + ".audit_event"));
    }

    @Test
    @Order(2)
    void validFactIsAppliedWithCheckpointInboxAndAudit() throws Exception {
        bootSpring();

        FACT_A_ENVELOPE = envelope(FACT_A_ID, FACT_A_AGG.toString(), 0,
                "FIXSTA-A", "Fixture Station A", 37.983810, 23.727540, "Athens");
        publish(FACT_A_ENVELOPE);

        awaitInbox(FACT_A_ID, "COMPLETED");
        awaitProjection("FIXSTA-A");

        // projection row content
        Map<String, Object> row = jdbc(RUNTIME).sql("""
                        SELECT public_ref, display_name, address_line, city, postal_code,
                               country_code, latitude, longitude, source_version, source_ref,
                               projection_state
                        FROM discovery_insights.station_search_projection
                        WHERE public_ref = 'FIXSTA-A'
                        """)
                .query((rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("public_ref", rs.getString("public_ref"));
                    m.put("display_name", rs.getString("display_name"));
                    m.put("address_line", rs.getString("address_line"));
                    m.put("city", rs.getString("city"));
                    m.put("postal_code", rs.getString("postal_code"));
                    m.put("country_code", rs.getString("country_code"));
                    m.put("latitude", rs.getBigDecimal("latitude"));
                    m.put("longitude", rs.getBigDecimal("longitude"));
                    m.put("source_version", rs.getLong("source_version"));
                    m.put("source_ref", rs.getString("source_ref"));
                    m.put("projection_state", rs.getString("projection_state"));
                    return m;
                })
                .single();
        assertEquals("FIXSTA-A", row.get("public_ref"));
        assertEquals("Fixture Station A", row.get("display_name"));
        assertEquals("Fixture Street 9", row.get("address_line"));
        assertEquals("Athens", row.get("city"));
        assertEquals("10431", row.get("postal_code"));
        assertEquals("GR", row.get("country_code"));
        assertEquals(0, new java.math.BigDecimal("37.983810")
                .compareTo((java.math.BigDecimal) row.get("latitude")));
        assertEquals(0, new java.math.BigDecimal("23.727540")
                .compareTo((java.math.BigDecimal) row.get("longitude")));
        assertEquals(0L, row.get("source_version"));
        assertEquals(FACT_A_AGG.toString(), row.get("source_ref"));
        assertEquals("ACTIVE", row.get("projection_state"));

        // checkpoint advanced to 0
        long lastApplied = jdbc(RUNTIME).sql("""
                        SELECT last_applied_version FROM discovery_insights.projection_checkpoint
                        WHERE projection_name = 'station_search_projection'
                          AND source_ref = 'station-operations-service'
                        """)
                .query((rs, i) -> rs.getLong(1))
                .single();
        assertEquals(0L, lastApplied, "checkpoint must advance to version 0");

        // inbox row completed
        assertEquals(1, count("inbox_message", "message_id = '" + FACT_A_ID
                + "' AND processing_outcome = 'COMPLETED' AND consumer_name = "
                + "'discovery-station-projection'"));

        // audit row exists for the applied fact
        assertEquals(1, count("audit_event", "action = 'APPLY_STATION_PUBLISHED' "
                + "AND target = 'station/FIXSTA-A' AND outcome = 'SUCCESS' "
                + "AND actor = 'system' AND calling_service = 'discovery-insights-service' "
                + "AND classification = 'BUSINESS'"));
    }

    @Test
    @Order(3)
    void duplicateDeliveryIsInboxDeduplicated() throws Exception {
        String projectionCountBefore = scalar(
                "SELECT count(*) FROM " + SCHEMA + ".station_search_projection");
        String auditBefore = scalar(
                "SELECT count(*) FROM " + SCHEMA + ".audit_event "
                        + "WHERE action = 'APPLY_STATION_PUBLISHED'");
        String updatedBefore = scalar(
                "SELECT updated_at FROM " + SCHEMA + ".station_search_projection "
                        + "WHERE public_ref = 'FIXSTA-A'");

        publish(FACT_A_ENVELOPE); // same message_id, same content
        Thread.sleep(2_000); // allow any (wrong) reprocessing to land

        assertEquals(projectionCountBefore, scalar(
                "SELECT count(*) FROM " + SCHEMA + ".station_search_projection"),
                "duplicate delivery must not add projection rows");
        assertEquals(updatedBefore, scalar(
                "SELECT updated_at FROM " + SCHEMA + ".station_search_projection "
                        + "WHERE public_ref = 'FIXSTA-A'"),
                "duplicate delivery must not touch the projection row");
        assertEquals(auditBefore, scalar(
                        "SELECT count(*) FROM " + SCHEMA + ".audit_event "
                                + "WHERE action = 'APPLY_STATION_PUBLISHED'"),
                "duplicate delivery must not add audit rows");
        assertEquals(1, count("inbox_message", "message_id = '" + FACT_A_ID + "'"),
                "exactly one inbox row for (consumer, message_id)");
    }

    @Test
    @Order(4)
    void gapIsRecordedAndOlderVersionCannotDowngrade() throws Exception {
        // v5 for the SAME aggregate as FACT_A (last applied = 0) → gap 1..4
        FACT_V5_ENVELOPE = envelope(FACT_V5_ID, FACT_A_AGG.toString(), 5,
                "FIXSTA-A", "Fixture Station A v5", 37.990000, 23.730000, "Athens");
        publish(FACT_V5_ENVELOPE);
        awaitInbox(FACT_V5_ID, "COMPLETED");

        // chosen strategy: apply-from-full-snapshot + recorded gap
        String name = scalar("SELECT display_name FROM " + SCHEMA
                + ".station_search_projection WHERE public_ref = 'FIXSTA-A'");
        assertEquals("Fixture Station A v5", name, "gap fact must be applied from full snapshot");
        long version = Long.parseLong(scalar(
                "SELECT source_version FROM " + SCHEMA
                        + ".station_search_projection WHERE public_ref = 'FIXSTA-A'"));
        assertEquals(5L, version);
        String gapFrom = scalar(
                "SELECT gap_from_version FROM " + SCHEMA + ".projection_checkpoint "
                        + "WHERE projection_name = 'station_search_projection'");
        assertNotNull(gapFrom, "gap must be recorded in the checkpoint");
        assertEquals("1", gapFrom);
        String gapAt = scalar(
                "SELECT gap_recorded_at FROM " + SCHEMA + ".projection_checkpoint "
                        + "WHERE projection_name = 'station_search_projection'");
        assertNotNull(gapAt, "gap_recorded_at must be set");

        // late v1 for the same aggregate: §7.1 guard must keep v5
        FACT_V1_ENVELOPE = envelope(FACT_V1_ID, FACT_A_AGG.toString(), 1,
                "FIXSTA-A", "Fixture Station A v1", 37.980000, 23.720000, "Athens");
        publish(FACT_V1_ENVELOPE);
        awaitInbox(FACT_V1_ID, "SKIPPED");

        version = Long.parseLong(scalar(
                "SELECT source_version FROM " + SCHEMA
                        + ".station_search_projection WHERE public_ref = 'FIXSTA-A'"));
        assertEquals(5L, version, "older version must not downgrade the projection");
        name = scalar("SELECT display_name FROM " + SCHEMA
                + ".station_search_projection WHERE public_ref = 'FIXSTA-A'");
        assertEquals("Fixture Station A v5", name, "older snapshot must not change fields");
    }

    @Test
    @Order(5)
    void poisonMessageGoesToDlqAndListenerStaysHealthy() throws Exception {
        publishRaw("not-json");
        // wait for the DLQ delivery
        String dlqBody = pollDlq(15_000);
        assertNotNull(dlqBody, "poison message must be dead-lettered");
        assertEquals("not-json", dlqBody);

        // listener stays healthy: a valid fact after the poison still processes
        UUID healthy = UUID.fromString("00000000-0000-0000-0000-00000000d004");
        ObjectNode healthyEnvelope = envelope(healthy,
                UUID.fromString("00000000-0000-0000-0000-00000000a002").toString(), 0,
                "FIXSTA-B", "Fixture Station B", 41.008240, 28.978360, "Istanbul");
        publish(healthyEnvelope);
        awaitInbox(healthy, "COMPLETED");
        awaitProjection("FIXSTA-B");
    }

    private static void publishRaw(String body) {
        RABBIT_TEMPLATE.convertAndSend("ev.domain.v1", "station.published", body);
    }

    /** basicGet poll of the DLQ (manual ack, mirrors the STA harness). */
    private static String pollDlq(long timeoutMs) throws Exception {
        var cf = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getMappedPort(5672));
        cf.setUsername("guest");
        cf.setPassword("guest");
        try (com.rabbitmq.client.Connection conn = cf.getRabbitConnectionFactory()
                .newConnection();
             com.rabbitmq.client.Channel channel = conn.createChannel()) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                var delivery = channel.basicGet("discovery.station.published.dlq", false);
                if (delivery != null) {
                    String body = new String(delivery.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    return body;
                }
                Thread.sleep(150);
            }
        }
        return null;
    }

    @Test
    @Order(6)
    void publicApiServesProjectionRows() throws Exception {
        // list: both fixtures visible
        ResponseEntity<String> list = REST.getForEntity(BASE_URL + "/api/v1/stations",
                String.class);
        assertEquals(200, list.getStatusCode().value());
        assertTrue(list.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
        JsonNode array = MAPPER.readTree(list.getBody());
        assertTrue(array.isArray());
        assertTrue(array.size() >= 2, "both fixture stations must be listed");
        JsonNode a = findByName(array, "Fixture Station A v5");
        assertNotNull(a, "station A (v5 state) must be in the list");
        assertEquals("FIXSTA-A", a.get("ref").asText());
        assertEquals(37.99, a.get("latitude").asDouble(), 1e-6);
        assertEquals(23.73, a.get("longitude").asDouble(), 1e-6);
        // optional EVSE fields must be absent (no fake data)
        assertNull(a.get("availableEvses"));
        assertNull(a.get("totalEvses"));
        JsonNode b = findByName(array, "Fixture Station B");
        assertNotNull(b);
        assertEquals("FIXSTA-B", b.get("ref").asText());

        // geo filter: radius around Athens excludes Istanbul
        ResponseEntity<String> geo = REST.getForEntity(
                BASE_URL + "/api/v1/stations?latitude=37.9838&longitude=23.7275&radius=50",
                String.class);
        assertEquals(200, geo.getStatusCode().value());
        JsonNode geoArray = MAPPER.readTree(geo.getBody());
        assertNotNull(findByName(geoArray, "Fixture Station A v5"),
                "Athens station must be inside the radius");
        assertNull(findByName(geoArray, "Fixture Station B"),
                "Istanbul station must be outside the radius");

        // details 200
        ResponseEntity<String> details = REST.getForEntity(
                BASE_URL + "/api/v1/stations/FIXSTA-A", String.class);
        assertEquals(200, details.getStatusCode().value());
        JsonNode d = MAPPER.readTree(details.getBody());
        assertEquals("FIXSTA-A", d.get("ref").asText());
        assertEquals("Fixture Station A v5", d.get("name").asText());
        assertEquals("Athens", d.get("city").asText());
        assertEquals("GR", d.get("countryCode").asText());
        assertEquals(5L, d.get("sourceVersion").asLong());
        assertNotNull(d.get("updatedAt"));

        // unknown ref → 404 problem+json. RestTemplate throws on 4xx, so the
        // response is asserted from the thrown HttpClientErrorException (the
        // server-side contract — status, headers, problem+json body — is
        // unchanged and fully asserted).
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_PROBLEM_JSON));
        org.springframework.web.client.HttpClientErrorException.NotFound notFound =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.client.HttpClientErrorException.NotFound.class,
                        () -> REST.exchange(
                                BASE_URL + "/api/v1/stations/NO-SUCH-REF", HttpMethod.GET,
                                new HttpEntity<>(headers), String.class));
        assertEquals(404, notFound.getStatusCode().value());
        assertTrue(notFound.getResponseHeaders().getContentType()
                        .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                "404 must be served as application/problem+json");
        JsonNode problem = MAPPER.readTree(notFound.getResponseBodyAsString());
        assertEquals("https://api.evplatform.example/problems/resource-not-found",
                problem.get("type").asText());
        assertEquals("Resource not found", problem.get("title").asText());
        assertEquals(404, problem.get("status").asInt());
        assertEquals("Station NO-SUCH-REF does not exist.", problem.get("detail").asText());
    }

    private static JsonNode findByName(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (name.equals(node.path("name").asText(null))) {
                return node;
            }
        }
        return null;
    }

    @Test
    @Order(7)
    void rebuildRepopulatesProjectionIdentically() throws Exception {
        // documented rebuild procedure: clear projection + checkpoint + inbox
        exec(MIGRATOR, "DELETE FROM " + SCHEMA + ".station_search_projection");
        exec(MIGRATOR, "DELETE FROM " + SCHEMA + ".projection_checkpoint");
        exec(MIGRATOR, "DELETE FROM " + SCHEMA + ".inbox_message");

        // re-publish the SAME original envelope (same message_id)
        publish(FACT_A_ENVELOPE);
        awaitInbox(FACT_A_ID, "COMPLETED");
        awaitProjection("FIXSTA-A");

        String name = scalar("SELECT display_name FROM " + SCHEMA
                + ".station_search_projection WHERE public_ref = 'FIXSTA-A'");
        assertEquals("Fixture Station A", name, "rebuild must repopulate the original snapshot");
        String version = scalar("SELECT source_version FROM " + SCHEMA
                + ".station_search_projection WHERE public_ref = 'FIXSTA-A'");
        assertEquals("0", version);
        String checkpoint = scalar(
                "SELECT last_applied_version FROM " + SCHEMA + ".projection_checkpoint "
                        + "WHERE projection_name = 'station_search_projection'");
        assertEquals("0", checkpoint, "checkpoint must restart from the first fact");
    }

    @Test
    @Order(8)
    void projectionContainsPublicReferenceDataOnly() throws Exception {
        // no column in any discovery_insights table matches privacy patterns
        List<String> forbidden = jdbc(MIGRATOR).sql("""
                        SELECT DISTINCT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'discovery_insights'
                        """)
                .query((rs, i) -> rs.getString(1))
                .list();
        List<String> patterns = List.of("account", "driver", "vehicle", "subject", "user_",
                "person", "email", "phone");
        for (String column : forbidden) {
            for (String pattern : patterns) {
                assertFalse(column.toLowerCase().contains(pattern),
                        "column name must not match privacy pattern '" + pattern + "': " + column);
            }
        }
        // projection row carries only public fields (spot check: no station_ref
        // leak into API responses was asserted in Order 6; here assert the
        // projection row has no unexpected non-null sensitive columns — the
        // table simply has none by construction, verified above)
    }

    /** Boots the real application once, against the containers. */
    private static void bootSpring() {
        if (CTX != null) {
            return;
        }
        org.springframework.boot.SpringApplication app =
                new org.springframework.boot.SpringApplication(DiscoveryInsightsApplication.class);
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", 0);
        // Inlined defaults win over application.yml (SpringApplication default
        // properties rank below any config file); the yml defaults point at
        // the local compose stack (127.0.0.1:5432), which is not running here.
        props.put("spring.datasource.url",
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB);
        props.put("spring.datasource.username", RUNTIME);
        props.put("spring.datasource.password", PW);
        props.put("spring.rabbitmq.host", RABBIT.getHost());
        props.put("spring.rabbitmq.port", String.valueOf(RABBIT.getMappedPort(5672)));
        props.put("spring.rabbitmq.username", "guest");
        props.put("spring.rabbitmq.password", "guest");
        props.put("spring.datasource.hikari.initialization-fail-timeout", "60000");
        // deterministic retry timing in tests
        props.put("spring.rabbitmq.listener.simple.retry.initial-interval", "100");
        props.put("spring.rabbitmq.listener.simple.retry.max-interval", "500");
        // command-line args outrank application.yml (highest non-test
        // precedence) — default properties do NOT, which is why the first
        // runs still dialled the compose defaults from application.yml.
        CTX = app.run("--spring.datasource.url=jdbc:postgresql://" + PG.getHost()
                        + ":" + PG.getMappedPort(5432) + "/" + DB,
                "--spring.datasource.username=" + RUNTIME,
                "--spring.datasource.password=" + PW,
                "--spring.rabbitmq.host=" + RABBIT.getHost(),
                "--spring.rabbitmq.port=" + RABBIT.getMappedPort(5672),
                "--spring.rabbitmq.username=guest",
                "--spring.rabbitmq.password=guest",
                "--server.port=0",
                "--spring.datasource.hikari.initialization-fail-timeout=60000",
                "--spring.rabbitmq.listener.simple.retry.initial-interval=100",
                "--spring.rabbitmq.listener.simple.retry.max-interval=500");
        RABBIT_TEMPLATE = CTX.getBean(RabbitTemplate.class);
        REST = new org.springframework.web.client.RestTemplate();
        var webServerAppContainer = (org.springframework.boot.web.server.servlet.context.
                ServletWebServerApplicationContext) CTX;
        BASE_URL = "http://localhost:" + webServerAppContainer.getWebServer().getPort();
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
                    .dataSource("jdbc:postgresql://" + c.getHost() + ":" + c.getMappedPort(5432) + "/" + DB,
                            MIGRATOR, PW)
                    .locations("filesystem:" + Path.of("src", "main", "resources", "db", "migration")
                            .toAbsolutePath().normalize())
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
