package com.evplatform.libraries.testsupport;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-DAT-001 AC-01/AC-02: the real provisioning script (bind-mounted, the
 * same code path as the compose stack) creates the nine databases with
 * owner/migrator/runtime roles, and cross-service access genuinely fails.
 */
class ProvisioningTest {

    private static final Set<String> DATABASES = Set.of(
            "account_db", "station_operations_db", "booking_session_db",
            "device_integration_db", "discovery_insights_db", "notification_db",
            "governance_support_db", "bff_session_db", "keycloak_db");

    static final PostgreSQLContainer PG = StartOnce.PG;

    static Connection connect(String db, String role) throws Exception {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + db,
                role, "evplatform_dev_only");
    }

    static Set<String> querySet(String sql) throws Exception {
        try (Connection c = connect("evplatform_bootstrap", "evplatform_admin");
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Set<String> out = new HashSet<>();
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            return out;
        }
    }

    @Test
    void allNineDatabasesExist() throws Exception {
        Set<String> found = new HashSet<>(querySet("SELECT datname FROM pg_database WHERE datistemplate = false"));
        Set<String> missing = new HashSet<>(DATABASES);
        missing.removeAll(found);
        assertTrue(missing.isEmpty(), "missing databases: " + missing);
    }

    @Test
    void allTwentySevenRolesExist() throws Exception {
        Set<String> roles = querySet("SELECT rolname FROM pg_roles");
        for (String db : DATABASES) {
            String base = db.substring(0, db.length() - 3); // strip "_db"
            assertTrue(roles.contains(base + "_owner"), "missing owner role for " + db);
            assertTrue(roles.contains(base + "_migrator"), "missing migrator role for " + db);
            assertTrue(roles.contains(base + "_runtime"), "missing runtime role for " + db);
        }
    }

    @Test
    void runtimeConnectsToOwnDatabase() throws Exception {
        try (Connection c = connect("booking_session_db", "booking_session_runtime")) {
            assertTrue(c.isValid(2));
        }
    }

    @Test
    void runtimeCannotConnectToAnotherServicesDatabase() {
        // account_runtime against booking_session_db — CONNECT was revoked from PUBLIC
        assertThrows(Exception.class, () -> {
            try (Connection c = connect("booking_session_db", "account_runtime")) {
                c.isValid(2);
            }
        }, "cross-service connect must fail (AC-02)");
    }

    @Test
    void noPublicConnectGrantRemains() throws Exception {
        // scoped to the nine canonical databases: transient throwaway DBs of
        // other tests are not provisioned targets
        try (Connection c = connect("evplatform_bootstrap", "evplatform_admin");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM pg_database dat "
                             + "WHERE datname = ANY(ARRAY['account_db','station_operations_db','booking_session_db',"
                             + "'device_integration_db','discovery_insights_db','notification_db',"
                             + "'governance_support_db','bff_session_db','keycloak_db']) "
                             + "AND has_database_privilege('public', datname, 'CONNECT')");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "no canonical database may keep the PUBLIC CONNECT grant");
        }
    }

    /** Single container shared by the whole suite; started with the real init dir. */
    private static class StartOnce {
        static final PostgreSQLContainer PG = startQuietly();

        static PostgreSQLContainer startQuietly() {
            PostgreSQLContainer c = LocalDependencies.newPostgresWithProvisioning(
                    Path.of("..", "..", "infra", "local", "postgres"));
            try {
                c.start();
                return c;
            } catch (Exception e) {
                // surface the entrypoint/provisioning output that killed startup
                System.err.println("=== container logs on failure ===");
                System.err.println(c.getLogs());
                throw new IllegalStateException("provisioned postgres failed to start", e);
            }
        }
    }
}
