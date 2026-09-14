package com.evplatform.stationoperations.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * TEST-ONLY fixture (I1-IAM-002): the real internal booking-operations
 * controllers do not exist yet (they arrive with their owning delivery
 * tasks). This fixture exists ONLY in src/test and exercises the REAL
 * resource-server security chain (SecurityFilterChain →
 * BearerTokenAuthenticationFilter → JwtDecoder → validator chain →
 * JwtAuthenticationConverter → authorizeHttpRequests) against the three
 * internal STA paths from station-operations-internal-api-v1.yaml:
 * {@code POST /internal/v1/booking-operations/impact-previews},
 * {@code GET /internal/v1/booking-operations/capacity-restrictions/{ref}},
 * and {@code POST /internal/v1/booking-operations/capacity-restrictions/{ref}/reconciliation-requests}.
 *
 * <p>Response shapes mirror the contract schemas (ImpactPreviewResponse /
 * CapacityRestriction / ReconciliationRequest) minimally; the contract
 * itself is unchanged and no main-source controller is added
 * (orchestrator-approved approach, same as the Discovery fixture).</p>
 */
@RestController
public class InternalApiTestFixtureController {

    @PostMapping("/internal/v1/booking-operations/impact-previews")
    public Map<String, Object> previewImpact(
            @org.springframework.web.bind.annotation.RequestBody(required = false)
            Map<String, Object> request) {
        String stationRef = request == null || request.get("stationRef") == null
                ? "STATION-UNKNOWN" : String.valueOf(request.get("stationRef"));
        return Map.of(
                "stationRef", stationRef,
                "estimatedBookingsAffected", 3,
                "riskLevel", "low");
    }

    @GetMapping("/internal/v1/booking-operations/capacity-restrictions/{ref}")
    public Map<String, Object> getRestriction(@PathVariable String ref) {
        return Map.of(
                "ref", ref,
                "stationRef", "STATION-1",
                "status", "active",
                "startTime", "2026-09-14T00:00:00Z",
                "endTime", "2026-09-15T00:00:00Z",
                "reason", "fixture");
    }

    @PostMapping("/internal/v1/booking-operations/capacity-restrictions/{ref}/reconciliation-requests")
    public org.springframework.http.ResponseEntity<Map<String, Object>> requestReconciliation(
            @PathVariable String ref) {
        // Contract: requestReconciliation answers 202 Accepted.
        return org.springframework.http.ResponseEntity.accepted().body(Map.of(
                "ref", UUID.randomUUID().toString(),
                "restrictionRef", ref,
                "status", "pending",
                "requestedAt", Instant.now().toString()));
    }
}
