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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-MSG-002 AC-04 (review finding M4): the StationPublished envelopes the
 * service actually emits into the outbox must validate against the
 * authoritative executable schemas — contracts/schemas/common/cloud-event.json
 * for the envelope and contracts/schemas/events/station-published-event.json
 * for the payload's data. Executable-schema validation, not shape assertion.
 *
 * I1-MSG-003: the WIRE envelope (what the dispatcher actually sends) is the
 * stored payload plus the ARC-014 §2 extension attributes derived from the
 * outbox columns at send time via OutboxDispatcher.enrichedPayload. The
 * enriched envelope must still validate against cloud-event.json (extensions
 * are additional properties) and its data against
 * station-published-event.json, with correlationid/aggregateid/
 * aggregateversion/dataschema equal to the authoritative columns and
 * causationid absent for NULL causation_id.
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
    void emittedStationPublishedEnvelopesValidateAgainstExecutableSchemas() throws Exception {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema cloudEventSchema = factory.getSchema(
                Files.newInputStream(contractsDir()
                        .resolve("schemas").resolve("common").resolve("cloud-event.json")));
        JsonSchema stationPublishedSchema = factory.getSchema(
                Files.newInputStream(contractsDir()
                        .resolve("schemas").resolve("events")
                        .resolve("station-published-event.json")));

        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT message_id, message_type, payload, attempt_count, "
                             + "correlation_id, causation_id, aggregate_ref, aggregate_version "
                             + "FROM " + SCHEMA + ".outbox_message "
                             + "WHERE message_type = 'com.evplatform.station.published.v1' "
                             + "ORDER BY message_id");
             ResultSet rs = ps.executeQuery()) {
            int validated = 0;
            while (rs.next()) {
                UUID messageId = rs.getObject("message_id", UUID.class);
                String messageType = rs.getString("message_type");
                String payload = rs.getString("payload");
                int attemptCount = rs.getInt("attempt_count");
                UUID correlationId = rs.getObject("correlation_id", UUID.class);
                UUID causationId = rs.getObject("causation_id", UUID.class);
                UUID aggregateRef = rs.getObject("aggregate_ref", UUID.class);
                long aggregateVersion = rs.getLong("aggregate_version");

                // raw stored payload validations (unchanged, I1-MSG-002)
                JsonNode envelope = MAPPER.readTree(payload);
                Set<ValidationMessage> envelopeErrors =
                        cloudEventSchema.validate(envelope);
                assertTrue(envelopeErrors.isEmpty(),
                        "emitted envelope must validate against cloud-event.json: "
                                + envelopeErrors + " in " + envelope);
                Set<ValidationMessage> payloadErrors =
                        stationPublishedSchema.validate(envelope.get("data"));
                assertTrue(payloadErrors.isEmpty(),
                        "emitted payload data must validate against "
                                + "station-published-event.json: " + payloadErrors);

                // I1-MSG-003: validate the ENRICHED wire envelope the
                // dispatcher derives at send time
                OutboxDispatcher.OutboxRow row = new OutboxDispatcher.OutboxRow(
                        messageId, messageType, payload, attemptCount,
                        correlationId, causationId, aggregateRef, aggregateVersion);
                JsonNode enriched = MAPPER.readTree(OutboxDispatcher.enrichedPayload(row));
                Set<ValidationMessage> enrichedEnvelopeErrors =
                        cloudEventSchema.validate(enriched);
                assertTrue(enrichedEnvelopeErrors.isEmpty(),
                        "enriched envelope must validate against cloud-event.json "
                                + "(ARC-014 §2 extensions are additional properties): "
                                + enrichedEnvelopeErrors + " in " + enriched);
                Set<ValidationMessage> enrichedPayloadErrors =
                        stationPublishedSchema.validate(enriched.get("data"));
                assertTrue(enrichedPayloadErrors.isEmpty(),
                        "enriched envelope data must validate against "
                                + "station-published-event.json: " + enrichedPayloadErrors);
                assertEquals(correlationId.toString(),
                        enriched.get("correlationid").asText(),
                        "correlationid must equal the correlation_id column");
                assertEquals(aggregateRef.toString(),
                        enriched.get("aggregateid").asText(),
                        "aggregateid must equal the aggregate_ref column");
                assertEquals(aggregateVersion,
                        enriched.get("aggregateversion").asLong(),
                        "aggregateversion must equal the aggregate_version column as a number");
                assertEquals("https://schema-registry.example.com/events/station-published-event.json",
                        enriched.get("dataschema").asText(),
                        "dataschema must be the event schema $id");
                assertNull(enriched.get("causationid"),
                        "causationid must be absent for NULL causation_id, not null-valued");
                assertNull(enriched.get("traceparent"),
                        "traceparent must never be emitted");
                validated++;
            }
            assertEquals(2, validated,
                    "the seed must have emitted one StationPublished fact per station");
        }

        // negative control: removing the required id attribute must fail
        // cloud-event.json validation — the harness can actually detect drift
        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT payload FROM " + SCHEMA + ".outbox_message "
                             + "WHERE message_type = 'com.evplatform.station.published.v1' "
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
