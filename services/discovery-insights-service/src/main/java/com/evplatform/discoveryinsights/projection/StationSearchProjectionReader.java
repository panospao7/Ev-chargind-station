package com.evplatform.discoveryinsights.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-side queries over the discovery_insights search projections
 * (public reference data only — ARC-022 §9: no account/driver/vehicle
 * identifiers in these projections).
 *
 * <p>Geo listing: SQL bounding-box prefilter (coarse lat/lon window from
 * the radius in degrees, backed by ix_station_search_location) plus an
 * exact haversine filter and distance ordering in Java. Depth filtering
 * (I1-DSC-002): connectorType/minPowerW are applied as an EXISTS subquery
 * over evse_search_projection × connector_search_projection (ACTIVE states
 * on both, exact type match when provided, max_power_w &gt;= when provided;
 * backed by ix_connector_search_type_power). totalEvses counts the
 * station's ACTIVE EVSE rows (MAJOR-1: populated on LIST responses too —
 * a correlated count subquery in the main SQL covers both the no-filter
 * and the connector-filtered path). Non-geo listing orders by display_name.
 * Limit clamped to 1..100, default 20 (public-discovery-api-v1.yaml
 * listStations).</p>
 *
 * <p>getStation returns the station's ACTIVE EVSEs (public uid + their
 * ACTIVE connectors, ordered by uid then connector type) and the
 * highest-version ACTIVE tariff_public_projection row (nullable; the
 * tariff is ORGANIZATION-SCOPED in the source domain — see
 * {@link TariffView} for the disclosed simplification). An ACTIVE EVSE
 * with no ACTIVE connectors is served with an EMPTY connectors list —
 * never a phantom connector (ConnectorView.type is contract-required
 * non-null, AC-05 honesty).</p>
 */
@Component
public class StationSearchProjectionReader {

