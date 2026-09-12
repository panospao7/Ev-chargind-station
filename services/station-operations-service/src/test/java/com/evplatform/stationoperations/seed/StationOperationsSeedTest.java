package com.evplatform.stationoperations.seed;

import com.evplatform.libraries.testsupport.LocalDependencies;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-STA-001 acceptance: migrations apply (AC-01), the S1-01 dataset seeds
 * deterministically (AC-02), seed is idempotent and reset restores canonical
 * emptiness within station_operations_db only (AC-03), ACTIVE versions are
 * immutable at the database level (AC-04). Ordered: the container and its
 * migrated schema are shared by the suite.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationOperationsSeedTest {

    private static final String PW = "evplatform_dev_only";
    private static final String DB = "station_operations_db";
    private static final String MIGRATOR = "station_operations_migrator";
    private static final String RUNTIME = "station_operations_runtime";
    private static final String SCHEMA = "station_operations";

    private static final List<String> TABLES = List.of(
            "outbox_message", "inbox_message", "idempotency_record", "audit_event",
            "operator_organization", "organization_member", "station",
            "station_opening_period", "station_schedule_exception", "evse",
            "connector", "tariff", "tariff_version", "tariff_component",
            "booking_policy", "booking_policy_version", "simulator_assignment");

    static final PostgreSQLContainer PG = StartOnce.PG;

    private static Connection connect(String user) throws Exception {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB,
                user, PW);
    }

    private static org.springframework.jdbc.core.simple.JdbcClient jdbc(String user)
            throws Exception {
        var ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.postgresql.Driver(),
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB,
                user, PW);
        return org.springframework.jdbc.core.simple.JdbcClient.create(ds);
    }

    private static long count(String table) throws Exception {
        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM " + SCHEMA + "." + table);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static StationOperationsSeeder seeder() throws Exception {
        var ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.postgresql.Driver(),
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB,
                RUNTIME, PW);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        return new StationOperationsSeeder(
                org.springframework.jdbc.core.simple.JdbcClient.create(ds),
                new com.evplatform.stationoperations.outbox.OutboxWriter(
                        org.springframework.jdbc.core.simple.JdbcClient.create(ds)),
                tx);
    }
    private static Map<String, Long> counts() throws Exception {
        var out = new HashMap<String, Long>();
        for (String table : TABLES) {
            out.put(table, count(table));
        }
        return out;
    }

    private static void exec(String user, String sql) throws Exception {
        try (Connection c = connect(user);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        }
    }

    @Test
    @Order(1)
    void migrationsApplySeventeenTables() throws Exception {
        Flyway.configure()
                .dataSource("jdbc:postgresql://" + PG.getHost() + ":"
                        + PG.getMappedPort(5432) + "/" + DB, MIGRATOR, PW)
                .locations("filesystem:" + Path.of("..", "..",
                        "services", "station-operations-service",
                        "src", "main", "resources", "db", "migration").normalize())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .load()
                .migrate();
        try (Connection c = connect(MIGRATOR);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_schema = ? AND table_type = 'BASE TABLE' "
                             + "AND table_name <> 'flyway_schema_history' ORDER BY 1")) {
            ps.setString(1, SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> found = new ArrayList<>();
                while (rs.next()) {
                    found.add(rs.getString(1));
                }
                assertEquals(TABLES.stream().sorted().toList(), found,
                        "table set must equal the ARC-022 §9 W1-S1 list plus §8 integration tables exactly");
            }
        }
    }

    @Test
    @Order(2)
    void seedProducesCanonicalS1Dataset() throws Exception {
        seeder().seed();

        assertEquals(1, count("operator_organization"), "one operator organization");
        assertEquals(2, count("organization_member"), "admin + operator member");
        assertEquals(2, count("station"), "two published Greek stations");
        assertEquals(4, count("evse"), "two EVSEs per station");
        assertEquals(8, count("connector"), "two connector/power combinations per EVSE");
        assertEquals(14, count("station_opening_period"), "seven days per station");
        assertEquals(1, count("tariff"));
        assertEquals(1, count("tariff_version"));
        assertEquals(2, count("tariff_component"));
        assertEquals(1, count("booking_policy"));
        assertEquals(1, count("booking_policy_version"));
        assertEquals(4, count("simulator_assignment"), "one assignment per EVSE");
        assertEquals(2, count("outbox_message"), "one StationPublished fact per published station");

        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT state, country_code, latitude, longitude FROM "
                             + SCHEMA + ".station ORDER BY public_ref");
             ResultSet rs = ps.executeQuery()) {
            int stations = 0;
            while (rs.next()) {
                stations++;
                assertEquals("PUBLISHED", rs.getString(1), "stations must be PUBLISHED");
                assertEquals("GR", rs.getString(2), "Greek stations");
                double lat = rs.getBigDecimal(3).doubleValue();
                double lon = rs.getBigDecimal(4).doubleValue();
                assertTrue(lat > 34 && lat < 42 && lon > 19 && lon < 28,
                        "Greek coordinates expected");
            }
            assertEquals(2, stations);
        }

        try (Connection c = connect(RUNTIME);
             PreparedStatement ps = c.prepareStatement("SELECT "
                     + "hold_duration_seconds, slot_increment_minutes, min_duration_minutes, "
                     + "max_duration_minutes, advance_booking_days, near_term_horizon_minutes, "
                     + "freshness_threshold_seconds, late_arrival_grace_minutes, state FROM "
                     + SCHEMA + ".booking_policy_version");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(300, rs.getInt(1), "5-minute hold");
            assertEquals(15, rs.getInt(2), "15-minute increments");
            assertEquals(15, rs.getInt(3), "15-minute minimum");
            assertEquals(240, rs.getInt(4), "4-hour maximum");
            assertEquals(14, rs.getInt(5), "14-day advance limit");
            assertEquals(60, rs.getInt(6), "60-minute near-term horizon");
            assertEquals(300, rs.getInt(7), "300-second freshness");
            assertEquals(15, rs.getInt(8), "15-minute late-arrival grace");
            assertEquals("ACTIVE", rs.getString(9));
            assertTrue(!rs.next(), "exactly one policy version");
        }
    }

    @Test
    @Order(3)
    void seedIsIdempotent() throws Exception {
        Map<String, Long> before = counts();
        seeder().seed();
        assertEquals(before, counts(), "re-seeding must not change any row count");
    }

    @Test
    @Order(4)
    void activeTariffVersionIsImmutable() {
        assertThrows(Exception.class, () -> exec(MIGRATOR,
                "UPDATE " + SCHEMA + ".tariff_version SET state = 'RETIRED'"));
        assertThrows(Exception.class, () -> exec(MIGRATOR,
                "DELETE FROM " + SCHEMA + ".tariff_version"));
        assertThrows(Exception.class, () -> exec(MIGRATOR,
                "DELETE FROM " + SCHEMA + ".tariff_component"));
        assertThrows(Exception.class, () -> exec(MIGRATOR,
                "INSERT INTO " + SCHEMA + ".tariff_component"
                        + " (component_ref, tariff_version_ref, component_kind, unit, amount_minor)"
                        + " VALUES (gen_random_uuid(), (SELECT tariff_version_ref FROM "
                        + SCHEMA + ".tariff_version), 'ENERGY_PER_KWH', 'KWH', 1)"));
    }

    @Test
    @Order(5)
    void activeBookingPolicyVersionIsImmutable() {
        assertThrows(Exception.class, () -> exec(MIGRATOR,
                "UPDATE " + SCHEMA + ".booking_policy_version SET state = 'RETIRED'"));
        assertThrows(Exception.class, () -> exec(MIGRATOR,
                "DELETE FROM " + SCHEMA + ".booking_policy_version"));
        assertThrows(Exception.class, () -> exec(MIGRATOR,
                "UPDATE " + SCHEMA + ".booking_policy_version SET hold_duration_seconds = 60"));
    }

    @Test
    @Order(6)
    void runtimeRoleCannotCreateTables() {
        assertThrows(Exception.class, () -> exec(RUNTIME,
                "CREATE TABLE " + SCHEMA + ".runtime_must_not_create(id int)"));
    }

    @Test
    @Order(7)
    void resetEmptiesAndReseedRestores() throws Exception {
        new StationOperationsReset(jdbc(MIGRATOR)).reset();
        for (String table : TABLES) {
            assertEquals(0, count(table), table + " must be empty after reset");
        }
        seeder().seed();
        assertEquals(2, count("station"));
        assertEquals(8, count("connector"));
        assertEquals(4, count("simulator_assignment"));
        assertEquals(1, count("booking_policy_version"));
    }

    /**
     * AC-05 (review findings m1+m5): V4 gives the integration-table
     * constraints explicit stable names and adds the idempotency expiry
     * index. The catalogue must show the new names and must no longer carry
     * the auto-generated constraint names from V3.
     */
    @Test
    @Order(8)
    void integrationConstraintsCarryExplicitStableNames() throws Exception {
        try (Connection c = connect(MIGRATOR);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT conname FROM pg_constraint "
                             + "WHERE conrelid = 'station_operations.outbox_message'::regclass "
                             + "AND conname = 'uq_outbox_event_fact'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "outbox_message must carry uq_outbox_event_fact");
            }
        }
        try (Connection c = connect(MIGRATOR);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT conname FROM pg_constraint "
                             + "WHERE conrelid = 'station_operations.idempotency_record'::regclass "
                             + "AND conname = 'uq_idempotency_scope'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "idempotency_record must carry uq_idempotency_scope");
            }
        }
        try (Connection c = connect(MIGRATOR);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT indexname FROM pg_indexes "
                             + "WHERE schemaname = ? AND tablename = 'idempotency_record' "
                             + "AND indexname = 'ix_idempotency_expiry'")) {
            ps.setString(1, SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "idempotency_record.expires_at must be indexed");
            }
        }
        // the auto-generated V3 names must be gone (renamed, not duplicated)
        try (Connection c = connect(MIGRATOR);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM pg_constraint "
                             + "WHERE conname IN ("
                             + "'outbox_message_aggregate_type_aggregate_ref_aggregate_versi_key', "
                             + "'idempotency_record_principal_identity_operation_target_reso_key')")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1),
                        "the auto-generated V3 constraint names must be absent after V4");
            }
        }
    }

    /**
     * AC-05 upgrade path: a fresh database migrated to V3 first, then to the
     * latest version, must end in the same explicitly-named state as a fresh
     * install — proving the V4 rename applies cleanly on top of V3.
     */
    @Test
    @Order(9)
    void v4UpgradePathFromV3ProducesExplicitNames() throws Exception {
        PostgreSQLContainer upgrade = LocalDependencies.newPostgresWithProvisioning(
                Path.of("..", "..", "infra", "local", "postgres"));
        upgrade.start();
        String url = "jdbc:postgresql://" + upgrade.getHost() + ":"
                + upgrade.getMappedPort(5432) + "/" + DB;
        String location = "filesystem:" + Path.of("..", "..",
                "services", "station-operations-service",
                "src", "main", "resources", "db", "migration").normalize();
        try {
            // migrate to V3 only
            org.flywaydb.core.api.output.MigrateResult toV3 = Flyway.configure()
                    .dataSource(url, MIGRATOR, PW)
                    .locations(location)
                    .schemas(SCHEMA)
                    .defaultSchema(SCHEMA)
                    .target("3")
                    .load()
                    .migrate();
            assertEquals("3", toV3.targetSchemaVersion,
                    "the upgrade fixture must stop at V3");
            // then upgrade to latest (V4)
            Flyway.configure()
                    .dataSource(url, MIGRATOR, PW)
                    .locations(location)
                    .schemas(SCHEMA)
                    .defaultSchema(SCHEMA)
                    .load()
                    .migrate();
            try (Connection c = DriverManager.getConnection(url, MIGRATOR, PW);
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT count(*) FROM pg_constraint "
                                 + "WHERE conname IN ('uq_outbox_event_fact', 'uq_idempotency_scope')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1),
                            "V3→V4 upgrade must produce both explicit constraint names");
                }
            }
        } finally {
            upgrade.stop();
        }
    }

    @Test
    @Order(10)
    void seedRollsBackAtomicallyWhenFailureIsInjectedAfterSeed() throws Exception {
        // start from a clean slate so the assertion is unambiguous
        new StationOperationsReset(jdbc(MIGRATOR)).reset();
        // The seeder joins the ambient transaction: it is constructed on the
        // SAME DataSource instance as this test's TransactionTemplate, so its
        // internal TransactionOperations (REQUIRED propagation) reuses the
        // ambient connection instead of opening an independent one. A failure
        // thrown after seed() inside the SAME transaction must therefore roll
        // back both the business data and the outbox facts — no partial commit.
        var ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.postgresql.Driver(),
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB,
                RUNTIME, PW);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        var writer = new com.evplatform.stationoperations.outbox.OutboxWriter(
                org.springframework.jdbc.core.simple.JdbcClient.create(ds));
        var seeder = new StationOperationsSeeder(
                org.springframework.jdbc.core.simple.JdbcClient.create(ds), writer, tx);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            seeder.seed();
            throw new IllegalStateException("injected failure after seed");
        }));
        for (String table : TABLES) {
            assertEquals(0, count(table),
                    table + " must be rolled back with the outbox (atomic seed)");
        }
        // restore the canonical dataset for any later orders
        seeder().seed();
        assertEquals(2, count("station"), "re-seed after rollback must restore the dataset");
    }

    /** Single provisioned container shared by the ordered suite. */
    private static class StartOnce {
        static final PostgreSQLContainer PG = start();

        static PostgreSQLContainer start() {
            PostgreSQLContainer c = LocalDependencies.newPostgresWithProvisioning(
                    Path.of("..", "..", "infra", "local", "postgres"));
            c.start();
            return c;
        }
    }
}
