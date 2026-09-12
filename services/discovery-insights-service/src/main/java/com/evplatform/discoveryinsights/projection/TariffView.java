package com.evplatform.discoveryinsights.projection;

import java.util.List;

/**
 * Public tariff view (StationDetails.tariff): currency plus the price
 * components of the highest ACTIVE tariff version. Null when no ACTIVE
 * version is projected. The tariff is ORGANIZATION-SCOPED in the source
 * domain (station-operations): Discovery projects the currently published
 * version(s) without organization attribution — the version surfaced here
 * is the platform-published one, not a per-station price. Disclosed
 * simplification per the I1-DSC-002 task packet.
 */
public record TariffView(
        String currency,
        List<ComponentView> components) {
}
