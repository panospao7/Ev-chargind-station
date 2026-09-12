package com.evplatform.stationoperations.outbox;

import com.evplatform.libraries.testsupport.LocalDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-MSG-002 AC-04 (review finding M4): the event envelopes the service
 * actually emits into the outbox must validate against the authoritative
 * executable schemas — contracts/schemas/common/cloud-event.json for the
 * envelope and the per-family event schema (station-published,
 * evse-configuration-changed, connector-configuration-changed,
 * tariff-published) for the payload's data. Executable-schema validation,
 * not shape assertion. Every outbox row is validated: rows are grouped by
 * message_type and each group is validated against its own schema.
 *
 * I1-MSG-003: the WIRE envelope (what the dispatcher actually sends) is the
 * stored payload plus the ARC-014 §2 extension attributes derived from the
 * outbox columns at send time via OutboxDispatcher.enrichedPayload. The
 * enriched envelope must still validate against cloud-event.json (extensions
 * are additional properties) and its data against the family schema, with
 * correlationid/aggregateid/aggregateversion/dataschema equal to the
 * authoritative columns and causationid absent for NULL causation_id.
 *
 * The negative control proves the harness can fail: an envelope missing the
 * required id attribute must produce validation errors.
 */
class ContractSchemaValidationTest {

    private static final String PW = "evplatform_dev_only";
    private static final String DB = "station_operations_db";
    private static final String MIGRATOR = "station_operations_migrator";
    private static final String RUNTIME = "station_operations_runtime";
    private static final String SCHEMA = "station_operations";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** message_type → event schema file, mirroring the registry schemaPath. */
    private static final Map<String, String> SCHEMA_FILE_BY_TYPE = Map.of(
            "com.evplatform.station.published.v1", "station-published-event.json",
            "com.evplatform.station.evse-configuration-changed.v1",
            "evse-configuration-changed-event.json",
            "com.evplatform.station.connector-configuration-changed.v1",
            "connector-configuration-changed-event.json",
            "com.evplatform.station.tariff-published.v1", "tariff-published-event.json");

    /** message_type → the $id of the family's executable event schema. */
    private static final Map<String, String> SCHEMA_ID_BY_TYPE = Map.of(
            "com.evplatform.station.published.v1",
            "https://schema-registry.example.com/events/station-published-event.json",
            "com.evplatform.station.evse-configuration-changed.v1",
            "https://schema-registry.example.com/events/evse-configuration-changed-event.json",
            "com.evplatform.station.connector-configuration-changed.v1",
            "https://schema-registry.example.com/events/connector-configuration-changed-event.json",
            "com.evplatform.station.tariff-published.v1",
            "https://schema-registry.example.com/events/tariff-published-event.json");

    /** Per-family expected fact counts for the canonical seed dataset. */
    private static final Map<String, Integer> EXPECTED_FACTS_BY_TYPE = Map.of(
            "com.evplatform.station.published.v1", 2,
            "com.evplatform.station.evse-configuration-changed.v1", 4,
            "com.evplatform.station.connector-configuration-changed.v1", 8,
            "com.evplatform.station.tariff-published.v1", 1);

    private static final PostgreSQLContainer PG = StartOnce.PG;

