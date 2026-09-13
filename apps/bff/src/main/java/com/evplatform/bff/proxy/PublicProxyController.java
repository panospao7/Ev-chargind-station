package com.evplatform.bff.proxy;

import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public same-origin proxy for the two Discovery read operations
 * (ARC-FE-10 honored uniformly: the browser never calls Discovery
 * directly). Route allowlist: only GET /api/v1/stations and
 * GET /api/v1/stations/{stationRef} exist ΓÇö any other /api/** path 404s
 * by Spring MVC routing itself (asserted in tests).
 *
 * <p>Header hygiene: no Cookie/Authorization forwarding (a clean request
 * is built from scratch). Correlation: an incoming valid-UUID
 * X-Correlation-Id is forwarded, otherwise a fresh one is generated; the
 * response always carries an X-Correlation-Id. Problem Details responses
 * pass through with status + Content-Type + body unchanged.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class PublicProxyController {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * Public station refs are constrained to the safe charset the seed and
     * the API contract use (alphanumerics, dot, underscore, hyphen; 1-64
     * chars). Anything else - including URL-encoded metacharacters such as
     * %3F/%23/%2F that survive servlet path decoding - is rejected with 404
     * BEFORE any downstream call (security review F-4: prevents query/
     * fragment/path injection into the proxied URI).
     */
    private static final Pattern STATION_REF_PATTERN = Pattern.compile(
            "^(?!\\.+$)[A-Za-z0-9._-]{1,64}$");

    private final DiscoveryDownstreamClient downstream;

    public PublicProxyController(DiscoveryDownstreamClient downstream) {
        this.downstream = downstream;
    }

    @GetMapping("/stations")
    public ResponseEntity<String> list(
            @RequestParam MultiValueMap<String, String> query,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        // Forward the query string verbatim (already URL-decoded/re-encoded
        // losslessly by Spring's MultiValueMap binding).
        MultiValueMap<String, String> clean = new LinkedMultiValueMap<>(query);
        ResponseEntity<String> response = downstream.list(clean);
        return withCorrelationId(response, correlationId);
    }

    @GetMapping("/stations/{stationRef}")
    public ResponseEntity<String> details(
            @PathVariable String stationRef,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        if (!STATION_REF_PATTERN.matcher(stationRef).matches()) {
            // Reject before any downstream call; 404 (resource-not-found
            // semantics) rather than 400 keeps the public surface honest.
            String problem = "{\"type\":\"https://api.evplatform.example/problems/resource-not-found\",\"title\":\"Resource not found\",\"status\":404,\"detail\":\"The requested station does not exist.\"}";
            return ResponseEntity.status(404)
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        ResponseEntity<String> response = downstream.details(stationRef);
        return withCorrelationId(response, correlationId);
    }

    private static ResponseEntity<String> withCorrelationId(
            ResponseEntity<String> response, String incomingCorrelationId) {
        String correlationId =
                incomingCorrelationId != null && UUID_PATTERN.matcher(incomingCorrelationId).matches()
                        ? incomingCorrelationId
                        : DiscoveryDownstreamClient.newCorrelationId();
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header("X-Correlation-Id", correlationId)
                .body(response.getBody());
    }
}
