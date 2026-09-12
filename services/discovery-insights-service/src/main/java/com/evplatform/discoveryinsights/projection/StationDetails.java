package com.evplatform.discoveryinsights.projection;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public station details (StationDetails schema). sourceVersion is the
 * source aggregate version of the last applied event — operational
 * metadata, not an account/driver/vehicle identifier.
 */
public record StationDetails(
        String ref,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String city,
        String postalCode,
        String countryCode,
        Instant updatedAt,
        long sourceVersion) {
}
