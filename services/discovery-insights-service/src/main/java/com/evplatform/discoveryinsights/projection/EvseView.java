package com.evplatform.discoveryinsights.projection;

import java.util.List;

/**
 * Public EVSE view (StationDetails.evses entries): provider-side UID plus
 * the EVSE's connectors (type + maximum power). Public reference data only
 * — no account/driver/vehicle identifiers (ARC-022 §9).
 */
public record EvseView(
        String uid,
        List<ConnectorView> connectors) {
}
