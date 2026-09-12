package com.evplatform.discoveryinsights.projection;

/**
 * Public connector view (StationDetails.evses[].connectors entries): type
 * and maximum power in watts. Public reference data only (ARC-022 §9).
 */
public record ConnectorView(
        String type,
        int maxPowerW) {
}
