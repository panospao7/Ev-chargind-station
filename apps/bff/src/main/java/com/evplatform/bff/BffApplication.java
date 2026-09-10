package com.evplatform.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Browser-facing BFF entry point.
 *
 * Runway skeleton only. The approved stack (Spring Cloud Gateway Server Web
 * MVC + Spring Security OAuth2 Client per the technology ADR set §7) is
 * introduced by the identity/BFF delivery task together with the opaque
 * BFF-session design — never by ad-hoc additions here.
 */
@SpringBootApplication
public class BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
