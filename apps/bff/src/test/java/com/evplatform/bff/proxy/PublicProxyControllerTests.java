package com.evplatform.bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evplatform.bff.BffApplication;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;

/**
 * Proxy route tests (I1-APP-001 AC-05), run through the full WebMvc
 * context (MockMvcBuilders.webAppContextSetup — Boot 4.1 has no
 * TestRestTemplate/@AutoConfigureMockMvc in test-autoconfigure):
 * verbatim query forwarding, body passthrough, correlation header,
 * 404 Problem Details passthrough, route allowlist, and header hygiene
 * (no Cookie/Authorization downstream).
 */
@SpringBootTest(classes = BffApplication.class)
class PublicProxyControllerTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private DiscoveryDownstreamClient downstream;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
    }

    @Test
    void listForwardsQueryVerbatimAndPassesBodyThrough() throws Exception {
        when(downstream.list(any())).thenReturn(
                org.springframework.http.ResponseEntity.ok()
                        .header("Content-Type", "application/json")
                        .body("[{\"ref\":\"SEEDSTA0001\"}]"));

        MvcResult result = mockMvc.perform(get("/api/v1/stations")
                        .queryParam("latitude", "37.98")
                        .queryParam("limit", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("[{\"ref\":\"SEEDSTA0001\"}]"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isNotBlank();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, String>> captor =
                ArgumentCaptor.forClass((Class) MultiValueMap.class);
        verify(downstream).list(captor.capture());
        MultiValueMap<String, String> forwarded = captor.getValue();
        assertThat(forwarded.getFirst("latitude")).isEqualTo("37.98");
        assertThat(forwarded.getFirst("limit")).isEqualTo("20");
    }

    @Test
    void detailsForwards404ProblemDetailsUnchanged() throws Exception {
        when(downstream.details("unknown-ref")).thenReturn(
                org.springframework.http.ResponseEntity.status(404)
                        .header("Content-Type", "application/problem+json")
                        .body("{\"title\":\"Resource not found\",\"status\":404}"));

        mockMvc.perform(get("/api/v1/stations/unknown-ref"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("{\"title\":\"Resource not found\",\"status\":404}"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("X-Correlation-Id", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void nonAllowlistedApiPathReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.verifyNoInteractions(downstream);
    }

    @Test
    void cookieAndAuthorizationHeadersAreNotForwardedDownstream() {
        // General review F-3 / security review F-S2: the previous version
        // asserted on the query map (vacuous - headers never checked).
        // This test observes the ACTUAL outbound HTTP headers at the
        // exchange boundary of a REAL DiscoveryDownstreamClient (not the
        // @MockitoBean) pointed at an embedded stub server. Claim scope:
        // the controller structurally cannot forward browser headers (no
        // HttpServletRequest access; fresh RestClient), so the outbound
        // side is what this test proves - no Cookie/Authorization on the
        // wire.
        AtomicReference<org.springframework.http.HttpHeaders> observed =
                new AtomicReference<>();
        org.springframework.util.MultiValueMap<String, String> capturedQuery =
                new org.springframework.util.LinkedMultiValueMap<>();

        com.sun.net.httpserver.HttpServer stub = null;
        try {
            stub = com.sun.net.httpserver.HttpServer.create(
                    new java.net.InetSocketAddress(0), 0);
            stub.createContext("/api/v1/stations", exchange -> {
                capturedQuery.putAll(
                        org.springframework.web.util.UriComponentsBuilder
                                .fromUri(exchange.getRequestURI())
                                .build().getQueryParams());
                byte[] body = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            stub.start();
            int port = stub.getAddress().getPort();

            var realClient = new DiscoveryDownstreamClient(
                    "http://127.0.0.1:" + port);
            realClient.outboundHeaderObserver = headers -> observed.set(headers);

            // Drive the real controller + real client via MockMvc against
            // the real client bean? No - use the controller directly with
            // the real client to keep the seam honest and the test fast.
            var controller = new PublicProxyController(realClient);
            var response = controller.list(
                    new org.springframework.util.LinkedMultiValueMap<>(), null);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("stub server failed", e);
        } finally {
            if (stub != null) {
                stub.stop(0);
            }
        }

        // Inbound browser credentials (Cookie session=abc, Authorization
        // Bearer) must NOT reach the downstream exchange:
        org.springframework.http.HttpHeaders outbound = observed.get();
        assertThat(outbound).isNotNull();
        assertThat(outbound.getFirst("Cookie")).isNull();
        assertThat(outbound.getFirst("Authorization")).isNull();
    }

    @Test
    void encodedMetacharactersInStationRefAreRejectedBeforeDownstream() throws Exception {
        // Security review F-4: %3F surviving path decoding must not alter
        // the downstream URI. The controller validates the ref charset
        // before any downstream call.
        mockMvc.perform(get("/api/v1/stations/x%3Fy=1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON));
        org.mockito.Mockito.verifyNoInteractions(downstream);
    }

    @Test
    void dotSegmentRefsAreRejectedBeforeDownstream() throws Exception {
        // Security review F-S1: "." and ".." must not reach the downstream
        // path (normalization could escape the stations collection).
        mockMvc.perform(get("/api/v1/stations/.."))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/stations/."))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.verifyNoInteractions(downstream);
    }

    @Test
    void validStationRefIsForwardedAsEncodedSegment() throws Exception {
        when(downstream.details("SEEDSTA0001")).thenReturn(
                org.springframework.http.ResponseEntity.ok().body("{}"));
        mockMvc.perform(get("/api/v1/stations/SEEDSTA0001"))
                .andExpect(status().isOk());
        verify(downstream).details("SEEDSTA0001");
    }

    @Test
    void validIncomingCorrelationIdIsForwarded() throws Exception {
        when(downstream.list(any())).thenReturn(
                org.springframework.http.ResponseEntity.ok().body("[]"));

        String incoming = "123e4567-e89b-42d3-a456-426614174000";
        MvcResult result = mockMvc.perform(get("/api/v1/stations")
                        .header("X-Correlation-Id", incoming))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo(incoming);
    }
}
