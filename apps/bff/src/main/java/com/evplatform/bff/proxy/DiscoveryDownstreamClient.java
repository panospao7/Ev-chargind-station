package com.evplatform.bff.proxy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Downstream Discovery client for the public proxy routes (I1-APP-001).
 * A clean RestClient built from {@link RestClient.Builder#baseUrl(String)}
 * — no cookies, no Authorization header, no browser state is ever copied
 * into downstream requests. Responses are returned unchanged (status,
 * Content-Type, body) so Problem Details pass through verbatim.
 */
@Component
public class DiscoveryDownstreamClient {

    private final RestClient restClient;

    /**
     * Outbound request-header observer for tests (package-private). When
     * set, every downstream request's headers are handed to this consumer
     * before execution — the honest seam that lets PublicProxyControllerTests
     * assert header hygiene (no Cookie/Authorization) at the actual HTTP
     * boundary instead of asserting on unrelated structures.
     */
    volatile java.util.function.Consumer<org.springframework.http.HttpHeaders>
            outboundHeaderObserver;

    public DiscoveryDownstreamClient(
            @Value("${discovery.base-url}") String discoveryBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(discoveryBaseUrl).build();
    }

    /**
     * GET /api/v1/stations with the given (already bound) query params.
     */
    public ResponseEntity<String> list(MultiValueMap<String, String> query) {
        String uri = UriComponentsBuilder.fromPath("/api/v1/stations")
                .queryParams(query)
                .encode()
                .build()
                .toUriString();
        return request(uri);
    }

    /**
     * GET /api/v1/stations/{stationRef} (404 included — passthrough).
     */
    public ResponseEntity<String> details(String stationRef) {
        // Security review F-S1: encode the segment (defense in depth —
        // the controller already validates the charset).
        String uri = UriComponentsBuilder.fromPath("/api/v1/stations")
                .pathSegment(stationRef)
                .encode()
                .build()
                .toUriString();
        return request(uri);
    }

    private ResponseEntity<String> request(String uri) {
        return restClient.get()
                .uri(uri)
                .exchange((request, response) -> {
                    var observer = outboundHeaderObserver;
                    if (observer != null) {
                        observer.accept(request.getHeaders());
                    }
                    return toResponse(response);
                });
    }

    private static ResponseEntity<String> toResponse(ClientHttpResponse response)
            throws java.io.IOException {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
        String contentType = response.getHeaders().getFirst("Content-Type");
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        byte[] bytes = response.getBody().readAllBytes();
        return builder.body(new String(bytes, StandardCharsets.UTF_8));
    }

    /** Generates a fresh correlation id for requests without a valid one. */
    static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