    /** Mean Earth radius in kilometres (haversine). */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    public StationSearchProjectionReader(org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<StationSummary> listStations(BigDecimal latitude, BigDecimal longitude,
                                             Integer radiusKm, Integer limit,
                                             String connectorType, Integer minPowerW) {
        int effectiveLimit = clampLimit(limit);
        boolean geo = latitude != null && longitude != null && radiusKm != null && radiusKm > 0;
        boolean filterType = connectorType != null;
        boolean filterPower = minPowerW != null;

        // Depth EXISTS clause (parameter order: connectorType first, then
        // minPowerW — must match the binding order below)
        StringBuilder depth = new StringBuilder();
        if (filterType || filterPower) {
            depth.append("AND EXISTS (SELECT 1 ")
                    .append("FROM discovery_insights.evse_search_projection e ")
                    .append("JOIN discovery_insights.connector_search_projection c ")
                    .append("ON c.evse_ref = e.evse_ref ")
                    .append("WHERE e.station_ref = s.station_ref ")
                    .append("AND e.projection_state = 'ACTIVE' ")
                    .append("AND c.projection_state = 'ACTIVE'");
            if (filterType) {
                depth.append(" AND c.connector_type = ?");
            }
            if (filterPower) {
                depth.append(" AND c.max_power_w >= ?");
            }
            depth.append(") ");
        }
        String depthFilter = depth.toString();

        List<Object> params = new ArrayList<>();
        String sql;
        if (geo) {
            double[] box = boundingBoxDegrees(latitude.doubleValue(), longitude.doubleValue(),
                    radiusKm);
            sql = """
                    SELECT s.public_ref, s.display_name, s.address_line,
                           s.latitude, s.longitude,
                           (SELECT count(*) FROM discovery_insights.evse_search_projection e
                            WHERE e.station_ref = s.station_ref
                              AND e.projection_state = 'ACTIVE') AS total_evses
                    FROM discovery_insights.station_search_projection s
                    WHERE s.projection_state = 'ACTIVE'
                      AND s.latitude BETWEEN ? AND ?
                      AND s.longitude BETWEEN ? AND ?
                      %s
                    ORDER BY s.display_name
                    LIMIT ?
                    """.formatted(depthFilter);
            params.add(box[0]);
            params.add(box[1]);
            params.add(box[2]);
            params.add(box[3]);
        } else {
            sql = """
                    SELECT s.public_ref, s.display_name, s.address_line,
                           s.latitude, s.longitude,
                           (SELECT count(*) FROM discovery_insights.evse_search_projection e
                            WHERE e.station_ref = s.station_ref
                              AND e.projection_state = 'ACTIVE') AS total_evses
                    FROM discovery_insights.station_search_projection s
                    WHERE s.projection_state = 'ACTIVE'
                      %s
                    ORDER BY s.display_name
                    LIMIT ?
                    """.formatted(depthFilter);
        }
        if (filterType) {
            params.add(connectorType);
        }
        if (filterPower) {
            params.add(minPowerW);
        }
        params.add(effectiveLimit);

        List<Row> rows = jdbc.sql(sql)
                .params(params.toArray())
                .query((rs, i) -> new Row(
                        rs.getString("public_ref"),
                        rs.getString("display_name"),
                        rs.getString("address_line"),
                        rs.getBigDecimal("latitude"),
                        rs.getBigDecimal("longitude"),
                        rs.getInt("total_evses")))
                .list();

        if (geo) {
            // exact distance filter + ordering in Java (bounding box is only
            // a prefilter)
            rows = rows.stream()
                    .sorted(java.util.Comparator.comparingDouble(r -> distanceKm(
                            r.latitude().doubleValue(), r.longitude().doubleValue(),
                            latitude.doubleValue(), longitude.doubleValue())))
                    .filter(r -> distanceKm(r.latitude().doubleValue(), r.longitude().doubleValue(),
                            latitude.doubleValue(), longitude.doubleValue()) <= radiusKm)
                    .toList();
        }

        return rows.stream()
                .map(r -> new StationSummary(r.publicRef(), r.displayName(), r.addressLine(),
                        r.latitude(), r.longitude(), r.totalEvses()))
                .toList();
    }

    public Optional<StationDetails> getStation(String stationRef) {
        return jdbc.sql("""
                        SELECT public_ref, display_name, address_line, city, postal_code,
                               country_code, latitude, longitude, updated_at, source_version
                        FROM discovery_insights.station_search_projection
                        WHERE public_ref = ? AND projection_state = 'ACTIVE'
                        """)
                .param(stationRef)
                .query((rs, i) -> new StationDetails(
                        rs.getString("public_ref"),
                        rs.getString("display_name"),
                        rs.getString("address_line"),
                        rs.getBigDecimal("latitude"),
                        rs.getBigDecimal("longitude"),
                        rs.getString("city"),
                        rs.getString("postal_code"),
                        rs.getString("country_code"),
                        rs.getTimestamp("updated_at",
                                java.util.Calendar.getInstance(
                                        java.util.TimeZone.getTimeZone("UTC"))).toInstant(),
                        rs.getLong("source_version"),
                        0,         // totalEvses, filled by withDepth
                        List.of(), // evses, filled by withDepth
                        null))     // tariff, filled by withDepth
                .optional()
                .map(this::withDepth);
    }

    /**
     * Depth enrichment for one station row: totalEvses (COUNT of ACTIVE EVSE
     * rows), evses (public uid + their ACTIVE connectors, ordered by uid then
     * connector type), and the tariff (highest version_number among ACTIVE
     * rows; null when none is projected).
     */
    private StationDetails withDepth(StationDetails base) {
        Integer totalEvses = jdbc.sql("""
                        SELECT count(*) FROM discovery_insights.evse_search_projection
                        WHERE station_ref = (
                            SELECT station_ref FROM discovery_insights.station_search_projection
                            WHERE public_ref = ?)
                          AND projection_state = 'ACTIVE'
                        """)
                .param(base.ref())
                .query((rs, i) -> rs.getInt(1))
                .single();

        Map<String, List<ConnectorView>> connectorsByUid = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT e.evse_uid, c.connector_type, c.max_power_w
                        FROM discovery_insights.evse_search_projection e
                        JOIN discovery_insights.station_search_projection s
                          ON s.station_ref = e.station_ref
                        LEFT JOIN discovery_insights.connector_search_projection c
                          ON c.evse_ref = e.evse_ref AND c.projection_state = 'ACTIVE'
                        WHERE s.public_ref = ?
                          AND e.projection_state = 'ACTIVE'
                        ORDER BY e.evse_uid, c.connector_type
                        """)
                .param(base.ref())
                .query((rs, i) -> {
                    // connector_type/max_power_w are NOT NULL in the projection
                    // table, so a NULL here can only be the LEFT JOIN miss: an
                    // ACTIVE EVSE with no ACTIVE connectors. Register the EVSE
                    // uid with an EMPTY connectors list (do NOT skip the row —
                    // a connector-less EVSE must still appear in evses, matching
                    // totalEvses) and never fabricate a ConnectorView with a
                    // null type or 0 W (contract-invalid; AC-05 honesty).
                    String uid = rs.getString("evse_uid");
                    List<ConnectorView> connectors = connectorsByUid
                            .computeIfAbsent(uid, k -> new ArrayList<>());
                    if (rs.getString("connector_type") != null) {
                        connectors.add(new ConnectorView(
                                rs.getString("connector_type"), rs.getInt("max_power_w")));
                    }
                    return Boolean.TRUE;
                })
                .list();
        List<EvseView> evses = connectorsByUid.entrySet().stream()
                .map(entry -> new EvseView(entry.getKey(), entry.getValue()))
                .toList();

        return new StationDetails(base.ref(), base.name(), base.address(),
                base.latitude(), base.longitude(), base.city(), base.postalCode(),
                base.countryCode(), base.updatedAt(), base.sourceVersion(),
                totalEvses, evses, readTariff());
    }

    /**
     * The highest-version ACTIVE tariff_public_projection row, or null when
     * none is projected. Organization-scoped: see {@link TariffView}.
     */
    private TariffView readTariff() {
        return jdbc.sql("""
                        SELECT currency, components
                        FROM discovery_insights.tariff_public_projection
                        WHERE projection_state = 'ACTIVE'
                        ORDER BY version_number DESC
                        LIMIT 1
                        """)
                .query((rs, i) -> toTariffView(rs.getString("currency"),
                        rs.getString("components")))
                .optional()
                .orElse(null);
    }

    /** JSON parse isolated from the row mapper (readTree is checked). */
    private static TariffView toTariffView(String currency, String componentsJson) {
        List<ComponentView> components = new ArrayList<>();
        try {
            JsonNode array = MAPPER.readTree(componentsJson);
            for (JsonNode component : array) {
                components.add(new ComponentView(
                        component.path("componentKind").asText(null),
                        component.path("unit").asText(null),
                        component.path("amountMinor").asLong()));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformedProjection) {
            // the projection row was written by the consumer from a
            // validated payload; a malformed row is an operational defect,
            // not a request failure — surface it as an unchecked error
            throw new IllegalStateException(
                    "tariff_public_projection.components is not valid JSON", malformedProjection);
        }
        return new TariffView(currency, components);
    }

    static int clampLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        return Math.max(1, Math.min(100, limit));
    }

    /** Coarse lat/lon window around the centre point (bounding-box prefilter). */
    static double[] boundingBoxDegrees(double lat, double lon, int radiusKm) {
        double latDelta = radiusKm / 111.195;
        double lonDelta = radiusKm / (111.195 * Math.max(0.1, Math.cos(Math.toRadians(lat))));
        return new double[]{lat - latDelta, lat + latDelta, lon - lonDelta, lon + lonDelta};
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record Row(String publicRef, String displayName, String addressLine,
                       BigDecimal latitude, BigDecimal longitude, int totalEvses) {
    }
}
