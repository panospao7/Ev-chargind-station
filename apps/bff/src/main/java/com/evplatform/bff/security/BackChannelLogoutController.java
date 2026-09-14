package com.evplatform.bff.security;

import com.evplatform.bff.session.SessionLifecycleService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OIDC Back-Channel Logout receiver (SEC-P08, I1-IAM-001 phase 2 part 2).
 * Keycloak POSTs a form-encoded {@code logout_token} (a signed JWS, not a
 * JWT bearer) to {@code /api/internal/back-channel-logout}; this controller
 * validates it and revokes the matching BFF session(s).
 *
 * <p>Validation order (OIDC Back-Channel Logout 1.0 §2.4 + task decision):
 * JWS signature against the IdP's remote JWKS (RS256 only), then issuer
 * match against the configured Keycloak issuer, then the {@code aud} claim
 * containing the {@code ev-bff} client id, then the {@code events}
 * claim containing the back-channel-logout event URI, then presence of
 * {@code sub} and/or {@code sid}, then rejection of any {@code nonce}
 * claim (logout tokens are ID-token-like and MUST NOT carry one). A
 * validated token revokes every ACTIVE session row for the subject+sid
 * pair; the endpoint answers 200 even when zero rows matched (idempotent
 * for Keycloak retries) and 400 (no revocation) for anything invalid.</p>
 *
 * <p>Privacy/logging (SEC-001 §5.3, ARC-SEC-21): the token value, subject
 * and sid are never logged — only the outcome and the revoked-row count.</p>
 *
 * <p>The 400 rejection body is an RFC 9457 problem+json with the RFC
 * default {@code about:blank} type: the platform problem-code registry
 * (contracts/registries/problem-codes-v1.yaml) has no code for an invalid
 * IdP-to-BFF internal callback, and inventing one is out of scope for this
 * slice.</p>
 */
@RestController
public class BackChannelLogoutController {

    /** Servlet path of the back-channel logout receiver (permitAll in SecurityConfig). */
    static final String BCL_PATH = "/api/internal/back-channel-logout";

    /** The single mandatory member of the {@code events} claim (OIDC BCL §2.4). */
    static final String LOGOUT_EVENT_URI = "http://schemas.openid.net/event/backchannel-logout";

    private static final Logger log =
            LoggerFactory.getLogger(BackChannelLogoutController.class);

    private final JWTProcessor<SecurityContext> logoutTokenProcessor;
    private final SessionLifecycleService lifecycle;
    private final String issuer;

    public BackChannelLogoutController(
            JWKSource<SecurityContext> idpJwkSource,
            SessionLifecycleService lifecycle,
            @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}") String issuer) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(Set.of(JWSAlgorithm.RS256), idpJwkSource));
        // The JOSE "typ" header is attacker-controlled and not a security
        // control here; Keycloak versions differ (logout+jwt / jwt / absent),
        // so the strict default type verifier is replaced deliberately. The
        // signature, issuer and events-claim checks are the actual gates.
        // Nimbus 10.9.1 rejects EVERY token when the verifier is null
        // ("Signed JWT rejected: No JWS header typ (type) verifier is
        // configured"), so an explicit permissive verifier is required.
        processor.setJWSTypeVerifier(new LogoutTokenTypeVerifier());
        this.logoutTokenProcessor = processor;
        this.lifecycle = lifecycle;
        this.issuer = issuer;
    }

    /**
     * Receives and processes the back-channel logout notification.
     *
     * @param logoutToken the signed logout token (form parameter); never logged
     * @return 200 when the token was valid (revocation attempted, row count
     *         logged), 400 with a problem+json body when anything is invalid
     */
    @PostMapping(value = BCL_PATH, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ProblemDetail> backChannelLogout(
            @RequestParam(value = "logout_token", required = false) String logoutToken) {
        if (logoutToken == null || logoutToken.isBlank()) {
            return reject();
        }
        try {
            SignedJWT signedJwt = SignedJWT.parse(logoutToken);
            JWTClaimsSet claims = logoutTokenProcessor.process(signedJwt, null);
            if (!issuer.equals(claims.getIssuer())) {
                return reject();
            }
            // Audience check (OIDC BCL §2.4): the logout token MUST carry the
            // BFF client id in its aud claim — a token minted for another
            // client must not be able to revoke this deployment's sessions.
            List<String> aud = claims.getAudience();
            if (aud == null || !aud.contains("ev-bff")) {
                return reject();
            }
            if (!hasLogoutEvent(claims)) {
                return reject();
            }
            String subject = claims.getSubject();
            String sid = claims.getStringClaim("sid");
            boolean hasSubject = subject != null && !subject.isBlank();
            boolean hasSid = sid != null && !sid.isBlank();
            if (!hasSubject && !hasSid) {
                return reject();
            }
            if (claims.getClaim("nonce") != null) {
                return reject();
            }
            // subject may be null when only sid is present; the store matches
            // on both columns, so a sid-only notification revokes nothing
            // (0 rows) and still answers 200 — see handoff residual risks.
            int revoked = lifecycle.revokeBySubjectAndSid(subject, sid);
            log.info("Back-channel logout accepted (revokedSessions={})", revoked);
            return ResponseEntity.ok().build();
        } catch (ParseException | BadJOSEException | JOSEException e) {
            log.info("Back-channel logout rejected (reason=invalid_logout_token)");
            return reject();
        }
    }

    /**
     * Permissive typ verifier replacing the Nimbus default: accepts
     * {@code typ} values {@code logout+jwt}, {@code jwt}, and an absent
     * header type (Keycloak versions differ; the typ header is
     * attacker-controlled and not a security control here — the signature,
     * issuer and events-claim checks are the actual gates).
     */
    static final class LogoutTokenTypeVerifier
            implements com.nimbusds.jose.proc.JOSEObjectTypeVerifier<SecurityContext> {

        @Override
        public void verify(com.nimbusds.jose.JOSEObjectType type, SecurityContext context)
                throws com.nimbusds.jose.proc.BadJOSEException {
            if (type == null || type.getType() == null) {
                return; // absent typ is accepted
            }
            String value = type.getType();
            if ("logout+jwt".equalsIgnoreCase(value) || "jwt".equalsIgnoreCase(value)) {
                return;
            }
            throw new com.nimbusds.jose.proc.BadJOSEException(
                    "Rejected JOSE object type: " + value);
        }
    }

    private static boolean hasLogoutEvent(JWTClaimsSet claims) {
        Object events = claims.getClaim("events");
        return events instanceof Map<?, ?> eventsMap
                && eventsMap.containsKey(LOGOUT_EVENT_URI);
    }

    /** RFC 9457 body with the RFC-default type (no registry code for this path). */
    private static ResponseEntity<ProblemDetail> reject() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The logout_token parameter is missing or invalid.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
