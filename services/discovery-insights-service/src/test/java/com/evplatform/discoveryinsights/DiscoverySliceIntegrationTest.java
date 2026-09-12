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
 * I1-DSC-001 phase 2 + I1-DSC-002 phase 2: discovery slice on real
 * PostgreSQL 18 and RabbitMQ 4.3 — consumer pipeline (inbox dedup,
 * checkpoint version discipline, §7.1 older-cannot-replace guard), poison →
 * DLQ, public API (list/details/404 problem+json), rebuild procedure, the
 * ARC-022 §9 privacy assertion, and the depth families (EVSE/connector/
 * tariff projections, per-family version gates, orphan handling, depth
 * filters, details enrichment).
 *
 * Ordered single-suite run against shared containers; the Spring context
 * boots once (@SpringBootTest, random port) with lazy topology so the
 * listener connects only after the containers are up.
 *
 * ORDER RENUMBERING DISCLOSURE (I1-DSC-002): the four depth-family tests
 * run BEFORE the existing API order so the depth projections are populated
 * when the extended API assertions execute. The original orders 6/7/8/9/10
 * keep their relative sequence and were renumbered 7/8/9/10/11 honestly —
 * no assertion was weakened or removed.
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

    // ---- Spring context (booted lazily by the first consumer test) ----
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

    // ---- depth-family fixture facts (I1-DSC-002) ----
    // stations ...0010 (SEEDSTA0001, Athens) and ...0011 (SEEDSTA0002,
    // Thessaloniki); EVSEs ...00a1/00a2 (station A) and ...00b1/00b2
    // (station B); connectorRef = nameUUIDFromBytes((uid + "|" + type))
    // — the same derivation the STA seed uses, so the facts reference
    // connectors the seed itself persists.
    static final UUID DEPTH_STA_A = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID DEPTH_STA_B = UUID.fromString("00000000-0000-0000-0000-000000000011");
    static final UUID EVSE_A1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID EVSE_A2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final UUID EVSE_B1 = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID EVSE_B2 = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final String UID_A1 = "GR*SEED*A1";
    static final String UID_A2 = "GR*SEED*A2";
    static final String UID_B1 = "GR*SEED*B1";
    static final String UID_B2 = "GR*SEED*B2";
    static final UUID TARIFF_REF = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    static final UUID TARIFF_VERSION_REF = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    static UUID connectorRef(String uid, String type) {
        return UUID.nameUUIDFromBytes((uid + "|" + type).getBytes());
    }

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

    /** evse-configuration-changed envelope (full extension set). */
    private static ObjectNode evseEnvelope(UUID messageId, UUID evseRef, UUID stationRef,
                                           String evseUid, long version) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("evseRef", evseRef.toString());
        data.put("stationRef", stationRef.toString());
        data.put("evseUid", evseUid);

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", "com.evplatform.station.evse-configuration-changed.v1");
        envelope.put("source", "//station-operations-service");
        envelope.put("id", messageId.toString());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("subject", "evse/" + evseUid);
        envelope.set("data", data);
        envelope.put("dataschema",
                "https://schema-registry.example.com/events/evse-configuration-changed-event.json");
        envelope.put("correlationid", messageId.toString());
        envelope.put("aggregateid", evseRef.toString());
        envelope.put("aggregateversion", version);
        envelope.put("classification", "BUSINESS");
        return envelope;
    }

    /** connector-configuration-changed envelope (full extension set). */
    private static ObjectNode connectorEnvelope(UUID messageId, UUID connectorRef,
                                                UUID evseRef, String connectorType,
                                                int maxPowerW, long version) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("connectorRef", connectorRef.toString());
        data.put("evseRef", evseRef.toString());
        data.put("connectorType", connectorType);
        data.put("maxPowerW", maxPowerW);

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", "com.evplatform.station.connector-configuration-changed.v1");
        envelope.put("source", "//station-operations-service");
        envelope.put("id", messageId.toString());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("subject", "connector/" + connectorRef);
        envelope.set("data", data);
        envelope.put("dataschema",
                "https://schema-registry.example.com/events/connector-configuration-changed-event.json");
        envelope.put("correlationid", messageId.toString());
        envelope.put("aggregateid", connectorRef.toString());
        envelope.put("aggregateversion", version);
        envelope.put("classification", "BUSINESS");
        return envelope;
    }

    /** tariff-published envelope (full extension set). */
    private static ObjectNode tariffEnvelope(UUID messageId, long version) {
        ObjectNode energy = MAPPER.createObjectNode();
        energy.put("componentKind", "ENERGY_PER_KWH");
        energy.put("unit", "KWH");
        energy.put("amountMinor", 480);
        ObjectNode occupancy = MAPPER.createObjectNode();
        occupancy.put("componentKind", "OCCUPANCY_PER_MINUTE");
        occupancy.put("unit", "MINUTE");
        occupancy.put("amountMinor", 10);
        ObjectNode data = MAPPER.createObjectNode();
        data.put("tariffRef", TARIFF_REF.toString());
        data.put("tariffVersionRef", TARIFF_VERSION_REF.toString());
        data.put("versionNumber", 1);
        data.put("currency", "EUR");
        data.set("components", MAPPER.createArrayNode().add(energy).add(occupancy));

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", "com.evplatform.station.tariff-published.v1");
        envelope.put("source", "//station-operations-service");
        envelope.put("id", messageId.toString());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("subject", "tariff/" + TARIFF_VERSION_REF);
        envelope.set("data", data);
        envelope.put("dataschema",
                "https://schema-registry.example.com/events/tariff-published-event.json");
        envelope.put("correlationid", messageId.toString());
        envelope.put("aggregateid", TARIFF_VERSION_REF.toString());
        envelope.put("aggregateversion", version);
        envelope.put("classification", "BUSINESS");
        return envelope;
    }

    private static void publish(ObjectNode envelope) {
        RABBIT_TEMPLATE.convertAndSend("ev.domain.v1", "station.published",
                envelope.toString());
    }

    /**
     * MINOR-4: publishes with an explicit routing key so the depth-family
     * facts positively exercise their REAL topology bindings
     * (station.evse-configuration-changed / station.connector-configuration-
     * changed / station.tariff-published) instead of riding the
     * station.published binding. Station facts keep station.published
     * (the single-argument overload).
     */
    private static void publish(ObjectNode envelope, String routingKey) {
        RABBIT_TEMPLATE.convertAndSend("ev.domain.v1", routingKey, envelope.toString());
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

    /** Waits until the EVSE projection row for evseRef exists. */
    private static void awaitEvse(UUID evseRef) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Integer n = jdbc(RUNTIME).sql("""
                            SELECT count(*) FROM discovery_insights.evse_search_projection
                            WHERE evse_ref = ?
                            """)
                    .param(evseRef)
                    .query((rs, i) -> rs.getInt(1))
                    .single();
            if (n == 1) {
                return;
            }
            Thread.sleep(150);
        }
        throw new AssertionError("evse projection row for " + evseRef + " did not appear within 15s");
    }

    @Test
    @Order(1)
    void schemaAndAppendOnlyAudit() throws Exception {
        // all 8 tables exist (4 base + 3 depth + audit)
        for (String table : List.of("station_search_projection", "projection_checkpoint",
                "inbox_message", "audit_event", "evse_search_projection",
                "connector_search_projection", "tariff_public_projection")) {
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
        // V2/V3 do exactly that; query each by its owning relation.
        Map<String, String> constraintRelations = Map.ofEntries(
                Map.entry("pk_station_search_projection", "station_search_projection"),
                Map.entry("uq_station_search_public_ref", "station_search_projection"),
                Map.entry("pk_projection_checkpoint", "projection_checkpoint"),
                Map.entry("pk_inbox_message", "inbox_message"),
                Map.entry("ck_inbox_processing_outcome", "inbox_message"),
                Map.entry("pk_audit_event", "audit_event"),
                Map.entry("pk_evse_search_projection", "evse_search_projection"),
                Map.entry("uq_evse_search_uid", "evse_search_projection"),
                Map.entry("ck_evse_search_state", "evse_search_projection"),
                Map.entry("pk_connector_search_projection", "connector_search_projection"),
                Map.entry("ck_connector_power", "connector_search_projection"),
                Map.entry("ck_connector_search_state", "connector_search_projection"),
                Map.entry("pk_tariff_public_projection", "tariff_public_projection"),
                Map.entry("ck_tariff_public_state", "tariff_public_projection"));
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
        // indexes (MINOR-5: V3 amended pre-application with
        // ix_tariff_public_source_version — 7 depth/first-slice indexes now)
        for (String index : List.of("ix_station_search_source_version",
                "ix_station_search_location",
                "ix_evse_search_station", "ix_evse_search_source_version",
                "ix_connector_search_evse", "ix_connector_search_type_power",
                "ix_tariff_public_tariff", "ix_tariff_public_source_version")) {
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
        // MINOR-6: the runtime role must not hold CREATE on the schema —
        // DDL belongs to the migrator role only (mirrors STA's
        // runtimeRoleCannotCreateTables pattern)
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> exec(RUNTIME,
                "CREATE TABLE " + SCHEMA + ".runtime_ddl_probe (id int)"));
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

        // gap evidence is PRESERVED on the completed inbox row: a gap-apply
        // fact completes with the VERSION_GAP failure_category kept (not
        // NULLed) — the completed inbox row is the durable gap trace once the
        // checkpoint's gap fields are overwritten by the next in-sequence fact
        String gapCategory = scalar(
                "SELECT failure_category FROM " + SCHEMA + ".inbox_message "
                        + "WHERE message_id = '" + FACT_V5_ID + "'");
        assertEquals("VERSION_GAP", gapCategory,
                "completed gap-apply inbox row must preserve failure_category=VERSION_GAP");
        String gapOutcome = scalar(
                "SELECT processing_outcome FROM " + SCHEMA + ".inbox_message "
                        + "WHERE message_id = '" + FACT_V5_ID + "'");
        assertEquals("COMPLETED", gapOutcome);

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

    @Test
    @Order(6)
    void depthFactsApplyInOrder() throws Exception {
        // Ordered facts mirroring the STA seed emission order: the two depth
        // station snapshots first (publicRefs DEPTHSTA-A/DEPTHSTA-B —
        // DISTINCT from FIXSTA-A/FIXSTA-B: uq_station_search_public_ref
        // forbids re-parenting a public_ref to another station_ref), then
        // EVSEs, then connectors, then the tariff. 15 envelopes in total
        // (2 parent stations + the 13 depth-family facts: 4 EVSE + 8
        // connector + 1 tariff).
        UUID staAId = UUID.fromString("00000000-0000-0000-0000-00000000e001");
        UUID staBId = UUID.fromString("00000000-0000-0000-0000-00000000e002");
        UUID evseA1Id = UUID.fromString("00000000-0000-0000-0000-00000000e003");
        UUID evseA2Id = UUID.fromString("00000000-0000-0000-0000-00000000e004");
        UUID evseB1Id = UUID.fromString("00000000-0000-0000-0000-00000000e005");
        UUID evseB2Id = UUID.fromString("00000000-0000-0000-0000-00000000e006");
        UUID conA1DcId = UUID.fromString("00000000-0000-0000-0000-00000000e007");
        UUID conA1AcId = UUID.fromString("00000000-0000-0000-0000-00000000e008");
        UUID conA2DcId = UUID.fromString("00000000-0000-0000-0000-00000000e009");
        UUID conA2AcId = UUID.fromString("00000000-0000-0000-0000-00000000e00a");
        UUID conB1DcId = UUID.fromString("00000000-0000-0000-0000-00000000e00b");
        UUID conB1AcId = UUID.fromString("00000000-0000-0000-0000-00000000e00c");
        UUID tariffId = UUID.fromString("00000000-0000-0000-0000-00000000e00d");

        // parent stations first — the EVSE orphan check requires their rows
        ObjectNode staA = envelope(staAId, DEPTH_STA_A.toString(), 0,
                "DEPTHSTA-A", "Depth Fixture Station A", 37.983810, 23.727540, "Athens");
        publish(staA);
        ObjectNode staB = envelope(staBId, DEPTH_STA_B.toString(), 0,
                "DEPTHSTA-B", "Depth Fixture Station B", 40.640060, 22.944420, "Thessaloniki");
        publish(staB);
        awaitInbox(staAId, "COMPLETED");
        awaitInbox(staBId, "COMPLETED");

        publish(evseEnvelope(evseA1Id, EVSE_A1, DEPTH_STA_A, UID_A1, 0),
                "station.evse-configuration-changed");
        publish(evseEnvelope(evseA2Id, EVSE_A2, DEPTH_STA_A, UID_A2, 0),
                "station.evse-configuration-changed");
        publish(evseEnvelope(evseB1Id, EVSE_B1, DEPTH_STA_B, UID_B1, 0),
                "station.evse-configuration-changed");
        publish(evseEnvelope(evseB2Id, EVSE_B2, DEPTH_STA_B, UID_B2, 0),
                "station.evse-configuration-changed");
        awaitInbox(evseA1Id, "COMPLETED");
        awaitInbox(evseA2Id, "COMPLETED");
        awaitInbox(evseB1Id, "COMPLETED");
        awaitInbox(evseB2Id, "COMPLETED");

        publish(connectorEnvelope(conA1DcId, connectorRef(UID_A1, "CCS"), EVSE_A1, "CCS", 150_000, 0),
                "station.connector-configuration-changed");
        publish(connectorEnvelope(conA1AcId, connectorRef(UID_A1, "TYPE2"), EVSE_A1, "TYPE2", 22_000, 0),
                "station.connector-configuration-changed");
        publish(connectorEnvelope(conA2DcId, connectorRef(UID_A2, "CCS"), EVSE_A2, "CCS", 150_000, 0),
                "station.connector-configuration-changed");
        publish(connectorEnvelope(conA2AcId, connectorRef(UID_A2, "TYPE2"), EVSE_A2, "TYPE2", 22_000, 0),
                "station.connector-configuration-changed");
        publish(connectorEnvelope(conB1DcId, connectorRef(UID_B1, "CCS"), EVSE_B1, "CCS", 150_000, 0),
                "station.connector-configuration-changed");
        publish(connectorEnvelope(conB1AcId, connectorRef(UID_B1, "TYPE2"), EVSE_B1, "TYPE2", 22_000, 0),
                "station.connector-configuration-changed");
        publish(connectorEnvelope(UUID.fromString("00000000-0000-0000-0000-00000000e00e"),
                connectorRef(UID_B2, "CCS"), EVSE_B2, "CCS", 150_000, 0),
                "station.connector-configuration-changed");
        publish(connectorEnvelope(UUID.fromString("00000000-0000-0000-0000-00000000e00f"),
                connectorRef(UID_B2, "TYPE2"), EVSE_B2, "TYPE2", 22_000, 0),
                "station.connector-configuration-changed");
        awaitInbox(conA1DcId, "COMPLETED");
        awaitInbox(conA1AcId, "COMPLETED");
        awaitInbox(conA2DcId, "COMPLETED");
        awaitInbox(conA2AcId, "COMPLETED");
        awaitInbox(conB1DcId, "COMPLETED");
        awaitInbox(conB1AcId, "COMPLETED");

        publish(tariffEnvelope(tariffId, 0), "station.tariff-published");
        awaitInbox(tariffId, "COMPLETED");
        awaitEvse(EVSE_B2);

        // the 13 depth-family inbox rows COMPLETED (plus the 2 parents)
        for (UUID id : List.of(evseA1Id, evseA2Id, evseB1Id, evseB2Id,
                conA1DcId, conA1AcId, conA2DcId, conA2AcId, conB1DcId, conB1AcId, tariffId)) {
            awaitInbox(id, "COMPLETED");
        }

        // 4 evse rows, 8 connector rows, 1 tariff row
        assertEquals(4, count("evse_search_projection", null));
        assertEquals(8, count("connector_search_projection", null));
        assertEquals(1, count("tariff_public_projection", null));

        // audit actions per family
        assertEquals(4, count("audit_event", "action = 'APPLY_EVSE_CONFIGURATION'"));
        assertEquals(8, count("audit_event", "action = 'APPLY_CONNECTOR_CONFIGURATION'"));
        assertEquals(1, count("audit_event", "action = 'APPLY_TARIFF_PUBLISHED' "
                + "AND target = 'tariff/" + TARIFF_VERSION_REF + "'"));

        // evse row content
        Map<String, Object> evseRow = jdbc(RUNTIME).sql("""
                        SELECT evse_uid, station_ref, source_version, projection_state
                        FROM discovery_insights.evse_search_projection
                        WHERE evse_ref = ?
                        """)
                .param(EVSE_A1)
                .query((rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("evse_uid", rs.getString("evse_uid"));
                    m.put("station_ref", rs.getString("station_ref"));
                    m.put("source_version", rs.getLong("source_version"));
                    m.put("projection_state", rs.getString("projection_state"));
                    return m;
                })
                .single();
        assertEquals(UID_A1, evseRow.get("evse_uid"));
        assertEquals(DEPTH_STA_A.toString(), evseRow.get("station_ref"));
        assertEquals(0L, evseRow.get("source_version"));
        assertEquals("ACTIVE", evseRow.get("projection_state"));

        // connector row content
        assertEquals(150_000, Integer.parseInt(scalar(
                "SELECT max_power_w FROM " + SCHEMA + ".connector_search_projection "
                        + "WHERE connector_ref = '" + connectorRef(UID_A1, "CCS") + "'")));
        assertEquals("CCS", scalar(
                "SELECT connector_type FROM " + SCHEMA + ".connector_search_projection "
                        + "WHERE connector_ref = '" + connectorRef(UID_A1, "CCS") + "'"));

        // tariff row content: components stored as the JSON array
        String components = scalar(
                "SELECT components::text FROM " + SCHEMA + ".tariff_public_projection "
                        + "WHERE tariff_version_ref = '" + TARIFF_VERSION_REF + "'");
        JsonNode componentsNode = MAPPER.readTree(components);
        assertTrue(componentsNode.isArray());
        assertEquals(2, componentsNode.size());
        assertEquals("ENERGY_PER_KWH", componentsNode.get(0).get("componentKind").asText());
        assertEquals(480, componentsNode.get(0).get("amountMinor").asInt());
        assertEquals("OCCUPANCY_PER_MINUTE", componentsNode.get(1).get("componentKind").asText());
        assertEquals(10, componentsNode.get(1).get("amountMinor").asInt());
        assertEquals("EUR", scalar(
                "SELECT currency FROM " + SCHEMA + ".tariff_public_projection "
                        + "WHERE tariff_version_ref = '" + TARIFF_VERSION_REF + "'"));
    }

    @Test
    @Order(7)
    void duplicateEvseFactHasSingleEffect() throws Exception {
        // republish the SAME evse envelope (same message_id): inbox dedup
        // must keep a single effect
        UUID evseA1Id = UUID.fromString("00000000-0000-0000-0000-00000000e003");
        int evseRowsBefore = count("evse_search_projection", null);
        int auditBefore = count("audit_event", "action = 'APPLY_EVSE_CONFIGURATION'");

        publish(evseEnvelope(evseA1Id, EVSE_A1, DEPTH_STA_A, UID_A1, 0),
                "station.evse-configuration-changed");
        Thread.sleep(2_000); // allow any (wrong) reprocessing to land

        assertEquals(evseRowsBefore, count("evse_search_projection", null),
                "duplicate EVSE fact must not add rows");
        assertEquals(auditBefore, count("audit_event", "action = 'APPLY_EVSE_CONFIGURATION'"),
                "duplicate EVSE fact must not add audit rows");
        assertEquals(1, count("inbox_message", "message_id = '" + evseA1Id + "'"),
                "exactly one inbox row for the duplicate EVSE fact");
    }

    @Test
    @Order(8)
    void outOfOrderEvseVersionsAreGuarded() throws Exception {
        // v2 first (applied is 0, so v2 is a gap; gap-apply advances to 2),
        // then late v1 → SKIPPED, guard keeps v2. The extra EVSE hangs off
        // DEPTH_STA_B (not A) so Order 13's totalEvses=2 for station A is
        // unaffected.
        UUID evseV2Id = UUID.fromString("00000000-0000-0000-0000-00000000e011");
        UUID evseV1Id = UUID.fromString("00000000-0000-0000-0000-00000000e012");
        UUID evseC1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

        publish(evseEnvelope(evseV2Id, evseC1, DEPTH_STA_B, "GR*SEED*C1", 2),
                "station.evse-configuration-changed");
        awaitInbox(evseV2Id, "COMPLETED");
        assertEquals(2L, Long.parseLong(scalar(
                "SELECT source_version FROM " + SCHEMA + ".evse_search_projection "
                        + "WHERE evse_ref = '" + evseC1 + "'")));

        publish(evseEnvelope(evseV1Id, evseC1, DEPTH_STA_B, "GR*SEED*C1", 1),
                "station.evse-configuration-changed");
        awaitInbox(evseV1Id, "SKIPPED");
        assertEquals(2L, Long.parseLong(scalar(
                "SELECT source_version FROM " + SCHEMA + ".evse_search_projection "
                        + "WHERE evse_ref = '" + evseC1 + "'")),
                "older EVSE version must not downgrade the projection");
    }

    @Test
    @Order(9)
    void orphanEvseFactFailsAndDeadLetters() throws Exception {
        // EVSE fact for a station with no projection row: ORPHAN_FACT →
        // FAILED inbox rows with attempt accounting → after the attempt
        // budget, requeue=false → DLQ; no evse row may exist.
        UUID orphanEvse = UUID.fromString("00000000-0000-0000-0000-0000000000c9");
        UUID orphanStation = UUID.fromString("00000000-0000-0000-0000-000000000099");
        UUID orphanId = UUID.fromString("00000000-0000-0000-0000-00000000e013");

        publish(evseEnvelope(orphanId, orphanEvse, orphanStation, "GR*SEED*ORPHAN", 0),
                "station.evse-configuration-changed");

        String dlqBody = pollDlq(30_000);
        assertNotNull(dlqBody, "orphan EVSE fact must eventually be dead-lettered");
        JsonNode dlqEnvelope = MAPPER.readTree(dlqBody);
        assertEquals("com.evplatform.station.evse-configuration-changed.v1",
                dlqEnvelope.get("type").asText());
        assertEquals(orphanId.toString(), dlqEnvelope.get("id").asText());
        assertEquals(0, count("evse_search_projection", "evse_ref = '" + orphanEvse + "'"),
                "orphan fact must not be projected");
        // the inbox row records the orphan failure
        String category = scalar(
                "SELECT failure_category FROM " + SCHEMA + ".inbox_message "
                        + "WHERE message_id = '" + orphanId + "'");
        assertEquals("ORPHAN_FACT", category);
    }

    @Test
    @Order(10)
    void aggregatePayloadMismatchGoesToDlq() throws Exception {
        // envelope aggregateid points at one station, payload stationRef at
        // another → poison-classified (AGGREGATE_PAYLOAD_MISMATCH) → DLQ;
        // no projection row may be created for the payload's stationRef.
        UUID mismatchId = UUID.fromString("00000000-0000-0000-0000-00000000d005");
        String aggB = "00000000-0000-0000-0000-00000000a002"; // station B's aggregate
        ObjectNode mismatched = envelope(mismatchId, aggB, 0,
                "FIXSTA-C", "Fixture Station C", 48.856600, 2.352200, "Paris");
        ((ObjectNode) mismatched.get("data")).put("stationRef",
                "00000000-0000-0000-0000-00000000a999"); // ≠ aggregateid
        publish(mismatched);

        String dlqBody = pollDlq(15_000);
        assertNotNull(dlqBody, "aggregate/payload mismatch must be dead-lettered");
        assertEquals(mismatched.toString(), dlqBody);
        assertEquals(0, count("station_search_projection", "public_ref = 'FIXSTA-C'"),
                "mismatched fact must not be projected");
    }

    @Test
    @Order(11)
    void malformedNumericPayloadGoesToDlq() throws Exception {
        // security finding 1 companion (documented current behavior; full
        // JSON-schema validation is the booked hardening follow-up): a
        // non-numeric latitude makes Jackson's decimalValue() coercion throw
        // inside the DB transaction → rollback → container retry exhausts →
        // reject → DLQ; no projection row may exist for the fact.
        UUID badNumericId = UUID.fromString("00000000-0000-0000-0000-00000000d006");
        String aggC = "00000000-0000-0000-0000-00000000a003";
        ObjectNode badNumeric = envelope(badNumericId, aggC, 0,
                "FIXSTA-D", "Fixture Station D", 0, 0, "Athens");
        ((ObjectNode) badNumeric.get("data")).put("latitude", "not-a-number");
        publish(badNumeric);

        String dlqBody = pollDlq(15_000);
        assertNotNull(dlqBody, "malformed numeric payload must be dead-lettered");
        assertEquals(badNumeric.toString(), dlqBody);
        assertEquals(0, count("station_search_projection", "public_ref = 'FIXSTA-D'"),
                "malformed fact must not be projected");
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
                var delivery = channel.basicGet("discovery.sta.domain.dlq", false);
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
    @Order(12)
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
        // totalEvses is populated on LIST (MAJOR-1): FIXSTA stations have no
        // EVSE facts published in this flow → 0 (honest per the test data);
        // availableEvses stays absent (no fake data)
        assertNull(a.get("availableEvses"));
        assertNotNull(a.get("totalEvses"), "totalEvses must be populated on list");
        assertEquals(0, a.get("totalEvses").asInt(),
                "FIXSTA-A has no EVSE facts → totalEvses must be 0, not absent");
        JsonNode b = findByName(array, "Fixture Station B");
        assertNotNull(b);
        assertEquals("FIXSTA-B", b.get("ref").asText());
        assertNotNull(b.get("totalEvses"));
        assertEquals(0, b.get("totalEvses").asInt(),
                "FIXSTA-B has no EVSE facts → totalEvses must be 0");

        // geo filter: radius around Athens excludes Istanbul
        ResponseEntity<String> geo = REST.getForEntity(
                BASE_URL + "/api/v1/stations?latitude=37.9838&longitude=23.7275&radius=50",
                String.class);
        assertEquals(200, geo.getStatusCode().value());
        JsonNode geoArray = MAPPER.readTree(geo.getBody());
        JsonNode geoA = findByName(geoArray, "Fixture Station A v5");
        assertNotNull(geoA, "Athens station must be inside the radius");
        assertEquals(0, geoA.get("totalEvses").asInt(),
                "the geo path must also populate totalEvses");
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

        // Privacy absence assertions (ARC-022 §9): the details response must
        // not expose the internal station identity. Checked explicitly here —
        // (a) no "stationRef" field anywhere in the body, (b) no UUID-shaped
        // value anywhere in the body (the DTOs carry no such field; the
        // depth view carries only the public evse uid). This asserts the
        // wire representation.
        String detailsBody = details.getBody();
        assertFalse(detailsBody.contains("\"stationRef\""),
                "details response must not contain a stationRef field");
        String uuidPattern = "\"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\"";
        assertFalse(detailsBody.matches(".*" + uuidPattern + ".*"),
                "details response must not contain any UUID-shaped value: " + detailsBody);
        // and specifically the fixture's own internal aggregate UUID
        assertFalse(detailsBody.contains(FACT_A_AGG.toString()),
                "details response must not contain the internal station UUID");

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
    @Order(13)
    void depthFiltersAndEnrichedDetails() throws Exception {
        // connectorType=CCS → both depth stations (each has a CCS connector)
        JsonNode ccs = listJson("connectorType=CCS");
        JsonNode ccsA = findByName(ccs, "Depth Fixture Station A");
        assertNotNull(ccsA, "A has CCS");
        assertNotNull(findByName(ccs, "Depth Fixture Station B"), "B has CCS");
        // MAJOR-1: totalEvses is populated on the connector-filtered path too
        assertEquals(2, ccsA.get("totalEvses").asInt(),
                "the filtered list must carry station A's totalEvses=2");
        JsonNode ccsB = findByName(ccs, "Depth Fixture Station B");
        assertNotNull(ccsB.get("totalEvses"), "station B's totalEvses must be present");
        // B = B1 + B2 (Order 6) + the extra C1 EVSE Order 8 hangs off B
        // (deliberately not A, so A stays at 2) → 3
        assertEquals(3, ccsB.get("totalEvses").asInt(),
                "station B has 3 ACTIVE EVSEs (B1, B2, and Order 8's C1)");

        // +minPowerW=100000 → still both (CCS 150000 >= 100000)
        JsonNode ccsPower = listJson("connectorType=CCS&minPowerW=100000");
        JsonNode powerA = findByName(ccsPower, "Depth Fixture Station A");
        assertNotNull(powerA);
        assertNotNull(findByName(ccsPower, "Depth Fixture Station B"));
        assertEquals(2, powerA.get("totalEvses").asInt(),
                "the power-filtered list must also carry totalEvses=2");

        // unfiltered list: DEPTHSTA-A totalEvses=2 (MAJOR-1 on the no-filter path)
        JsonNode all = listJson("");
        JsonNode plainA = findByName(all, "Depth Fixture Station A");
        assertNotNull(plainA, "DEPTHSTA-A must be listed");
        assertEquals(2, plainA.get("totalEvses").asInt(),
                "the unfiltered list must carry DEPTHSTA-A's totalEvses=2");

        // minPowerW=200000 → none
        JsonNode none = listJson("minPowerW=200000");
        assertTrue(none.isArray());
        assertEquals(0, none.size(), "no connector reaches 200000 W");

        // details enrichment on DEPTHSTA-A: totalEvses=2, evses with
        // connectors, tariff EUR 480/10
        ResponseEntity<String> details = REST.getForEntity(
                BASE_URL + "/api/v1/stations/DEPTHSTA-A", String.class);
        assertEquals(200, details.getStatusCode().value());
        JsonNode d = MAPPER.readTree(details.getBody());
        assertEquals("DEPTHSTA-A", d.get("ref").asText());
        assertEquals(2, d.get("totalEvses").asInt(), "station A must have 2 ACTIVE EVSEs");
        JsonNode evses = d.get("evses");
        assertTrue(evses.isArray());
        assertEquals(2, evses.size());
        JsonNode evseA1 = findByUid(evses, UID_A1);
        assertNotNull(evseA1, "GR*SEED*A1 must be in the details");
        JsonNode a1Connectors = evseA1.get("connectors");
        assertEquals(2, a1Connectors.size());
        assertEquals("CCS", a1Connectors.get(0).get("type").asText());
        assertEquals(150000, a1Connectors.get(0).get("maxPowerW").asInt());
        assertEquals("TYPE2", a1Connectors.get(1).get("type").asText());
        assertEquals(22000, a1Connectors.get(1).get("maxPowerW").asInt());
        JsonNode evseA2 = findByUid(evses, UID_A2);
        assertNotNull(evseA2, "GR*SEED*A2 must be in the details");
        assertEquals(2, evseA2.get("connectors").size());
        JsonNode tariff = d.get("tariff");
        assertNotNull(tariff, "the projected tariff must be surfaced");
        assertEquals("EUR", tariff.get("currency").asText());
        JsonNode components = tariff.get("components");
        assertEquals(2, components.size());
        assertEquals("ENERGY_PER_KWH", components.get(0).get("kind").asText());
        assertEquals("KWH", components.get(0).get("unit").asText());
        assertEquals(480, components.get(0).get("amountMinor").asInt());
        assertEquals("OCCUPANCY_PER_MINUTE", components.get(1).get("kind").asText());
        assertEquals("MINUTE", components.get(1).get("unit").asText());
        assertEquals(10, components.get(1).get("amountMinor").asInt());

        // no openingHours in this slice (deferred per the task packet)
        assertNull(d.get("openingHours"), "openingHours is deferred and must not appear");
    }

    private static JsonNode listJson(String query) throws Exception {
        ResponseEntity<String> response = REST.getForEntity(
                BASE_URL + "/api/v1/stations?" + query, String.class);
        assertEquals(200, response.getStatusCode().value());
        return MAPPER.readTree(response.getBody());
    }

    private static JsonNode findByUid(JsonNode evses, String uid) {
        for (JsonNode node : evses) {
            if (uid.equals(node.path("uid").asText(null))) {
                return node;
            }
        }
        return null;
    }

    @Test
    @Order(14)
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
    @Order(15)
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
        // projection rows carry only public fields. Protection is
        // STRUCTURAL: the DTOs (StationSummary/StationDetails/EvseView/
        // ConnectorView/TariffView/ComponentView) have no
        // station_ref/evse_ref/connector_ref/internal-identifier field at
        // all, and Orders 12–13 assert the wire representation (no
        // "stationRef" field, no UUID-shaped value in the details body).
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
                "--spring.rabbitmq.listener.simple.retry.max-interval=500",
                "--spring.rabbitmq.listener.simple.retry.max-retries=3");
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
