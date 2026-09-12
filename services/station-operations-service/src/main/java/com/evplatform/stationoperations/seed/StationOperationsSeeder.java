package com.evplatform.stationoperations.seed;

import com.evplatform.stationoperations.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Deterministic, idempotent S1-01 seed for station_operations_db.
 *
 * Service-owned (AGENTS.md §4): every statement targets this service's own
 * schema; the runner connects with the STA runtime role, which provably
 * cannot reach other databases (I1-DAT-001 role separation).
 *
 * The whole seed — data plus the outbox facts (one StationPublished fact per
 * published station, one EVSEConfigurationChanged fact per EVSE, one
 * ConnectorConfigurationChanged fact per connector, one TariffPublished fact
 * for the active tariff version) — commits in a single transaction: the
 * business change and the outbox records commit together (AGENTS.md §4;
 * FR-PLT-01).
 *
 * Idempotency: fixed identifiers + ON CONFLICT DO NOTHING and the §8.1
 * unique event-fact constraint — re-running never duplicates or mutates.
 */
@Component
public class StationOperationsSeeder {

    private static final String STATION_PUBLISHED_TYPE =
            "com.evplatform.station.published.v1";
    private static final String EVSE_CONFIGURATION_CHANGED_TYPE =
            "com.evplatform.station.evse-configuration-changed.v1";
    private static final String CONNECTOR_CONFIGURATION_CHANGED_TYPE =
            "com.evplatform.station.connector-configuration-changed.v1";
    private static final String TARIFF_PUBLISHED_TYPE =
            "com.evplatform.station.tariff-published.v1";
    private static final String EVENT_SOURCE =
            "//station-operations-service";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcClient jdbc;
    private final OutboxWriter outbox;
    private final TransactionOperations tx;

