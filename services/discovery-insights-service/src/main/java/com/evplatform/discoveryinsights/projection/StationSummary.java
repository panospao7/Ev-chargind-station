package com.evplatform.discoveryinsights.projection;

import java.math.BigDecimal;

/** Public station summary (StationSummary schema: ref/name/address/lat/lon). */
public record StationSummary(
        String ref,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude) {
}
