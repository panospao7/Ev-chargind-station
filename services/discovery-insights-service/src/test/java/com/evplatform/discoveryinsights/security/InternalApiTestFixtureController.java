package com.evplatform.discoveryinsights.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * TEST-ONLY fixture (I1-IAM-002): the real internal-API business
 * controllers do not exist yet (they arrive with their owning delivery
 * tasks). This fixture exists ONLY in src/test and exercises the REAL
 * resource-server security chain (SecurityFilterChain → BearerTokenAuthenticationFilter
 * → JwtDecoder → validator chain → JwtAuthenticationConverter →
 * authorizeHttpRequests) against the two internal discovery paths from
 * discovery-insights-internal-api-v1.yaml:
 * {@code GET /stations/{stationRef}/utilization} and
 * {@code GET /insights/trends}.
 *
 * <p>Response shapes mirror the contract schemas (StationUtilization /
 * TrendDataPoint) minimally; the contract itself is unchanged and no
 * main-source controller is added (orchestrator-approved approach).</p>
 */
@RestController
public class InternalApiTestFixtureController {

    @GetMapping("/stations/{stationRef}/utilization")
    public Map<String, Object> utilization(@PathVariable String stationRef,
                                           @RequestParam(required = false) String period) {
        return Map.of(
                "stationRef", stationRef,
                "totalSessions", 42,
                "totalEnergyKwh", 123.4,
                "averageUtilizationPercent", 55.5,
                "dataPoints", List.of());
    }

    @GetMapping("/insights/trends")
    public List<Map<String, Object>> trends(@RequestParam String from,
                                            @RequestParam String to) {
        return List.of(Map.of(
                "period", from + ".." + to,
                "totalSessions", 7,
                "totalEnergyKwh", 88.8,
                "averageSessionDurationMinutes", 31.5));
    }
}
