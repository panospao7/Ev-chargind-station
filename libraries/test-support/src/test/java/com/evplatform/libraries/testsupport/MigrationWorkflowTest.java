package com.evplatform.libraries.testsupport;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-DAT-001 AC-03/AC-04: fresh install, validate, upgrade path, checksum
 * immutability and unique numbering — Flyway runs as the migrator role
 * against the provisioned PostgreSQL 18 container (never an embedded
 * substitute).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationWorkflowTest {

    private static final String PW = "evplatform_dev_only";
    private static final List<String[]> SERVICES = List.of(
            new String[]{"account-service", "account", "account_db", "account_migrator", "account_runtime"},
            new String[]{"station-operations-service", "station_operations", "station_operations_db", "station_operations_migrator", "station_operations_runtime"},
            new String[]{"booking-session-service", "booking_session", "booking_session_db", "booking_session_migrator", "booking_session_runtime"},
            new String[]{"device-integration-service", "device_integration", "device_integration_db", "device_integration_migrator", "device_integration_runtime"},
            new String[]{"discovery-insights-service", "discovery_insights", "discovery_insights_db", "discovery_insights_migrator", "discovery_insights_runtime"},
            new String[]{"notification-service", "notification", "notification_db", "notification_migrator", "notification_runtime"},
            new String[]{"governance-support-service", "governance_support", "governance_support_db", "governance_support_migrator", "governance_support_runtime"});

    private static String[] svc(int idx) {
        return SERVICES.get(idx);
    }

    private static Flyway flywayFor(String db, String schema, String migrator, List<String> locationArgs) {
        String[] locations = locationArgs.stream()
                .map(l -> "filesystem:" + l)
                .toArray(String[]::new);
        return Flyway.configure()
                .dataSource("jdbc:postgresql://" + ProvisioningTest.PG.getHost() + ":"
                        + ProvisioningTest.PG.getMappedPort(5432) + "/" + db, migrator, PW)
                // history table lives in the service schema (the migrator has
                // CREATE there); the default `public` is intentionally off-limits
                .schemas(schema)
                .defaultSchema(schema)
                .locations(locations)
                .baselineOnMigrate(false)
                .load();
    }

    private static Path migrationDir(String serviceDir) {
        return Path.of("..", "..", "services", serviceDir, "src", "main", "resources", "db", "migration").normalize();
    }

    @Test
    @Order(1)
    void freshInstallAppliesAllMigrationsPerService() {
        // grew from ==1 (DAT-001 snapshot) to >=1: services legitimately gain
        // forward migrations after their V1 baseline (I1-STA-001 added V2)
        for (String[] s : SERVICES) {
            Flyway flyway = flywayFor(s[2], s[1], s[3], List.of(migrationDir(s[0]).toString()));
            MigrateResult result = flyway.migrate();
            assertTrue(result.migrationsExecuted >= 1,
                    s[0] + " must apply its migrations cleanly from an empty database");
        }
    }

    @Test
    @Order(2)
    void validatePassesForAllServices() {
        for (String[] s : SERVICES) {
            Flyway flyway = flywayFor(s[2], s[1], s[3], List.of(migrationDir(s[0]).toString()));
            flyway.validate(); // throws on checksum/version problems
        }
    }

    @Test
    @Order(3)
    void runtimeRoleSeesSchemaButCannotCreateTables() throws Exception {
        String[] s = svc(2); // booking-session-service
        // runtime can see the schema (USAGE grant from V1)
        try (Connection runtime = ProvisioningTest.connect(s[2], s[4]);
             PreparedStatement ps = runtime.prepareStatement(
                     "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, s[1]);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "runtime must see the service schema");
            }
        }
        // runtime cannot execute DDL (AC-02)
        try (Connection runtime = ProvisioningTest.connect(s[2], s[4]);
             PreparedStatement ps = runtime.prepareStatement(
                     "CREATE TABLE " + s[1] + ".runtime_must_not_create(id int)")) {
            assertThrows(Exception.class, ps::execute, "runtime role DDL must fail");
        }
    }

    @Test
    @Order(4)
    void runtimeDmlWorksOnMigratorCreatedTable() throws Exception {
        String[] s = svc(2);
        try (Connection migrator = ProvisioningTest.connect(s[2], s[3]);
             PreparedStatement create = migrator.prepareStatement(
                     "CREATE TABLE " + s[1] + ".dat001_probe(id int primary key, note text)");
             PreparedStatement grant = migrator.prepareStatement(
                     "GRANT SELECT, INSERT, DELETE ON " + s[1] + ".dat001_probe TO " + s[4])) {
            create.execute();
            grant.execute();
        }
        try (Connection runtime = ProvisioningTest.connect(s[2], s[4]);
             PreparedStatement insert = runtime.prepareStatement(
                     "INSERT INTO " + s[1] + ".dat001_probe VALUES (1, 'runtime dml ok')")) {
            assertEquals(1, insert.executeUpdate(), "runtime DML must succeed");
        }
        // cleanup so the database stays at V1-only state
        try (Connection migrator = ProvisioningTest.connect(s[2], s[3]);
             PreparedStatement drop = migrator.prepareStatement(
                     "DROP TABLE " + s[1] + ".dat001_probe")) {
            drop.execute();
        }
    }

    @Test
    @Order(5)
    void upgradePathAppliesV2AfterV1() throws Exception {
        String[] s = svc(2);
        Path temp = Files.createTempDirectory("dat001-upgrade");
        Files.writeString(temp.resolve("V2__test_upgrade.sql"),
                "CREATE TABLE " + s[1] + ".upgrade_probe(id int);");
        Flyway flyway = flywayFor(s[2], s[1], s[3],
                List.of(migrationDir(s[0]).toString(), temp.toString()));
        MigrateResult result = flyway.migrate();
        assertEquals(1, result.migrationsExecuted, "V2 fixture must apply on top of V1");
        try (Connection migrator = ProvisioningTest.connect(s[2], s[3]);
             PreparedStatement drop = migrator.prepareStatement(
                     "DROP TABLE " + s[1] + ".upgrade_probe");
             PreparedStatement tidy = migrator.prepareStatement(
                     "DELETE FROM " + s[1] + ".flyway_schema_history WHERE version = '2'")) {
            drop.execute();
            tidy.execute();
        }
        Files.walk(temp).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    @Test
    @Order(6)
    void tamperedChecksumFailsValidation() throws Exception {
        String[] s = svc(0); // account-service — migrate fresh copy in throwaway DB
        Path temp = Files.createTempDirectory("dat001-tamper");
        List<String> copies = new ArrayList<>();
        try (Stream<Path> files = Files.list(migrationDir(s[0]))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                Path target = temp.resolve(p.getFileName().toString());
                Files.writeString(target, Files.readString(p) + "\n-- tampered\n");
                copies.add(target.toString());
            }
        }
        // throwaway database owned like a service DB, with the same grants
        // step 3 of infra/local/postgres/01-provision.sh applies
        try (Connection admin = ProvisioningTest.connect("evplatform_bootstrap", "evplatform_admin");
             PreparedStatement createDb = admin.prepareStatement(
                     "CREATE DATABASE dat001_tamper_db OWNER account_owner");
             PreparedStatement grants = admin.prepareStatement(
                     "GRANT CONNECT ON DATABASE dat001_tamper_db TO account_migrator;"
                             + "GRANT CREATE ON DATABASE dat001_tamper_db TO account_migrator")) {
            createDb.execute();
            grants.execute();
        }
        Flyway good = flywayFor("dat001_tamper_db", s[1], s[3], List.of(migrationDir(s[0]).toString()));
        good.migrate();
        Flyway tampered = flywayFor("dat001_tamper_db", s[1], s[3], List.of(temp.toString()));
        assertThrows(Exception.class, tampered::validate,
                "checksum tampering must fail validation (applied migrations are immutable)");
        try (Connection admin = ProvisioningTest.connect("evplatform_bootstrap", "evplatform_admin");
             PreparedStatement dropDb = admin.prepareStatement("DROP DATABASE dat001_tamper_db WITH (FORCE)")) {
            dropDb.execute();
        }
    }

    @Test
    @Order(7)
    void duplicateVersionNumbersAreRejected() throws Exception {
        String[] s = svc(0);
        Path temp = Files.createTempDirectory("dat001-duplicate");
        Files.writeString(temp.resolve("V1__first.sql"), "SELECT 1;");
        Files.writeString(temp.resolve("V1__second.sql"), "SELECT 2;");
        Flyway flyway = flywayFor(s[2], s[1], s[3], List.of(temp.toString()));
        assertThrows(Exception.class, flyway::migrate,
                "duplicate migration version numbers must fail (unique numbering)");
    }

    @Test
    @Order(8)
    void migrationVersionNumbersAreUniqueOnDisk() throws Exception {
        Pattern v = Pattern.compile("V(\\d+)__");
        for (String[] s : SERVICES) {
            List<Integer> seen = new ArrayList<>();
            try (Stream<Path> files = Files.list(migrationDir(s[0]))) {
                for (Path p : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                    Matcher m = v.matcher(p.getFileName().toString());
                    assertTrue(m.lookingAt(), "migration files must be named V<version>__<name>.sql: " + p);
                    assertTrue(seen.add(Integer.parseInt(m.group(1))),
                            "duplicate version in " + p);
                }
            }
        }
    }
}
