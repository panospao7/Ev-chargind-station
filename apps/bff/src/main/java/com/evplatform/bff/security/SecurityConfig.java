package com.evplatform.bff.security;

import com.evplatform.bff.session.BffSession;
import com.evplatform.bff.session.BffSessionProperties;
import com.evplatform.bff.session.SessionLifecycleService;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * BFF security configuration (I1-IAM-001 phase 2). Implements SEC-001 §5
 * (SEC-P01 cookie-session proof) and SEC-P02 (CSRF synchronizer):
 *
 * <ul>
 *   <li>opaque {@code __Host-evsession} cookie backed by bff_session_db;</li>
 *   <li>Authorization Code + PKCE S256 login against Keycloak, with the
 *       authorization request persisted as an encrypted pre-auth row in
 *       the session store (no JSESSIONID);</li>
 *   <li>session rotation + exact cookie contract on login success;</li>
 *   <li>session-bound synchronizer CSRF tokens;</li>
 *   <li>Origin/Referer allowlist for mutations on {@code /api/**};</li>
 *   <li>RFC 9457 problem+json errors with the security problem codes
 *       (SEC-001 §17: AUTHENTICATION_REQUIRED, SESSION_EXPIRED,
 *       CSRF_VALIDATION_FAILED, ORIGIN_NOT_ALLOWED, ACCESS_DENIED).</li>
 * </ul>
 */
/**
 * {@code @Configuration} is declared explicitly: in Spring Security 7.1.1
 * {@code @EnableWebSecurity} is no longer meta-annotated with
 * {@code @Configuration}, so without it the {@code idpJwkSource}
 * {@code JWKSource<SecurityContext>} bean is not registered and context
 * load fails (I1-IAM-001 phase-2 blocker).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Problem type URI base (RFC 9457 {@code type} member). Aligned with the
     * established platform convention used by PublicProxyController and the
     * Discovery public API ({@code api.evplatform.example}); a phase-2
     * deviation emitted the bare {@code evplatform.example} host.
     */
    static final String PROBLEM_BASE = "https://api.evplatform.example/problems/";

    /** Request attribute set by the OriginFilter when the origin is bad. */
    static final String ORIGIN_REJECTED_ATTR =
            SecurityConfig.class.getName() + ".ORIGIN_REJECTED";

    // ------------------------------------------------------------------
    // Problem Details writing (RFC 9457; SEC-001 §17 codes)
    // ------------------------------------------------------------------

    /** Writes an RFC 9457 problem+json body with the given code/status. */
    static void writeProblem(HttpServletResponse response, HttpStatus status,
                             String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        // detail is a fixed string per call site (never user input), and the
        // code is a registry constant — no injection surface.
        String json = "{\"type\":\"" + PROBLEM_BASE + code.toLowerCase()
                + "\",\"title\":\"" + code + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\"}";
        response.getWriter().write(json);
    }

    // ------------------------------------------------------------------
    // Origin / content-type filter (SEC-P02 §6.2)
    // ------------------------------------------------------------------

    /**
     * Mutation validation for browser-originated requests on {@code /api/**}
     * (SEC-P02 §6.2): exact allowed Origin (or valid Referer when Origin is
     * absent), and approved content type for mutations. Runs before CSRF.
     *
     * <p>Documented mapping decision (phase-2 handoff): a mutation with a
     * disallowed content type is reported as {@code CSRF_VALIDATION_FAILED}
     * (the CSRF layer is where mutation validation happens in Spring
     * Security; a separate 415 code is deferred to the contract slice).</p>
     *
     * <p>Plain class instantiated inside the filter chain (NOT a
     * {@code @Component}): the only registration path is
     * {@code addFilterBefore} in {@link #bffSecurityFilterChain}, so a
     * duplicate servlet registration is structurally impossible.</p>
     */
    public static class OriginFilter extends OncePerRequestFilter {

        private final List<String> allowedOrigins;

        public OriginFilter(BffSessionProperties properties) {
            this.allowedOrigins = properties.session() != null
                    && properties.session().allowedOrigins() != null
                    ? List.copyOf(properties.session().allowedOrigins())
                    : List.of();
        }

        private static boolean isSafeMethod(HttpServletRequest request) {
            String method = request.getMethod();
            return "GET".equals(method) || "HEAD".equals(method)
                    || "OPTIONS".equals(method);
        }

        private static boolean isApiPath(HttpServletRequest request) {
            return request.getRequestURI().startsWith("/api/");
        }

        private static boolean isMutation(HttpServletRequest request) {
            return !isSafeMethod(request);
        }

        private static boolean originAllowed(List<String> allowedOrigins, String origin) {
            return origin != null && allowedOrigins.contains(origin);
        }

        private static String originOf(String url) {
            if (url == null || url.isBlank()) {
                return null;
            }
            try {
                URI uri = URI.create(url);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                int port = uri.getPort();
                StringBuilder sb = new StringBuilder(scheme).append("://")
                        .append(uri.getHost() == null ? "" : uri.getHost());
                if (port != -1
                        && !((("http".equals(scheme)) && port == 80)
                             || (("https".equals(scheme)) && port == 443))) {
                    sb.append(':').append(port);
                }
                return sb.toString();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain)
                throws ServletException, IOException {
            if (!isApiPath(request) || !isMutation(request)) {
                chain.doFilter(request, response);
                return;
            }
            String origin = request.getHeader("Origin");
            String referer = request.getHeader("Referer");
            if (origin != null) {
                if (!originAllowed(allowedOrigins, origin)) {
                    reject(response, "ORIGIN_NOT_ALLOWED",
                            "Origin is not allowed for this deployment.");
                    return;
                }
            } else if (referer != null) {
                String refererOrigin = originOf(referer);
                if (!originAllowed(allowedOrigins, refererOrigin)) {
                    reject(response, "ORIGIN_NOT_ALLOWED",
                            "Referer origin is not allowed for this deployment.");
                    return;
                }
            }
        // Content-type check for mutations (documented mapping decision:
        // disallowed type → CSRF_VALIDATION_FAILED). Form-urlencoded is
        // allowed ONLY on the OIDC Back-Channel Logout notification path
        // (SEC-P08): that is a form POST from Keycloak on /api/internal/** —
        // it is NOT a browser mutation and is validated by its signed
        // logout_token instead (BackChannelLogoutController). Every other
        // browser mutation on /api/** must use an approved JSON content
        // type (SEC-P02 §6.4: "form-encoded mutation fails").
        if (isMutation(request) && !hasApprovedContentType(request)) {
            reject(response, "CSRF_VALIDATION_FAILED",
                    "Request content type is not approved for mutations.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean hasApprovedContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        String base = contentType.split(";")[0].trim().toLowerCase();
        if ("application/json".equals(base)
                || "application/problem+json".equals(base)
                || base.startsWith("application/problem+json")) {
            return true;
        }
        // The form-urlencoded allowance is scoped to the back-channel
        // logout receiver only (see doFilterInternal above).
        return "application/x-www-form-urlencoded".equals(base)
                && BackChannelLogoutController.BCL_PATH.equals(
                        request.getRequestURI());
    }

        private static void reject(HttpServletResponse response, String code,
                                   String detail) throws IOException {
            writeProblem(response, HttpStatus.FORBIDDEN, code, detail);
        }
    }

    // ------------------------------------------------------------------
    // Login success handler: create + rotate session, set cookie
    // ------------------------------------------------------------------

    /**
     * Login success (SEC-P01 §5.1 steps 5-7): persist the OAuth token
     * material encrypted, rotate the session reference, and emit the exact
     * cookie contract. The browser receives NO token material.
     */
    @Component
    public static class BffLoginSuccessHandler implements AuthenticationSuccessHandler {

        private final SessionLifecycleService lifecycle;
        private final BffSessionProperties properties;
        private final Clock clock;
        private final OAuth2AuthorizedClientService authorizedClientService;

        public BffLoginSuccessHandler(SessionLifecycleService lifecycle,
                                      BffSessionProperties properties,
                                      Clock clock,
                                      OAuth2AuthorizedClientService
                                              authorizedClientService) {
            this.lifecycle = lifecycle;
            this.properties = properties;
            this.clock = clock;
            this.authorizedClientService = authorizedClientService;
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request,
                                            HttpServletResponse response,
                                            Authentication authentication)
                throws IOException {
            if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            Object principalObj = oauth.getPrincipal();
            if (!(principalObj instanceof OidcUser oidcUser)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            String subject = oidcUser.getSubject();
            String sid = oidcUser.getClaimAsString("sid");
            String acr = oidcUser.getIdToken().getClaimAsString("acr");
            Instant authnTime = issuedAtOrNow(oidcUser);
            // Access token comes from the authorized client (token endpoint
            // response), not the user object; refresh token likewise. Both
            // go straight into the encrypted store — never into the browser.
            var authorizedClient = authorizedClientService.loadAuthorizedClient(
                    oauth.getAuthorizedClientRegistrationId(), oauth.getName());
            OAuth2AccessToken accessToken = authorizedClient != null
                    ? authorizedClient.getAccessToken() : null;
            OAuth2RefreshToken refreshToken =
                    authorizedClient == null ? null : authorizedClient.getRefreshToken();
            SessionLifecycleService.TokenMaterial tokens =
                    new SessionLifecycleService.TokenMaterial(
                            accessToken == null ? null : accessToken.getTokenValue(),
                            refreshToken == null ? null : refreshToken.getTokenValue(),
                            accessToken == null ? null : accessToken.getExpiresAt());
            Instant now = Instant.now(clock);
            BffSession session = lifecycle.createSession(subject, sid, tokens, acr, now);
            // Rotate immediately after authentication (SEC-P01 §5.1 step 6).
            String rotatedRef = lifecycle.rotate(session.sessionRef(), now);
            emitSessionCookie(response, rotatedRef, properties, now);
            response.sendRedirect("/");
        }

        private static Instant issuedAtOrNow(OidcUser user) {
            java.time.Instant iat = user.getIdToken().getIssuedAt();
            return iat != null ? iat : Instant.now();
        }
    }

    /**
     * Emits the exact session cookie (SEC-001 §5.2): {@code __Host-evsession},
     * Secure, HttpOnly, Path=/, no Domain, SameSite=Lax, Max-Age = absolute
     * timeout seconds. A static helper so tests can assert the emitted
     * header byte-for-byte.
     */
    static void emitSessionCookie(HttpServletResponse response, String sessionRef,
                                  BffSessionProperties properties, Instant now) {
        long maxAge = properties.session().absoluteTimeout().getSeconds();
        // ResponseCookie renders attributes in a fixed order and never emits
        // a Domain unless one is set — matching the SEC-001 §5.2 contract.
        org.springframework.http.ResponseCookie cookie =
                org.springframework.http.ResponseCookie.from(
                        properties.session().cookieName(), sessionRef)
                        .httpOnly(true)
                        .secure(true)
                        .path("/")
                        .maxAge(Duration.ofSeconds(maxAge))
                        .sameSite("Lax")
                        .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    // ------------------------------------------------------------------
    // Login failure handler: generic 401 (no reason detail)
    // ------------------------------------------------------------------

    /** Generic 401 on login failure — never reveals the failure reason. */
    @Component
    public static class BffLoginFailureHandler implements AuthenticationFailureHandler {

        @Override
        public void onAuthenticationFailure(HttpServletRequest request,
                                            HttpServletResponse response,
                                            org.springframework.security.core.AuthenticationException exception)
                throws IOException {
            writeProblem(response, HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "Authentication is required to access this resource.");
        }
    }

    // ------------------------------------------------------------------
    // Entry point / access-denied handler
    // ------------------------------------------------------------------

    /**
     * Entry point: 401 AUTHENTICATION_REQUIRED by default; when the security
     * context repository observed a present-but-invalid session cookie the
     * request attribute marks it and the code becomes SESSION_EXPIRED.
     */
    @Component
    public static class BffAuthenticationEntryPoint
            implements org.springframework.security.web.AuthenticationEntryPoint {

        @Override
        public void commence(HttpServletRequest request,
                             HttpServletResponse response,
                             org.springframework.security.core.AuthenticationException authException)
                throws IOException {
            String expired = (String) request.getAttribute(
                    BffSessionSecurityContextRepository.SESSION_STATE_ATTR);
            if (BffSessionSecurityContextRepository.SESSION_STATE_EXPIRED.equals(expired)) {
                writeProblem(response, HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                        "The session has expired or was revoked. Authenticate again.");
                return;
            }
            writeProblem(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Authentication is required to access this resource.");
        }
    }

    /**
     * Access denied: 403 ACCESS_DENIED (no reason detail). Spring Security
     * 7.1.1 routes CSRF token failures (missing/invalid synchronizer token)
     * through this handler — CsrfFilter raises a CsrfException (an
     * AccessDeniedException subtype) and CsrfConfigurer wires the chain's
     * access-denied handler to it — so those failures are mapped to the
     * SEC-001 §17 CSRF_VALIDATION_FAILED code (SEC-P02 §6.4, AC-04). The
     * failure detail is generic: the request's token value, session
     * reference and subject are never echoed (SEC-P02 §6.4 "does not reveal
     * sensitive state").
     */
    @Component
    public static class BffAccessDeniedHandler
            implements org.springframework.security.web.access.AccessDeniedHandler {

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           org.springframework.security.access.AccessDeniedException accessDeniedException)
                throws IOException {
            if (accessDeniedException instanceof
                    org.springframework.security.web.csrf.CsrfException) {
                writeProblem(response, HttpStatus.FORBIDDEN, "CSRF_VALIDATION_FAILED",
                        "The CSRF token is missing or invalid for this session.");
                return;
            }
            writeProblem(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Access to the requested resource is denied.");
        }
    }

    // ------------------------------------------------------------------
    // IdP JWKS source (back-channel logout token verification, SEC-P08)
    // ------------------------------------------------------------------

    /**
     * Remote JWKS for the Keycloak realm, cached 300s with a 60s refresh
     * window (task decision). Used by {@link BackChannelLogoutController}
     * to verify RS256 logout-token signatures. The issuer URL comes from
     * the configured provider property, matching the OIDC discovery value
     * resolved by the OAuth2 client auto-configuration.
     */
    @org.springframework.context.annotation.Bean
    public com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext>
    idpJwkSource(
            @org.springframework.beans.factory.annotation.Value(
                    "${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
            String issuer) throws java.net.MalformedURLException {
        var jwkSetUrl = new java.net.URL(
                issuer + "/protocol/openid-connect/certs");
        var cache = new com.nimbusds.jose.jwk.source.DefaultJWKSetCache(
                300, 60, java.util.concurrent.TimeUnit.SECONDS);
        return new com.nimbusds.jose.jwk.source.RemoteJWKSet<>(
                jwkSetUrl,
                new com.nimbusds.jose.util.DefaultResourceRetriever(2000, 2000),
                cache);
    }

    // ------------------------------------------------------------------
    // Token endpoint clients: private_key_jwt client authentication
    // ------------------------------------------------------------------

    /**
     * Authorization-code token request client with private_key_jwt client
     * authentication (SEC-001 §4.2/§5.1): the client assertion is signed
     * with the ev-bff RSA key ({@link ClientKeyConfig#BFF_CLIENT_JWK_BEAN}).
     * {@code setParametersConverter} composes with (does not replace) the
     * default grant-parameter converter — verified in
     * {@code AbstractRestClientOAuth2AccessTokenResponseClient} bytecode —
     * so grant_type/code/redirect_uri are preserved alongside
     * client_assertion. The converter itself returns {@code null} for
     * non-jwt registrations (verified bytecode), which the parent treats as
     * "no extra parameters".
     */
    @org.springframework.context.annotation.Bean
    public org.springframework.security.oauth2.client.endpoint
            .RestClientAuthorizationCodeTokenResponseClient
    authorizationCodeTokenResponseClient(RSAKey bffClientJwk) {
        var client = new org.springframework.security.oauth2.client.endpoint
                .RestClientAuthorizationCodeTokenResponseClient();
        client.setParametersConverter(
                new org.springframework.security.oauth2.client.endpoint
                        .NimbusJwtClientAuthenticationParametersConverter<>(
                        registration -> "ev-bff".equals(registration.getClientId())
                                ? bffClientJwk : null));
        return client;
    }

    // ------------------------------------------------------------------
    // Filter chain
    // ------------------------------------------------------------------

    @org.springframework.context.annotation.Bean
    public SecurityFilterChain bffSecurityFilterChain(
            HttpSecurity http,
            BffSessionSecurityContextRepository contextRepository,
            BffSessionCsrfTokenRepository csrfRepository,
            BffSessionProperties properties,
            StoreBackedAuthorizationRequestRepository storeBackedAuthorizationRequestRepository,
            BffLoginSuccessHandler successHandler,
            BffLoginFailureHandler failureHandler,
            BffAuthenticationEntryPoint entryPoint,
            BffAccessDeniedHandler accessDeniedHandler,
            ClientRegistrationRepository clientRegistrationRepository,
            org.springframework.security.oauth2.client.endpoint
                    .RestClientAuthorizationCodeTokenResponseClient
                    authorizationCodeTokenResponseClient) throws Exception {

        // PKCE S256 on the authorization-code request (SEC-P01 §5.1 step 2).
        // Spring Security 7 auto-enables PKCE for confidential clients when
        // the resolver is left default; this customizer makes it explicit
        // and version-independent. setAuthorizationRequestCustomizer is a
        // void setter — it cannot be chained on the constructor result.
        DefaultOAuth2AuthorizationRequestResolver pkceResolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization");
        pkceResolver.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());

        OriginFilter originFilter = new OriginFilter(properties);

        http
                .securityContext(sc -> sc
                        .securityContextRepository(contextRepository))
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(
                                org.springframework.security.config.http
                                        .SessionCreationPolicy.NEVER))
                .requestCache(rc -> rc.disable())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(
                                new org.springframework.security.web.csrf
                                        .CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults()
                                        .matcher("/login/oauth2/code/*"),
                                // OIDC Back-Channel Logout (SEC-P08): the
                                // notification is authenticated by its signed
                                // logout_token (RS256 against the IdP JWKS),
                                // not by the browser CSRF synchronizer.
                                PathPatternRequestMatcher.withDefaults()
                                        .matcher(BackChannelLogoutController.BCL_PATH)))
                .addFilterBefore(originFilter, CsrfFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(ae -> ae
                                .authorizationRequestResolver(pkceResolver)
                                // The authorization request is persisted as an
                                // ENCRYPTED pre-auth row in bff_session_db keyed
                                // by the state parameter (SEC-001 §5.1/§5.3,
                                // closeout M-3) — no JSESSIONID is ever created
                                // (SessionCreationPolicy.NEVER is preserved).
                                .authorizationRequestRepository(
                                        storeBackedAuthorizationRequestRepository))
                        // private_key_jwt client authentication on the token
                        // endpoint (SEC-001 §4.2/§5.1).
                        .tokenEndpoint(te -> te.accessTokenResponseClient(
                                authorizationCodeTokenResponseClient))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/api/internal/back-channel-logout").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/api/v1/stations", "/api/v1/stations/{stationRef}")
                        .permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .addHeaderWriter(new PrivateNoStoreHeaderWriter()));

        return http.build();
    }

    // ------------------------------------------------------------------
    // Cache-Control writer
    // ------------------------------------------------------------------

    /**
     * Emits {@code Cache-Control: private, no-store} on every response
     * (SEC-001 §5.4 item 13). Applied unconditionally: unauthenticated
     * responses to public endpoints are also non-cacheable in this slice;
     * the proof tests assert the authenticated case. Plain class
     * instantiated inside {@link #bffSecurityFilterChain} (no
     * {@code @Component}) so it cannot be double-registered.
     */
    public static class PrivateNoStoreHeaderWriter
            implements org.springframework.security.web.header.HeaderWriter {

        @Override
        public void writeHeaders(HttpServletRequest request,
                                 HttpServletResponse response) {
            response.setHeader("Cache-Control", "private, no-store");
        }
    }
}
