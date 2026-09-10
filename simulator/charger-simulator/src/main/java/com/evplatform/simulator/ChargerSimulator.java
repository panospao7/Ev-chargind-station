package com.evplatform.simulator;

/**
 * Charger simulator entry point.
 *
 * Runway skeleton only (ADR-025): the real simulator — JDK WebSocket
 * protocol, SQLite queue, enrollment, failure injection — is delivered by
 * the device-integration delivery tasks against approved contracts.
 */
public final class ChargerSimulator {

    private ChargerSimulator() {
    }

    public static void main(String[] args) {
        throw new UnsupportedOperationException(
            "Charger simulator is not implemented yet; see ADR-025 and the device-integration tasks");
    }
}