    public StationOperationsSeeder(JdbcClient jdbc, OutboxWriter outbox,
                                   TransactionOperations tx) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.tx = tx;
    }

    /** Applies the canonical dataset atomically; safe to run repeatedly. */
    public void seed() {
        tx.executeWithoutResult(status -> {
            insertOrganization();
            insertMembers();
            insertStation(SeedDataset.STATION_A_REF, SeedDataset.STATION_A_PUBLIC_REF,
                    SeedDataset.STATION_A_NAME, SeedDataset.STATION_A_ADDRESS,
                    SeedDataset.STATION_A_CITY, SeedDataset.STATION_A_POSTAL,
                    SeedDataset.STATION_A_LAT, SeedDataset.STATION_A_LON);
            insertStation(SeedDataset.STATION_B_REF, SeedDataset.STATION_B_PUBLIC_REF,
                    SeedDataset.STATION_B_NAME, SeedDataset.STATION_B_ADDRESS,
                    SeedDataset.STATION_B_CITY, SeedDataset.STATION_B_POSTAL,
                    SeedDataset.STATION_B_LAT, SeedDataset.STATION_B_LON);
            insertEvse(SeedDataset.EVSE_A1);
            insertEvse(SeedDataset.EVSE_A2);
            insertEvse(SeedDataset.EVSE_B1);
            insertEvse(SeedDataset.EVSE_B2);
            insertConnectors(SeedDataset.EVSE_A1);
            insertConnectors(SeedDataset.EVSE_A2);
            insertConnectors(SeedDataset.EVSE_B1);
            insertConnectors(SeedDataset.EVSE_B2);
            insertOpeningHours(SeedDataset.STATION_A_REF);
            insertOpeningHours(SeedDataset.STATION_B_REF);
            insertTariff();
            insertPolicy();
            insertSimulatorAssignment(SeedDataset.SIM_A1);
            insertSimulatorAssignment(SeedDataset.SIM_A2);
            insertSimulatorAssignment(SeedDataset.SIM_B1);
            insertSimulatorAssignment(SeedDataset.SIM_B2);
            emitStationPublished(SeedDataset.STATION_A_REF,
                    SeedDataset.STATION_A_PUBLIC_REF, SeedDataset.STATION_A_NAME,
                    SeedDataset.STATION_A_ADDRESS, SeedDataset.STATION_A_CITY,
                    SeedDataset.STATION_A_POSTAL, SeedDataset.STATION_A_LAT,
                    SeedDataset.STATION_A_LON,
                    UUID.fromString("00000000-0000-0000-0000-000000000301"));
            emitStationPublished(SeedDataset.STATION_B_REF,
                    SeedDataset.STATION_B_PUBLIC_REF, SeedDataset.STATION_B_NAME,
                    SeedDataset.STATION_B_ADDRESS, SeedDataset.STATION_B_CITY,
                    SeedDataset.STATION_B_POSTAL, SeedDataset.STATION_B_LAT,
                    SeedDataset.STATION_B_LON,
                    UUID.fromString("00000000-0000-0000-0000-000000000302"));
            // deterministic fact order within the transaction: published
            // facts first, then EVSEs, then connectors, then the tariff
            emitEvseConfigurationChanged(SeedDataset.EVSE_A1,
                    UUID.fromString("00000000-0000-0000-0000-000000000303"));
            emitEvseConfigurationChanged(SeedDataset.EVSE_A2,
                    UUID.fromString("00000000-0000-0000-0000-000000000304"));
            emitEvseConfigurationChanged(SeedDataset.EVSE_B1,
                    UUID.fromString("00000000-0000-0000-0000-000000000305"));
            emitEvseConfigurationChanged(SeedDataset.EVSE_B2,
                    UUID.fromString("00000000-0000-0000-0000-000000000306"));
            emitConnectorConfigurationChanged(SeedDataset.EVSE_A1,
                    SeedDataset.CONNECTOR_DC, SeedDataset.CONNECTOR_DC_POWER_W,
                    UUID.fromString("00000000-0000-0000-0000-000000000307"));
            emitConnectorConfigurationChanged(SeedDataset.EVSE_A1,
                    SeedDataset.CONNECTOR_AC, SeedDataset.CONNECTOR_AC_POWER_W,
                    UUID.fromString("00000000-0000-0000-0000-000000000308"));
            emitConnectorConfigurationChanged(SeedDataset.EVSE_A2,
                    SeedDataset.CONNECTOR_DC, SeedDataset.CONNECTOR_DC_POWER_W,
                    UUID.fromString("00000000-0000-0000-0000-000000000309"));
            emitConnectorConfigurationChanged(SeedDataset.EVSE_A2,
                    SeedDataset.CONNECTOR_AC, SeedDataset.CONNECTOR_AC_POWER_W,
                    UUID.fromString("00000000-0000-0000-0000-000000000310"));
            emitConnectorConfigurationChanged(SeedDataset.EVSE_B1,
                    SeedDataset.CONNECTOR_DC, SeedDataset.CONNECTOR_DC_POWER_W,
                    UUID.fromString("00000000-0000-0000-0000-000000000311"));
            emitConnectorConfigurationChanged(SeedDataset.EVSE_B1,
                    SeedDataset.CONNECTOR_AC, SeedDataset.CONNECTOR_AC_POWER_W,
                    UUID.fromString("00000000-0000-0000-0000-000000000312"));
            emitConnectorConfigurationChanged(SeedDataset.EVSE_B2,
                    SeedDataset.CONNECTOR_DC, SeedDataset.CONNECTOR_DC_POWER_W,
                    UUID.fromString("00000000-0000-0000-0000-000000000313"));
            emitConnectorConfigurationChanged(SeedDataset.EVSE_B2,
                    SeedDataset.CONNECTOR_AC, SeedDataset.CONNECTOR_AC_POWER_W,
                    UUID.fromString("00000000-0000-0000-0000-000000000314"));
            emitTariffPublished(
                    UUID.fromString("00000000-0000-0000-0000-000000000315"));
        });
    }

    /** CloudEvents 1.0 envelope carrying the station reference payload. */
    private void emitStationPublished(UUID stationRef, String publicRef, String name,
                                      String address, String city, String postal,
                                      String lat, String lon, UUID messageId) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("stationRef", stationRef.toString());
        data.put("publicRef", publicRef);
        data.put("displayName", name);
        data.put("addressLine", address);
        data.put("city", city);
        data.put("postalCode", postal);
        data.put("countryCode", "GR");
        data.put("latitude", new java.math.BigDecimal(lat));
        data.put("longitude", new java.math.BigDecimal(lon));

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", STATION_PUBLISHED_TYPE);
        envelope.put("source", EVENT_SOURCE);
        envelope.put("id", messageId.toString());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("subject", "station/" + publicRef);
        envelope.set("data", data);

        outbox.append(messageId, "EVENT", STATION_PUBLISHED_TYPE, "Station",
                stationRef, 0, messageId, null, "BUSINESS",
                (JsonNode) envelope, Instant.now());
    }

    /**
     * EVSE configuration fact (ARC-020 §6): the public EVSE identity — ref,
     * owning station, provider-side UID. Aggregate version 0: the seed
     * publishes the initial configuration once, and the §8.1 event-fact
     * uniqueness keeps re-seeds idempotent.
     */
    private void emitEvseConfigurationChanged(SeedDataset.Evse evse, UUID messageId) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("evseRef", evse.ref().toString());
        data.put("stationRef", evse.stationRef().toString());
        data.put("evseUid", evse.uid());

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", EVSE_CONFIGURATION_CHANGED_TYPE);
        envelope.put("source", EVENT_SOURCE);
        envelope.put("id", messageId.toString());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("subject", "evse/" + evse.uid());
        envelope.set("data", data);

        outbox.append(messageId, "EVENT", EVSE_CONFIGURATION_CHANGED_TYPE,
                "EVSE", evse.ref(), 0, messageId, null, "BUSINESS",
                (JsonNode) envelope, Instant.now());
    }

    /**
     * Connector configuration fact (ARC-020 §6): type and maximum power for
     * one connector of the given EVSE. The connector ref is derived exactly
     * as {@link #insertConnectors(SeedDataset.Evse)} derives it, so the fact
     * can never reference a connector the seed did not persist.
     */
    private void emitConnectorConfigurationChanged(SeedDataset.Evse evse,
                                                   String connectorType,
                                                   int maxPowerW, UUID messageId) {
        UUID connectorRef = connectorRef(evse.uid(), connectorType);
        ObjectNode data = MAPPER.createObjectNode();
        data.put("connectorRef", connectorRef.toString());
        data.put("evseRef", evse.ref().toString());
        data.put("connectorType", connectorType);
        data.put("maxPowerW", maxPowerW);

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", CONNECTOR_CONFIGURATION_CHANGED_TYPE);
        envelope.put("source", EVENT_SOURCE);
        envelope.put("id", messageId.toString());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("subject", "connector/" + connectorRef);
        envelope.set("data", data);

        outbox.append(messageId, "EVENT", CONNECTOR_CONFIGURATION_CHANGED_TYPE,
                "Connector", connectorRef, 0, messageId, null, "BUSINESS",
                (JsonNode) envelope, Instant.now());
    }

    /**
     * TariffPublished fact (ARC-020 §6): the seed's single ACTIVE tariff
     * version with its two components. Emitted once per seed dataset; the
     * §8.1 uniqueness keys it on the immutable tariff version.
     */
    private void emitTariffPublished(UUID messageId) {
        ObjectNode energy = MAPPER.createObjectNode();
        energy.put("componentKind", SeedDataset.COMPONENT_ENERGY_KIND);
        energy.put("unit", SeedDataset.COMPONENT_ENERGY_UNIT);
        energy.put("amountMinor", SeedDataset.COMPONENT_ENERGY_AMOUNT_MINOR);
        ObjectNode occupancy = MAPPER.createObjectNode();
        occupancy.put("componentKind", SeedDataset.COMPONENT_OCCUPANCY_KIND);
        occupancy.put("unit", SeedDataset.COMPONENT_OCCUPANCY_UNIT);
        occupancy.put("amountMinor", SeedDataset.COMPONENT_OCCUPANCY_AMOUNT_MINOR);
        ObjectNode data = MAPPER.createObjectNode();
        data.put("tariffRef", SeedDataset.TARIFF_REF.toString());
        data.put("tariffVersionRef", SeedDataset.TARIFF_VERSION_REF.toString());
        data.put("versionNumber", SeedDataset.TARIFF_VERSION_NUMBER);
        data.put("currency", SeedDataset.TARIFF_CURRENCY);
        data.set("components", MAPPER.createArrayNode().add(energy).add(occupancy));

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("type", TARIFF_PUBLISHED_TYPE);
        envelope.put("source", EVENT_SOURCE);
        envelope.put("id", messageId.toString());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("subject", "tariff/" + SeedDataset.TARIFF_VERSION_REF);
        envelope.set("data", data);

        outbox.append(messageId, "EVENT", TARIFF_PUBLISHED_TYPE,
                "TariffVersion", SeedDataset.TARIFF_VERSION_REF, 0, messageId,
                null, "BUSINESS", (JsonNode) envelope, Instant.now());
    }

    /** Connector ref derivation shared by persistence and event emission. */
    static UUID connectorRef(String evseUid, String connectorType) {
        return UUID.nameUUIDFromBytes(
                (evseUid + "|" + connectorType).getBytes());
    }

    private void insertOrganization() {
        jdbc.sql("""
                INSERT INTO station_operations.operator_organization
                    (organization_ref, legal_name, state)
                VALUES (?, ?, ?)
                ON CONFLICT (organization_ref) DO NOTHING
                """).param(SeedDataset.ORGANIZATION_REF)
                .param(SeedDataset.ORGANIZATION_NAME)
                .param(SeedDataset.ORGANIZATION_STATE)
                .update();
    }

    private void insertMembers() {
        jdbc.sql("""
                INSERT INTO station_operations.organization_member
                    (member_ref, organization_ref, display_name, member_role)
                VALUES (?, ?, ?, 'ADMINISTRATOR'), (?, ?, ?, 'OPERATOR')
                ON CONFLICT (member_ref) DO NOTHING
                """)
                .param(SeedDataset.MEMBER_ADMIN_REF)
                .param(SeedDataset.ORGANIZATION_REF)
                .param("Seed Fixture Administrator")
                .param(SeedDataset.MEMBER_OPERATOR_REF)
                .param(SeedDataset.ORGANIZATION_REF)
                .param("Seed Fixture Operator")
                .update();
    }

    private void insertStation(UUID ref, String publicRef, String name,
                               String address, String city, String postal,
                               String lat, String lon) {
        jdbc.sql("""
                INSERT INTO station_operations.station
                    (station_ref, organization_ref, public_ref, display_name, state,
                     address_line, city, postal_code, country_code, latitude, longitude)
                VALUES (?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, 'GR', ?, ?)
                ON CONFLICT (station_ref) DO NOTHING
                """)
                .param(ref).param(SeedDataset.ORGANIZATION_REF).param(publicRef)
                .param(name).param(address).param(city).param(postal)
                .param(new java.math.BigDecimal(lat))
                .param(new java.math.BigDecimal(lon))
                .update();
    }

    private void insertEvse(SeedDataset.Evse evse) {
        jdbc.sql("""
                INSERT INTO station_operations.evse (evse_ref, station_ref, evse_uid, state)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (evse_ref) DO NOTHING
                """)
                .param(evse.ref()).param(evse.stationRef()).param(evse.uid())
                .param(SeedDataset.EVSE_STATE)
                .update();
    }

    private void insertConnectors(SeedDataset.Evse evse) {
        jdbc.sql("""
                INSERT INTO station_operations.connector
                    (connector_ref, evse_ref, connector_type, max_power_w)
                VALUES (?, ?, ?, ?), (?, ?, ?, ?)
                ON CONFLICT (connector_ref) DO NOTHING
                """)
                .param(connectorRef(evse.uid(), SeedDataset.CONNECTOR_DC))
                .param(evse.ref()).param(SeedDataset.CONNECTOR_DC)
                .param(SeedDataset.CONNECTOR_DC_POWER_W)
                .param(connectorRef(evse.uid(), SeedDataset.CONNECTOR_AC))
                .param(evse.ref()).param(SeedDataset.CONNECTOR_AC)
                .param(SeedDataset.CONNECTOR_AC_POWER_W)
                .update();
    }

    private void insertOpeningHours(UUID stationRef) {
        for (int weekday = 0; weekday <= 6; weekday++) {
            jdbc.sql("""
                    INSERT INTO station_operations.station_opening_period
                        (period_ref, station_ref, weekday, opens_at, closes_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (period_ref) DO NOTHING
                    """)
                    .param(UUID.nameUUIDFromBytes(
                            (stationRef + "|hours|" + weekday).getBytes()))
                    .param(stationRef).param((short) weekday)
                    .param(java.time.LocalTime.MIN)
                    .param(java.time.LocalTime.MAX)
                    .update();
        }
    }

    private void insertTariff() {
        jdbc.sql("""
                INSERT INTO station_operations.tariff
                    (tariff_ref, organization_ref, display_name, currency)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tariff_ref) DO NOTHING
                """)
                .param(SeedDataset.TARIFF_REF)
                .param(SeedDataset.ORGANIZATION_REF)
                .param(SeedDataset.TARIFF_NAME)
                .param(SeedDataset.TARIFF_CURRENCY)
                .update();
        // canonical ordering: version starts DRAFT, components are inserted,
        // then the version is activated (guarded — re-runs must not touch the
        // already-ACTIVE row, whose mutation the protection trigger rejects)
        jdbc.sql("""
                INSERT INTO station_operations.tariff_version
                    (tariff_version_ref, tariff_ref, version_number, state, valid_from)
                VALUES (?, ?, ?, 'DRAFT', ?)
                ON CONFLICT (tariff_version_ref) DO NOTHING
                """)
                .param(SeedDataset.TARIFF_VERSION_REF)
                .param(SeedDataset.TARIFF_REF)
                .param(SeedDataset.TARIFF_VERSION_NUMBER)
                .param(Timestamp.from(SeedDataset.TARIFF_VALID_FROM))
                .update();
        insertComponent(SeedDataset.COMPONENT_ENERGY_REF,
                SeedDataset.TARIFF_VERSION_REF, SeedDataset.COMPONENT_ENERGY_KIND,
                SeedDataset.COMPONENT_ENERGY_UNIT, SeedDataset.COMPONENT_ENERGY_AMOUNT_MINOR);
        insertComponent(SeedDataset.COMPONENT_OCCUPANCY_REF,
                SeedDataset.TARIFF_VERSION_REF, SeedDataset.COMPONENT_OCCUPANCY_KIND,
                SeedDataset.COMPONENT_OCCUPANCY_UNIT, SeedDataset.COMPONENT_OCCUPANCY_AMOUNT_MINOR);
        jdbc.sql("""
                UPDATE station_operations.tariff_version
                SET state = 'ACTIVE'
                WHERE tariff_version_ref = ? AND state = 'DRAFT'
                """)
                .param(SeedDataset.TARIFF_VERSION_REF)
                .update();
    }

    /**
     * Existence-guarded insert: BEFORE-row triggers fire even for rows that
     * would conflict, so an EXISTS check is required to keep re-runs silent
     * while the parent version is ACTIVE.
     */
    private void insertComponent(UUID ref, UUID versionRef, String kind,
                                 String unit, long amountMinor) {
        boolean exists = Boolean.TRUE.equals(jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM station_operations.tariff_component
                               WHERE component_ref = ?)
                """)
                .param(ref)
                .query(Boolean.class)
                .single());
        if (exists) {
            return;
        }
        jdbc.sql("""
                INSERT INTO station_operations.tariff_component
                    (component_ref, tariff_version_ref, component_kind, unit, amount_minor)
                VALUES (?, ?, ?, ?, ?)
                """)
                .param(ref).param(versionRef).param(kind).param(unit).param(amountMinor)
                .update();
    }

    private void insertPolicy() {
        jdbc.sql("""
                INSERT INTO station_operations.booking_policy
                    (policy_ref, organization_ref, display_name)
                VALUES (?, ?, ?)
                ON CONFLICT (policy_ref) DO NOTHING
                """)
                .param(SeedDataset.POLICY_REF)
                .param(SeedDataset.ORGANIZATION_REF)
                .param(SeedDataset.POLICY_NAME)
                .update();
        jdbc.sql("""
                INSERT INTO station_operations.booking_policy_version
                    (policy_version_ref, policy_ref, version_number, state,
                     hold_duration_seconds, slot_increment_minutes, min_duration_minutes,
                     max_duration_minutes, advance_booking_days, near_term_horizon_minutes,
                     freshness_threshold_seconds, late_arrival_grace_minutes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (policy_version_ref) DO NOTHING
                """)
                .param(SeedDataset.POLICY_VERSION_REF)
                .param(SeedDataset.POLICY_REF)
                .param(SeedDataset.POLICY_VERSION_NUMBER)
                .param(SeedDataset.POLICY_VERSION_STATE)
                .param(SeedDataset.HOLD_DURATION_SECONDS)
                .param(SeedDataset.SLOT_INCREMENT_MINUTES)
                .param(SeedDataset.MIN_DURATION_MINUTES)
                .param(SeedDataset.MAX_DURATION_MINUTES)
                .param(SeedDataset.ADVANCE_BOOKING_DAYS)
                .param(SeedDataset.NEAR_TERM_HORIZON_MINUTES)
                .param(SeedDataset.FRESHNESS_THRESHOLD_SECONDS)
                .param(SeedDataset.LATE_ARRIVAL_GRACE_MINUTES)
                .update();
    }

    private void insertSimulatorAssignment(SeedDataset.SimulatorAssignment a) {
        jdbc.sql("""
                INSERT INTO station_operations.simulator_assignment
                    (assignment_ref, evse_ref, simulator_device_ref, assigned_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (assignment_ref) DO NOTHING
                """)
                .param(a.assignmentRef()).param(a.evseRef()).param(a.deviceRef())
                .param(Timestamp.from(Instant.now()))
                .update();
    }
}