    /**
     * Resolves the repository contracts directory. The module working dir is
     * services/station-operations-service, so the repo root is two levels up;
     * if that fails (e.g. an unusual run dir), walk up until a directory
     * containing contracts/schemas is found.
     */
    private static Path contractsDir() throws Exception {
        Path candidate = Path.of("..", "..", "contracts").toAbsolutePath().normalize();
        if (Files.isDirectory(candidate.resolve("schemas"))) {
            return candidate;
        }
        Path dir = Path.of("").toAbsolutePath().normalize();
        while (dir != null) {
            Path probe = dir.resolve("contracts").resolve("schemas");
            if (Files.isDirectory(probe)) {
                return dir.resolve("contracts");
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("contracts/schemas directory not found");
    }

    @Test
    void emittedEnvelopesValidateAgainstExecutableSchemas() throws Exception {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema cloudEventSchema = factory.getSchema(
                Files.newInputStream(contractsDir()
                        .resolve("schemas").resolve("common").resolve("cloud-event.json")));
        Map<String, JsonSchema> eventSchemas = new java.util.HashMap<>();
        for (String schemaFile : SCHEMA_FILE_BY_TYPE.values()) {
            eventSchemas.put(schemaFile, factory.getSchema(
                    Files.newInputStream(contractsDir()
                            .resolve("schemas").resolve("events").resolve(schemaFile))));
        }

        // every outbox row, grouped by message_type, validated against its
        // own family schema — no row is exempt from contract validation
        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT message_id, message_type, payload, attempt_count, "
                             + "correlation_id, causation_id, aggregate_ref, aggregate_version, "
                             + "classification "
                             + "FROM " + SCHEMA + ".outbox_message "
                             + "ORDER BY message_id");
             ResultSet rs = ps.executeQuery()) {
            Map<String, Integer> validatedByType = new java.util.HashMap<>();
            while (rs.next()) {
                UUID messageId = rs.getObject("message_id", UUID.class);
                String messageType = rs.getString("message_type");
                String payload = rs.getString("payload");
                int attemptCount = rs.getInt("attempt_count");
                UUID correlationId = rs.getObject("correlation_id", UUID.class);
                UUID causationId = rs.getObject("causation_id", UUID.class);
                UUID aggregateRef = rs.getObject("aggregate_ref", UUID.class);
                long aggregateVersion = rs.getLong("aggregate_version");
                String classification = rs.getString("classification");

                JsonSchema eventSchema = eventSchemas.get(SCHEMA_FILE_BY_TYPE.get(messageType));
                assertNotNull(eventSchema, "every outbox message_type must map to a family schema: "
                        + messageType);

                // raw stored payload validations (I1-MSG-002, now all families)
                JsonNode envelope = MAPPER.readTree(payload);
                Set<ValidationMessage> envelopeErrors =
                        cloudEventSchema.validate(envelope);
                assertTrue(envelopeErrors.isEmpty(),
                        "emitted envelope must validate against cloud-event.json: "
                                + envelopeErrors + " in " + envelope);
                Set<ValidationMessage> payloadErrors =
                        eventSchema.validate(envelope.get("data"));
                assertTrue(payloadErrors.isEmpty(),
                        "emitted payload data must validate against "
                                + SCHEMA_FILE_BY_TYPE.get(messageType) + ": " + payloadErrors);

                // I1-MSG-003: validate the ENRICHED wire envelope the
                // dispatcher derives at send time
                OutboxDispatcher.OutboxRow row = new OutboxDispatcher.OutboxRow(
                        messageId, messageType, payload, attemptCount,
                        correlationId, causationId, aggregateRef, aggregateVersion,
                        classification);
                JsonNode enriched = MAPPER.readTree(OutboxDispatcher.enrichedPayload(row));
                Set<ValidationMessage> enrichedEnvelopeErrors =
                        cloudEventSchema.validate(enriched);
                assertTrue(enrichedEnvelopeErrors.isEmpty(),
                        "enriched envelope must validate against cloud-event.json "
                                + "(ARC-014 §2 extensions are additional properties): "
                                + enrichedEnvelopeErrors + " in " + enriched);
                Set<ValidationMessage> enrichedPayloadErrors =
                        eventSchema.validate(enriched.get("data"));
                assertTrue(enrichedPayloadErrors.isEmpty(),
                        "enriched envelope data must validate against "
                                + SCHEMA_FILE_BY_TYPE.get(messageType) + ": "
                                + enrichedPayloadErrors);
                assertEquals(correlationId.toString(),
                        enriched.get("correlationid").asText(),
                        "correlationid must equal the correlation_id column");
                assertEquals(aggregateRef.toString(),
                        enriched.get("aggregateid").asText(),
                        "aggregateid must equal the aggregate_ref column");
                assertEquals(aggregateVersion,
                        enriched.get("aggregateversion").asLong(),
                        "aggregateversion must equal the aggregate_version column as a number");
                assertEquals(SCHEMA_ID_BY_TYPE.get(messageType),
                        enriched.get("dataschema").asText(),
                        "dataschema must be the event schema $id for the message type");
                assertEquals(classification,
                        enriched.get("classification").asText(),
                        "classification must equal the outbox classification column");
                assertNull(enriched.get("causationid"),
                        "causationid must be absent for NULL causation_id, not null-valued");
                assertNull(enriched.get("traceparent"),
                        "traceparent must never be emitted");
                validatedByType.merge(messageType, 1, Integer::sum);
            }
            assertEquals(EXPECTED_FACTS_BY_TYPE, validatedByType,
                    "the seed must emit the canonical fact inventory (2+4+8+1 = 15)");
        }

        // negative control: removing the required id attribute must fail
        // cloud-event.json validation — the harness can actually detect drift
        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT payload FROM " + SCHEMA + ".outbox_message "
                             + "ORDER BY message_id LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "a seed-emitted envelope must exist");
            ObjectNode broken = (ObjectNode) MAPPER.readTree(rs.getString(1));
            broken.remove("id");
            assertFalse(cloudEventSchema.validate(broken).isEmpty(),
                    "an envelope without id must NOT validate against cloud-event.json");
        }
    }

    private static Connection connect(String user) throws Exception {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB,
                user, PW);
    }

    /** Own container: fresh install + migrations + seed, mirroring the ordered suite's harness. */
    private static class StartOnce {
        static final PostgreSQLContainer PG = start();

        static PostgreSQLContainer start() {
            PostgreSQLContainer c = LocalDependencies.newPostgresWithProvisioning(
                    Path.of("..", "..", "infra", "local", "postgres"));
            c.start();
            Flyway.configure()
                    .dataSource("jdbc:postgresql://" + c.getHost() + ":"
                            + c.getMappedPort(5432) + "/" + DB, MIGRATOR, PW)
                    .locations("filesystem:" + Path.of("src", "main", "resources",
                            "db", "migration").toAbsolutePath().normalize())
                    .schemas(SCHEMA)
                    .defaultSchema(SCHEMA)
                    .load()
                    .migrate();
            var ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                    new org.postgresql.Driver(),
                    "jdbc:postgresql://" + c.getHost() + ":" + c.getMappedPort(5432) + "/" + DB,
                    RUNTIME, PW);
            var tx = new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
            new com.evplatform.stationoperations.seed.StationOperationsSeeder(
                    org.springframework.jdbc.core.simple.JdbcClient.create(ds),
                    new OutboxWriter(org.springframework.jdbc.core.simple.JdbcClient.create(ds)),
                    tx).seed();
            return c;
        }
    }
}
