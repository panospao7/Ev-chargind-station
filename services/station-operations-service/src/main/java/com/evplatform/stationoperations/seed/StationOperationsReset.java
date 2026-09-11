package com.evplatform.stationoperations.seed;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reset: deletes all seed-relevant rows (children first) and leaves the
 * database empty, so a following {@link StationOperationsSeeder#seed()}
 * rebuilds the canonical dataset. Operates strictly within
 * station_operations_db.
 *
 * Privileged maintenance path: the ACTIVE-version protection triggers are
 * disabled for the duration by the MIGRATOR role (the tables' owner — the
 * only role permitted DDL, mirroring Flyway itself) and re-enabled in a
 * finally block. Application/runtime roles can never bypass the protection.
 * This reset is a local/CI fixture operation, never a production workflow.
 */
@Component
public class StationOperationsReset {

    /** FK-safe deletion order (children before parents). */
    private static final List<String> TABLES = List.of(
            "audit_event",
            "idempotency_record",
            "inbox_message",
            "outbox_message",
            "simulator_assignment",
            "tariff_component",
            "tariff_version",
            "tariff",
            "booking_policy_version",
            "booking_policy",
            "connector",
            "evse",
            "station_schedule_exception",
            "station_opening_period",
            "station",
            "organization_member",
            "operator_organization");

    private final JdbcClient jdbc;

    public StationOperationsReset(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Deletes every row from the W1-S1 tables; table set is fixed by ARC-022 §9. */
    public void reset() {
        disableProtection();
        try {
            for (String table : TABLES) {
                jdbc.sql("DELETE FROM station_operations." + table).update();
            }
        } finally {
            enableProtection();
        }
    }

    private void disableProtection() {
        jdbc.sql("ALTER TABLE station_operations.tariff_version "
                + "DISABLE TRIGGER tariff_version_active_immutable").update();
        jdbc.sql("ALTER TABLE station_operations.booking_policy_version "
                + "DISABLE TRIGGER booking_policy_version_active_immutable").update();
        jdbc.sql("ALTER TABLE station_operations.tariff_component "
                + "DISABLE TRIGGER tariff_component_active_protected").update();
    }

    private void enableProtection() {
        jdbc.sql("ALTER TABLE station_operations.tariff_version "
                + "ENABLE TRIGGER tariff_version_active_immutable").update();
        jdbc.sql("ALTER TABLE station_operations.booking_policy_version "
                + "ENABLE TRIGGER booking_policy_version_active_immutable").update();
        jdbc.sql("ALTER TABLE station_operations.tariff_component "
                + "ENABLE TRIGGER tariff_component_active_protected").update();
    }
}
