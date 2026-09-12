package com.evplatform.discoveryinsights.projection;

import java.math.BigDecimal;

/**
 * Public station summary (StationSummary schema: ref/name/address/lat/lon
 * plus the optional depth field totalEvses — the count of the station's
 * ACTIVE EVSE projection rows, populated on listStations responses).
 */
public record StationSummary(
        String ref,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        int totalEvses) {
}
