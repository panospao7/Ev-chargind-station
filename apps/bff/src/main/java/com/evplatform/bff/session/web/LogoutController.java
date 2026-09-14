package com.evplatform.bff.session.web;

import com.evplatform.bff.security.BffSessionSecurityContextRepository;
import com.evplatform.bff.session.BffSessionProperties;
import com.evplatform.bff.session.SessionLifecycleService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local logout (SEC-001 §6.3 / SEC-P01): revokes the current BFF session
 * row and clears the session cookie. The browser stays logged in to the
 * IdP (front-channel RP-initiated logout is a later slice); this endpoint
 * ends the BFF session itself.
 */
@RestController
public class LogoutController {

    private final SessionLifecycleService lifecycle;
    private final BffSessionProperties properties;
    private final Clock clock;

    public LogoutController(SessionLifecycleService lifecycle,
                            BffSessionProperties properties,
                            Clock clock) {
        this.lifecycle = lifecycle;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping("/api/v1/session/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        var principal = BffSessionSecurityContextRepository
                .currentPrincipal(authentication);
        if (principal.isPresent()) {
            boolean revoked = lifecycle.revoke(principal.get().sessionRef());
            if (!revoked) {
                // Already expired/revoked/unknown — the cookie is cleared
                // regardless; nothing sensitive is revealed either way.
            }
        }
        clearSessionCookie(response);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clears the session cookie with the exact inverse of the SEC-001 §5.2
     * contract: empty value, Max-Age=0, Secure, HttpOnly, Path=/, no
     * Domain, SameSite=Lax. Attribute order matches
     * {@code SecurityConfig#emitSessionCookie} (ResponseCookie renders in a
     * fixed order and never emits a Domain unless one is set).
     */
    private void clearSessionCookie(HttpServletResponse response) {
        String cookieName = properties.session() != null
                && properties.session().cookieName() != null
                ? properties.session().cookieName()
                : "__Host-evsession";
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
