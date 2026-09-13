package com.evplatform.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Browser-facing BFF entry point.
 *
 * I1-APP-001: minimal public proxy per the task packet — two read-only
 * route allowlist entries ({@code GET /api/v1/stations} and
 * {@code GET /api/v1/stations/{stationRef}}) forwarding to the Discovery
 * service with correlation-id handling and verbatim Problem Details
 * passthrough. Implemented as plain WebMvc routes; the approved Gateway +
 * Security stack (Spring Cloud Gateway Server Web MVC + Spring Security
 * OAuth2 Client, ADR set §7) is introduced by the identity/BFF delivery
 * task together with the opaque BFF-session design — never by ad-hoc
 * additions here.
 */
@SpringBootApplication
public class BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
