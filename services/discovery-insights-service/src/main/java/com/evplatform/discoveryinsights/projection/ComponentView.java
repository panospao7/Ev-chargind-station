package com.evplatform.discoveryinsights.projection;

/**
 * One tariff price component (StationDetails.tariff.components entries):
 * component kind, unit and the minor-unit amount. Public reference data
 * only (ARC-022 §9).
 */
public record ComponentView(
        String kind,
        String unit,
        long amountMinor) {
}
