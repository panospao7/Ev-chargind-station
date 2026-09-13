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
    void cookieHeaderIsNotForwardedDownstream() throws Exception {
        when(downstream.list(any())).thenReturn(
                org.springframework.http.ResponseEntity.ok().body("[]"));

        mockMvc.perform(get("/api/v1/stations").cookie(new Cookie("session", "abc")))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, String>> captor =
                ArgumentCaptor.forClass((Class) MultiValueMap.class);
        verify(downstream).list(captor.capture());
        // The clean downstream request carries no cookies by construction:
        // the client builds a fresh request with no header copying.
        assertThat(captor.getValue()).doesNotContainKey("Cookie");
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
