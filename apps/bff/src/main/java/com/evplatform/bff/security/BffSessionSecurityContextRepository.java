package com.evplatform.bff.security;

import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.SessionLifecycleService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Cookie-backed security-context repository for the opaque BFF session
 * (SEC-001 §5.2/§5.3, SEC-P01). The browser holds only the opaque
 * {@code __Host-evsession} cookie; the authoritative state lives in
 * {@code bff_session_db} and is loaded through
 * {@link SessionLifecycleService#loadValidSession(String, Instant)}.
 *
 * <p>Cookie-present-vs-absent distinction for the entry point (phase-2
 * decision): when a session cookie is present but the referenced row is
 * missing, expired or revoked, a request attribute is set so the
 * authentication entry point emits {@code SESSION_EXPIRED} instead of
 * {@code AUTHENTICATION_REQUIRED}. The attribute name is
 * {@link #SESSION_STATE_ATTR}; the value is {@code expired}.</p>
 *
 * <p>Load also touches activity (idle window re-derived from now) — the
 * load itself is an authenticated interaction for the session lifetime
 * model (SEC-001 §5.2: idle timeout 30m).</p>
 */
@Component
public class BffSessionSecurityContextRepository implements SecurityContextRepository {

    /** Request attribute signalling an expired/revoked/unknown session cookie. */
    public static final String SESSION_STATE_ATTR =
            BffSessionSecurityContextRepository.class.getName() + ".SESSION_STATE";

    /** Value for {@link #SESSION_STATE_ATTR}: cookie present but invalid. */
    public static final String SESSION_STATE_EXPIRED = "expired";

    /** The session cookie name (SEC-001 §2.2 / application.yml bff.session.cookie-name). */
    public static final String COOKIE_NAME = "__Host-evsession";

    private final SessionLifecycleService lifecycle;
    private final Clock clock;

    public BffSessionSecurityContextRepository(SessionLifecycleService lifecycle, Clock clock) {
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /** Reads the opaque session reference from the request cookies, if any. */
    public static Optional<String> sessionRef(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public SecurityContext loadContext(HttpRequestResponseHolder holder) {
        return loadContext(holder.getRequest());
    }

    /**
     * Modern overload used by {@link #loadContext(HttpRequestResponseHolder)}
     * internally. Not an interface override in Security 7.1.1 — the interface
     * still declares only the deprecated holder-based method as abstract.
     */
    public SecurityContext loadContext(HttpServletRequest request) {
        Optional<String> ref = sessionRef(request);
        if (ref.isEmpty()) {
            return SecurityContextHolder.createEmptyContext();
        }
        SessionLifecycleService.Loaded loaded =
                lifecycle.loadValidSession(ref.get(), Instant.now(clock));
        if (loaded instanceof SessionLifecycleService.Loaded.Valid valid) {
            BffSession session = valid.session();
            lifecycle.touch(session.sessionRef(), Instant.now(clock));
            request.setAttribute(SESSION_STATE_ATTR, "valid");
            return new SecurityContextImpl(new BffSessionAuthentication(session));
        }
        // Cookie present but invalid (expired/revoked/unknown) — remember
        // this for the entry point so it can emit SESSION_EXPIRED.
        request.setAttribute(SESSION_STATE_ATTR, SESSION_STATE_EXPIRED);
        return SecurityContextHolder.createEmptyContext();
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request,
                            HttpServletResponse response) {
        // Persistence is owned by SessionLifecycleService (createSession /
        // rotate). The login success handler persists the session and sets
        // the cookie itself; there is nothing to write here beyond the
        // contract requirement.
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return sessionRef(request).isPresent()
                && lifecycle.loadValidSession(
                        sessionRef(request).get(), Instant.now(clock))
                instanceof SessionLifecycleService.Loaded.Valid;
    }

    /**
     * Minimal authentication carrying the Keycloak subject (principal) and
     * the BFF session row. No OAuth token material is exposed here — tokens
     * stay in the encrypted store (SEC-001 §5.3); downstream exchange
     * (SEC-P03, later slice) decrypts them server-side only.
     */
    public static final class BffSessionAuthentication
            extends AbstractAuthenticationToken {

        private final BffSessionPrincipal principal;

        public BffSessionAuthentication(BffSession session) {
            super(AuthorityUtils.createAuthorityList("ROLE_AUTHENTICATED"));
            this.principal = new BffSessionPrincipal(
                    session.keycloakSubject(), session.keycloakSid(),
                    session.acr(), session.authnTime(), session.sessionRef(),
                    session.idleExpiresAt(), session.absoluteExpiresAt());
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null; // no credential material in memory beyond the store
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        public BffSessionPrincipal sessionPrincipal() {
            return principal;
        }
    }

    /** Session principal: identity + assurance + lifetime context, no token material. */
    public record BffSessionPrincipal(String subject, String sid, String acr,
                                      Instant authenticationTime,
                                      String sessionRef,
                                      Instant idleExpiresAt,
                                      Instant absoluteExpiresAt)
            implements java.security.Principal, java.io.Serializable {

        @Override
        public String getName() {
            return subject;
        }
    }

    /** Convenience accessor for controllers: the current session principal. */
    public static Optional<BffSessionPrincipal> currentPrincipal(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof BffSessionPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    /** Granted authorities of the session authentication (single role marker). */
    public static Collection<GrantedAuthority> sessionAuthorities() {
        return Collections.unmodifiableList(
                AuthorityUtils.createAuthorityList("ROLE_AUTHENTICATED"));
    }
}
