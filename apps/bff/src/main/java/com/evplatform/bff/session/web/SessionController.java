package com.evplatform.bff.session.web;

import com.evplatform.bff.security.BffSessionSecurityContextRepository;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session introspection for the browser (SEC-001 §6.3, SEC-P02 §6.3):
 * returns the assurance and lifetime context of the current BFF session.
 * The response NEVER contains the subject, sid or any token material —
 * identity is the IdP's concern; the browser only needs assurance and
 * expiry to decide on re-login/refresh UX.
 */
@RestController
public class SessionController {

    /** Response body: assurance + lifetime only (no identifiers, no tokens). */
    public record SessionResponse(boolean authenticated, String acr,
                                  Instant authenticationTime, Instant idleExpiresAt,
                                  Instant absoluteExpiresAt) {
    }

    @GetMapping("/api/v1/session")
    public ResponseEntity<SessionResponse> session() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        var principal = BffSessionSecurityContextRepository
                .currentPrincipal(authentication);
        if (principal.isEmpty()) {
            // Unauthenticated requests never reach this method in production
            // (the entry point answers 401 first); the guard keeps the
            // controller honest under direct invocation in tests.
            return ResponseEntity.status(401).build();
        }
        var p = principal.get();
        return ResponseEntity.ok(new SessionResponse(
                true,
                p.acr(),
                p.authenticationTime(),
                p.idleExpiresAt(),
                p.absoluteExpiresAt()));
    }
}
