package com.evplatform.bff.session.web;

import com.evplatform.bff.security.BffSessionCsrfTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF token fetch endpoint for the browser (SEC-P02 §6.3): returns the
 * session-bound synchronizer token so the SPA can send it in the
 * {@code X-CSRF-TOKEN} header on mutations.
 *
 * <p>Implementation decision (task C3): the spec snippet read the DEFERRED
 * request attribute set by {@code CsrfTokenRequestAttributeHandler}. That
 * attribute is a lazy {@code SupplierCsrfToken} whose delegate resolves
 * through {@code CsrfTokenRepository.loadToken} and THROWS
 * {@code IllegalStateException("csrfTokenSupplier returned null delegate")}
 * when no token is stored yet (fresh session, first fetch). The controller
 * therefore talks to the repository directly: load the stored token; when
 * absent, generate one and persist it via {@code saveToken} so the very
 * first fetch mints and binds the session token. Same shape as the
 * deferred path, without the null-delegate trap.</p>
 */
@RestController
public class CsrfController {

    private final BffSessionCsrfTokenRepository csrfTokenRepository;

    public CsrfController(BffSessionCsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/api/v1/session/csrf")
    public ResponseEntity<CsrfResponse> csrf(HttpServletRequest request,
                                             HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        }
        return ResponseEntity.ok(new CsrfResponse(
                token.getToken(), BffSessionCsrfTokenRepository.CSRF_HEADER));
    }

    /** Response body: the token value and the header that carries it. */
    public record CsrfResponse(String token, String headerName) {
    }
}
