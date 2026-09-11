package com.evplatform.stationoperations.seed;

import java.time.Instant;
import java.util.UUID;

/**
 * The canonical S1-01 seed dataset: fixed, deterministic identifiers and the
 * GOV-007 §3 S1-03 constants.
 *
 * All station/organization names are clearly-marked FIXTURE data — they are
 * not claims about real operators. No credentials, tokens, or personal data
 * appear anywhere in the dataset.
 */
public final class SeedDataset {

    // ── organization ────────────────────────────────────────────────────────
    public static final UUID ORGANIZATION_REF =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String ORGANIZATION_NAME = "Seed Fixture — Hellas Charge Coop";
    public static final String ORGANIZATION_STATE = "ACTIVE";

    public static final UUID MEMBER_ADMIN_REF =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID MEMBER_OPERATOR_REF =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    // ── stations (Greek fixture coordinates) ───────────────────────────────
    public static final UUID STATION_A_REF =
            UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final String STATION_A_PUBLIC_REF = "SEEDSTA0001";
    public static final String STATION_A_NAME = "Seed Fixture — Athens Center";
    public static final String STATION_A_STATE = "PUBLISHED";
    public static final String STATION_A_ADDRESS = "Fixture Street 1";
    public static final String STATION_A_CITY = "Athens";
    public static final String STATION_A_POSTAL = "10431";
    public static final String STATION_A_LAT = "37.983810";
    public static final String STATION_A_LON = "23.727540";

    public static final UUID STATION_B_REF =
            UUID.fromString("00000000-0000-0000-0000-000000000011");
    public static final String STATION_B_PUBLIC_REF = "SEEDSTA0002";
    public static final String STATION_B_NAME = "Seed Fixture — Thessaloniki Waterfront";
    public static final String STATION_B_STATE = "PUBLISHED";
    public static final String STATION_B_ADDRESS = "Fixture Avenue 2";
    public static final String STATION_B_CITY = "Thessaloniki";
    public static final String STATION_B_POSTAL = "54626";
    public static final String STATION_B_LAT = "40.640060";
    public static final String STATION_B_LON = "22.944420";

    // ── EVSEs: two per station, ACTIVE ──────────────────────────────────────
    public record Evse(UUID ref, UUID stationRef, String uid) {
    }

    public static final Evse EVSE_A1 = new Evse(
            UUID.fromString("00000000-0000-0000-0000-0000000000a1"), STATION_A_REF, "GR*SEED*A1");
    public static final Evse EVSE_A2 = new Evse(
            UUID.fromString("00000000-0000-0000-0000-0000000000a2"), STATION_A_REF, "GR*SEED*A2");
    public static final Evse EVSE_B1 = new Evse(
            UUID.fromString("00000000-0000-0000-0000-0000000000b1"), STATION_B_REF, "GR*SEED*B1");
    public static final Evse EVSE_B2 = new Evse(
            UUID.fromString("00000000-0000-0000-0000-0000000000b2"), STATION_B_REF, "GR*SEED*B2");

    public static final String EVSE_STATE = "ACTIVE";

    // ── connectors: two power combinations per EVSE ────────────────────────
    public static final String CONNECTOR_DC = "CCS";
    public static final int CONNECTOR_DC_POWER_W = 150_000;
    public static final String CONNECTOR_AC = "TYPE2";
    public static final int CONNECTOR_AC_POWER_W = 22_000;

    // ── tariff (one ACTIVE version, minor currency units) ──────────────────
    public static final UUID TARIFF_REF =
            UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    public static final String TARIFF_NAME = "Seed Standard Tariff";
    public static final String TARIFF_CURRENCY = "EUR";
    public static final UUID TARIFF_VERSION_REF =
            UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    public static final int TARIFF_VERSION_NUMBER = 1;
    public static final String TARIFF_VERSION_STATE = "ACTIVE";
    public static final Instant TARIFF_VALID_FROM = Instant.parse("2026-01-01T00:00:00Z");
    public static final String COMPONENT_ENERGY_KIND = "ENERGY_PER_KWH";
    public static final String COMPONENT_ENERGY_UNIT = "KWH";
    public static final long COMPONENT_ENERGY_AMOUNT_MINOR = 480; // €0.48 / kWh
    public static final UUID COMPONENT_ENERGY_REF =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    public static final String COMPONENT_OCCUPANCY_KIND = "OCCUPANCY_PER_MINUTE";
    public static final String COMPONENT_OCCUPANCY_UNIT = "MINUTE";
    public static final long COMPONENT_OCCUPANCY_AMOUNT_MINOR = 10; // €0.10 / min
    public static final UUID COMPONENT_OCCUPANCY_REF =
            UUID.fromString("00000000-0000-0000-0000-000000000102");

    // ── booking policy (one ACTIVE version with the S1-03 constants) ───────
    public static final UUID POLICY_REF =
            UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    public static final String POLICY_NAME = "Seed Default Booking Policy";
    public static final UUID POLICY_VERSION_REF =
            UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    public static final int POLICY_VERSION_NUMBER = 1;
    public static final String POLICY_VERSION_STATE = "ACTIVE";

    public static final int HOLD_DURATION_SECONDS = 300;          // 5-minute hold
    public static final int SLOT_INCREMENT_MINUTES = 15;          // 15-minute increments
    public static final int MIN_DURATION_MINUTES = 15;            // 15-minute minimum
    public static final int MAX_DURATION_MINUTES = 240;           // 4-hour maximum
    public static final int ADVANCE_BOOKING_DAYS = 14;            // 14-day advance limit
    public static final int NEAR_TERM_HORIZON_MINUTES = 60;       // 60-minute horizon
    public static final int FRESHNESS_THRESHOLD_SECONDS = 300;    // 300-second freshness
    public static final int LATE_ARRIVAL_GRACE_MINUTES = 15;      // 15-minute grace (S1-07)

    // ── simulator assignments (device references only — no credentials) ────
    public record SimulatorAssignment(UUID assignmentRef, UUID evseRef, UUID deviceRef) {
    }

    public static final SimulatorAssignment SIM_A1 = new SimulatorAssignment(
            UUID.fromString("00000000-0000-0000-0000-0000000001a1"), EVSE_A1.ref(),
            UUID.fromString("00000000-0000-0000-0000-0000000002a1"));
    public static final SimulatorAssignment SIM_A2 = new SimulatorAssignment(
            UUID.fromString("00000000-0000-0000-0000-0000000001a2"), EVSE_A2.ref(),
            UUID.fromString("00000000-0000-0000-0000-0000000002a2"));
    public static final SimulatorAssignment SIM_B1 = new SimulatorAssignment(
            UUID.fromString("00000000-0000-0000-0000-0000000001b1"), EVSE_B1.ref(),
            UUID.fromString("00000000-0000-0000-0000-0000000002b1"));
    public static final SimulatorAssignment SIM_B2 = new SimulatorAssignment(
            UUID.fromString("00000000-0000-0000-0000-0000000001b2"), EVSE_B2.ref(),
            UUID.fromString("00000000-0000-0000-0000-0000000002b2"));

    private SeedDataset() {
    }
}
