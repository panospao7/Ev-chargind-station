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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-MSG-002 AC-04 (review finding M4): the StationPublished envelopes the
 * service actually emits into the outbox must validate against the
 * authoritative executable schemas — contracts/schemas/common/cloud-event.json
 * for the envelope and contracts/schemas/events/station-published-event.json
 * for the payload's data. Executable-schema validation, not shape assertion.
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
                     "SELECT payload FROM " + SCHEMA + ".outbox_message "
                             + "WHERE message_type = 'com.evplatform.station.published.v1' "
                             + "ORDER BY message_id");
             ResultSet rs = ps.executeQuery()) {
            int validated = 0;
            while (rs.next()) {
                JsonNode envelope = MAPPER.readTree(rs.getString(1));
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
