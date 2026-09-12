package com.evplatform.discoveryinsights.api;

import com.evplatform.discoveryinsights.projection.StationDetails;
import com.evplatform.discoveryinsights.projection.StationSearchProjectionReader;
import com.evplatform.discoveryinsights.projection.StationSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public discovery API (public-discovery-api-v1.yaml): listStations and
 * getStationDetails. Anonymous access per the contract's security schemes
 * — no Spring Security in this slice (task packet decision).
 *
 * <p>404 problem placement (disclosed): the problem-details.json schema has
 * no top-level "code" property, so the stable code RESOURCE_NOT_FOUND is
 * carried in the `type` URI per RFC 9457 style
 * (https://api.evplatform.example/problems/resource-not-found), title/status/
 * detail follow the schema. That code→URI mapping is a code-level RFC 9457
 * convention: RESOURCE_NOT_FOUND is registered in problem-codes-v1.yaml
 * (owner-approved scope extension), but the registry does not anchor type
 * URIs — URI anchoring in the registry is booked as part of the follow-up
 * contract task for the remaining ARC-003 §14 general codes.</p>
 */
@RestController
@RequestMapping("/api/v1/stations")
public class PublicStationController {

    private final StationSearchProjectionReader reader;

    public PublicStationController(StationSearchProjectionReader reader) {
        this.reader = reader;
    }

    @GetMapping
    public List<StationSummary> listStations(
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer limit) {
        return reader.listStations(latitude, longitude, radius, limit);
    }

    @GetMapping("/{stationRef}")
    public ResponseEntity<?> getStationDetails(@PathVariable String stationRef) {
        return reader.getStation(stationRef)
                .<ResponseEntity<?>>map(details -> ResponseEntity.ok(details))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(notFound(stationRef)));
    }

    private static ProblemDetail notFound(String stationRef) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "Station " + stationRef + " does not exist.");
        problem.setType(java.net.URI.create(
                "https://api.evplatform.example/problems/resource-not-found"));
        problem.setTitle("Resource not found");
        return problem;
    }
}
