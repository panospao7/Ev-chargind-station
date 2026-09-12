package com.evplatform.discoveryinsights.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public station details (StationDetails schema). sourceVersion is the
 * source aggregate version of the last applied event — operational
 * metadata, not an account/driver/vehicle identifier. totalEvses counts the
 * station's ACTIVE EVSE projection rows; evses carries their public UIDs
 * with connectors; tariff is the highest ACTIVE projected tariff version
 * (nullable; organization-scoped — see {@link TariffView}). NO
 * openingHours in this slice (deferred per the I1-DSC-002 task packet).
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
        long sourceVersion,
        int totalEvses,
        List<EvseView> evses,
        TariffView tariff) {
}
