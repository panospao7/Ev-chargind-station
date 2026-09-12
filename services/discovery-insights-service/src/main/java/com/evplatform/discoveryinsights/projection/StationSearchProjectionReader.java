package com.evplatform.discoveryinsights.projection;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Read-side queries over discovery_insights.station_search_projection
 * (public reference data only — ARC-022 §9: no account/driver/vehicle
 * identifiers in this projection).
 *
 * <p>Geo listing: SQL bounding-box prefilter (coarse lat/lon window from
 * the radius in degrees, backed by ix_station_search_location) plus an
 * exact haversine filter and distance ordering in Java. Non-geo listing
 * orders by display_name. Limit clamped to 1..100, default 20
 * (public-discovery-api-v1.yaml listStations).</p>
 */
@Component
public class StationSearchProjectionReader {

    /** Mean Earth radius in kilometres (haversine). */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    public StationSearchProjectionReader(org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<StationSummary> listStations(BigDecimal latitude, BigDecimal longitude,
                                             Integer radiusKm, Integer limit) {
        int effectiveLimit = clampLimit(limit);
        boolean geo = latitude != null && longitude != null && radiusKm != null && radiusKm > 0;

        List<Row> rows;
        if (geo) {
            double[] box = boundingBoxDegrees(latitude.doubleValue(), longitude.doubleValue(),
                    radiusKm);
            rows = jdbc.sql("""
                            SELECT public_ref, display_name, address_line, latitude, longitude
                            FROM discovery_insights.station_search_projection
                            WHERE projection_state = 'ACTIVE'
                              AND latitude BETWEEN ? AND ?
                              AND longitude BETWEEN ? AND ?
                            ORDER BY display_name
                            LIMIT ?
                            """)
                    .param(box[0]).param(box[1]).param(box[2]).param(box[3])
                    .param(effectiveLimit)
                    .query((rs, i) -> new Row(
                            rs.getString("public_ref"),
                            rs.getString("display_name"),
                            rs.getString("address_line"),
                            rs.getBigDecimal("latitude"),
                            rs.getBigDecimal("longitude")))
                    .list();
            // exact distance filter + ordering in Java (bounding box is only
            // a prefilter)
            rows = rows.stream()
                    .sorted(java.util.Comparator.comparingDouble(r -> distanceKm(
                            r.latitude().doubleValue(), r.longitude().doubleValue(),
                            latitude.doubleValue(), longitude.doubleValue())))
                    .filter(r -> distanceKm(r.latitude().doubleValue(), r.longitude().doubleValue(),
                            latitude.doubleValue(), longitude.doubleValue()) <= radiusKm)
                    .toList();
        } else {
            rows = jdbc.sql("""
                            SELECT public_ref, display_name, address_line, latitude, longitude
                            FROM discovery_insights.station_search_projection
                            WHERE projection_state = 'ACTIVE'
                            ORDER BY display_name
                            LIMIT ?
                            """)
                    .param(effectiveLimit)
                    .query((rs, i) -> new Row(
                            rs.getString("public_ref"),
                            rs.getString("display_name"),
                            rs.getString("address_line"),
                            rs.getBigDecimal("latitude"),
                            rs.getBigDecimal("longitude")))
                    .list();
        }

        return rows.stream()
                .map(r -> new StationSummary(r.publicRef(), r.displayName(), r.addressLine(),
                        r.latitude(), r.longitude()))
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
                        rs.getLong("source_version")))
                .optional();
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
                       BigDecimal latitude, BigDecimal longitude) {
    }
}
