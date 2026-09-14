package com.evplatform.bff.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Testcontainers harness for the RFC 8693 token-exchange integration tests
 * (I1-IAM-002 phase 3): a REAL Keycloak 26.6 (same digest-pinned image as
 * {@code infra/local/compose.yaml}) started with {@code start-dev
 * --import-realm} and the ev-local realm imported with generated test JWKS.
 *
 * <p>Key handling (AGENTS.md §4 — key material is never committed): two RSA
 * key pairs are generated at harness start. The ev-bff pair is built EXACTLY
 * like {@code ClientKeyConfig} (kid = RFC 7638 SHA-256 thumbprint via
 * {@code keyIDFromThumbprint("SHA-256")}) so the BFF's client_assertion kid
 * matches the JWKS imported into the realm; its private key is written as a
 * PKCS#8 PEM temp file and surfaced via
 * {@code BFF_OAUTH_CLIENT_PRIVATE_KEY_PATH}. One shared svc-* key pair is
 * reused for all seven svc-* clients — test-only reuse, documented: the svc
 * clients never sign anything in these tests (they only receive exchanged
 * tokens), so a single test key keeps the harness small.</p>
 *
 * <p>The committed realm template contains {@code <PLACEHOLDER_JWKS_*>}
 * markers; the harness replaces them with the JSON-escaped JWKS strings and
 * writes the result to a temp file copied into the container's import
 * directory. The committed file itself is never modified.</p>
 *
 * <p>Startup wait: after the container's port is listening, the realm JWKS
 * endpoint ({@code /realms/ev-local/protocol/openid-connect/certs}) is
 * polled until HTTP 200 (up to ~120s) — the realm import completes
 * asynchronously during Keycloak startup.</p>
 *
 * <p>Helpers: ROPC token issuance for the driver test user
 * (security-test-client → subject token carrying the ev-bff audience per the
 * realm's aud-ev-bff mapper), raw token-endpoint form posts (rogue-client
 * and malformed-subject negatives), and Admin REST readers for the DA-1/DA-2
 * round-trip (client attributes + client profiles/policies). Dev-convention
 * credentials only; no token material is logged.</p>
 */
final class KeycloakTestHarness implements AutoCloseable {

    static final String ADMIN_USERNAME = "evplatform_admin";
    static final String ADMIN_PASSWORD = "evplatform_dev_only";
    static final String REALM = "ev-local";
    static final String DRIVER_USERNAME = "driver-local";
    static final String DRIVER_PASSWORD = "evplatform_dev_only";
    static final String SECURITY_TEST_CLIENT_ID = "security-test-client";
    static final String SECURITY_TEST_CLIENT_SECRET = "evplatform_dev_only";
    static final String ROGUE_CLIENT_ID = "rogue-exchange-client";
    static final String ROGUE_CLIENT_SECRET = "evplatform_dev_only_rogue";

    /** Same digest-pinned image as infra/local/compose.yaml (ENG-001 §3). */
    private static final String IMAGE =
            "keycloak/keycloak@sha256:0aae0de7fca85525f727d3354df17896092de8bb26ae4c12d89c77e5df8cbce4";

    private static final Logger log = LoggerFactory.getLogger(KeycloakTestHarness.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GenericContainer<?> container;
    private final RSAKey bffClientJwk;
    private final RSAKey svcClientJwk;
    private final Path bffPrivateKeyPemPath;
    private final String issuer;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private KeycloakTestHarness(GenericContainer<?> container,
                                RSAKey bffClientJwk,
                                RSAKey svcClientJwk,
                                Path bffPrivateKeyPemPath,
                                String issuer) {
        this.container = container;
        this.bffClientJwk = bffClientJwk;
        this.svcClientJwk = svcClientJwk;
        this.bffPrivateKeyPemPath = bffPrivateKeyPemPath;
        this.issuer = issuer;
    }

    /** Starts the container, imports the realm with generated JWKS, waits for the realm. */
    static KeycloakTestHarness start() {
        try {
            RSAKey bffJwk = generateRsaJwk();
            RSAKey svcJwk = generateRsaJwk();
            Path pem = writePkcs8Pem(bffJwk);
            Path realmFile = prepareRealmFile(bffJwk, svcJwk);

            GenericContainer<?> container = new GenericContainer<>(
                    DockerImageName.parse(IMAGE))
                    .withExposedPorts(8080)
                    // Exact env names from infra/local/compose.yaml (Keycloak 26.6
                    // KC_BOOTSTRAP_ADMIN_* bootstrap user).
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN_USERNAME)
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(realmFile),
                            "/opt/keycloak/data/import/ev-local-realm.json")
                    .withCommand("start-dev", "--import-realm", "--hostname-strict=false")
                    .withStartupTimeout(Duration.ofSeconds(180));
            container.start();

            String issuer = "http://localhost:" + container.getMappedPort(8080)
                    + "/realms/" + REALM;
            KeycloakTestHarness harness = new KeycloakTestHarness(
                    container, bffJwk, svcJwk, pem, issuer);
            harness.awaitRealm();
            log.info("Keycloak test harness ready (issuer port={})",
                    container.getMappedPort(8080));
            return harness;
        } catch (IOException e) {
            throw new UncheckedIOException("Keycloak test harness setup failed", e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Harness RSA key generation failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keycloak test harness startup interrupted", e);
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Harness RSA key generation failed", e);
        }
    }

    String issuer() {
        return issuer;
    }

    /** Path of the PKCS#8 PEM for the ev-bff private_key_jwt signing key. */
    String bffPrivateKeyPemPath() {
        return bffPrivateKeyPemPath.toString();
    }

    /** The shared svc-* signing key (test-only; used to craft negative-case JWTs). */
    RSAKey svcSigningKey() {
        return svcClientJwk;
    }

    // ------------------------------------------------------------------
    // ROPC (driver subject token)
    // ------------------------------------------------------------------

    /** Decrypted-in-memory ROPC result; token values are never logged. */
    record RopcTokens(String accessToken, String idToken, String refreshToken,
                      long expiresIn, String subject, String acr, Long authTime,
                      List<String> aud) {
    }

    /**
     * Password grant for the driver test user against security-test-client
     * (directAccessGrantsEnabled=true). The access token carries the ev-bff
     * audience (realm aud-ev-bff mapper) — the V2 rule that the subject
     * token must carry the requester as audience.
     */
    RopcTokens ropcDriverTokens() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("client_id", SECURITY_TEST_CLIENT_ID);
        form.put("client_secret", SECURITY_TEST_CLIENT_SECRET);
        form.put("username", DRIVER_USERNAME);
        form.put("password", DRIVER_PASSWORD);
        form.put("scope", "openid");
        RawResponse response = rawTokenRequest(form);
        if (response.status() != 200) {
            // The error body carries no token material (OAuth error JSON only).
            throw new IllegalStateException("ROPC token request failed (HTTP "
                    + response.status() + "): " + oauthErrorOf(response.body()));
        }
        try {
            JsonNode node = MAPPER.readTree(response.body());
            String accessToken = node.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("ROPC response lacked access_token");
            }
            com.nimbusds.jwt.JWTClaimsSet claims =
                    com.nimbusds.jwt.SignedJWT.parse(accessToken).getJWTClaimsSet();
            String refreshToken = node.hasNonNull("refresh_token")
                    ? node.get("refresh_token").asText() : null;
            String idToken = node.hasNonNull("id_token")
                    ? node.get("id_token").asText() : null;
            long expiresIn = node.path("expires_in").asLong(300L);
            Long authTime = claims.getClaim("auth_time") instanceof Number n
                    ? n.longValue() : null;
            String acr = claims.getClaim("acr") instanceof String s ? s : null;
            return new RopcTokens(accessToken, idToken, refreshToken, expiresIn,
                    claims.getSubject(), acr, authTime,
                    claims.getAudience() == null ? List.of() : claims.getAudience());
        } catch (IOException e) {
            throw new UncheckedIOException("ROPC response was not valid JSON", e);
        } catch (java.text.ParseException e) {
            throw new IllegalStateException("ROPC access token was not a parseable JWT", e);
        }
    }

    // ------------------------------------------------------------------
    // Raw token-endpoint access (negatives + response-shape assertions)
    // ------------------------------------------------------------------

    record RawResponse(int status, String body) {
    }

    /** Posts an arbitrary token-endpoint form request; returns status + raw body. */
    RawResponse rawTokenRequest(Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuer + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            return new RawResponse(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Token endpoint request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token endpoint request interrupted", e);
        }
    }

    private static String oauthErrorOf(String body) {
        try {
            return MAPPER.readTree(body).path("error").asText("") + " - "
                    + MAPPER.readTree(body).path("error_description").asText("");
        } catch (IOException e) {
            return "(unparseable error body)";
        }
    }

    // ------------------------------------------------------------------
    // Admin REST (DA-1/DA-2 round-trip)
    // ------------------------------------------------------------------

    /** Master-realm admin token via ROPC on admin-cli (bootstrap admin user). */
    private String adminAccessToken() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("client_id", "admin-cli");
        form.put("username", ADMIN_USERNAME);
        form.put("password", ADMIN_PASSWORD);
        RawResponse response = postForm(
                "http://localhost:" + container.getMappedPort(8080)
                        + "/realms/master/protocol/openid-connect/token", form);
        if (response.status() != 200) {
            throw new IllegalStateException(
                    "Admin token request failed (HTTP " + response.status() + ")");
        }
        try {
            return MAPPER.readTree(response.body()).path("access_token").asText();
        } catch (IOException e) {
            throw new UncheckedIOException("Admin token response was not valid JSON", e);
        }
    }

    /** GETs an Admin REST path under /admin/realms/ev-local/ (path may carry a query). */
    JsonNode adminGet(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + container.getMappedPort(8080)
                        + "/admin/realms/" + REALM + "/" + path))
                .header("Authorization", "Bearer " + adminAccessToken())
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Admin REST GET " + path
                        + " failed (HTTP " + response.statusCode() + ")");
            }
            return MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Admin REST response was not valid JSON", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Admin REST request interrupted", e);
        }
    }

    private RawResponse postForm(String uri, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            return new RawResponse(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Form POST failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Form POST interrupted", e);
        }
    }

    // ------------------------------------------------------------------
    // Setup helpers
    // ------------------------------------------------------------------

    /**
     * Generates a 2048-bit RSA JWK with kid = RFC 7638 SHA-256 thumbprint —
     * the SAME derivation as {@code ClientKeyConfig} so the BFF's
     * client_assertion kid matches the imported JWKS. The JWK carries
     * {@code use=sig} and {@code alg=RS256}: Keycloak's
     * {@code ClientPublicKeyLoader} filters client JWKS keys via
     * {@code JWKSUtils.getKeyWrappersForUse(SIG)} and silently drops keys
     * without a {@code use} field ("Available kids: '[]'").
     */
    private static RSAKey generateRsaJwk() throws java.security.NoSuchAlgorithmException,
            com.nimbusds.jose.JOSEException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint("SHA-256")
                .build();
    }

    private static Path writePkcs8Pem(RSAKey jwk) throws IOException,
            com.nimbusds.jose.JOSEException {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'})
                        .encodeToString(jwk.toRSAPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path file = Files.createTempFile("bff-exchange-client-key", ".pem");
        Files.writeString(file, pem, StandardCharsets.US_ASCII);
        return file;
    }

    /**
     * Reads the committed realm template, replaces the JWKS placeholders with
     * the generated (JSON-escaped) JWKS strings, writes the result to a temp
     * file. The committed file is never modified.
     */
    private static Path prepareRealmFile(RSAKey bffJwk, RSAKey svcJwk) throws IOException {
        Path template = Path.of("..", "..", "infra", "local", "keycloak", "realms",
                "ev-local-realm.json");
        String json = Files.readString(template, StandardCharsets.UTF_8);
        json = json.replace("<PLACEHOLDER_JWKS_EV_BFF>",
                jsonEscape(new JWKSet(bffJwk.toPublicJWK()).toString()));
        String svcJwks = jsonEscape(new JWKSet(svcJwk.toPublicJWK()).toString());
        for (String svc : List.of("ACCOUNT", "STATION_OPERATIONS", "BOOKING_SESSION",
                "DEVICE_INTEGRATION", "DISCOVERY_INSIGHTS", "NOTIFICATION",
                "GOVERNANCE_SUPPORT")) {
            json = json.replace("<PLACEHOLDER_JWKS_SVC_" + svc + ">", svcJwks);
        }
        if (json.contains("<PLACEHOLDER_JWKS")) {
            throw new IllegalStateException(
                    "Realm template still contains an unreplaced JWKS placeholder");
        }
        Path target = Files.createTempFile("ev-local-realm-test-", ".json");
        Files.writeString(target, json, StandardCharsets.UTF_8);
        return target;
    }

    /** Escapes a JWKS JSON string for embedding inside a JSON string value. */
    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Polls the realm JWKS endpoint until HTTP 200 (import complete), up to ~120s. */
    private void awaitRealm() throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(120).toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(issuer + "/protocol/openid-connect/certs"))
                        .GET().build();
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException e) {
                // not ready yet — retry until the deadline
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("Keycloak realm did not become ready within 120s. "
                + "Container log tail: " + containerLogTail());
    }

    private String containerLogTail() {
        String logs = container.getLogs();
        return logs.length() > 4000
                ? logs.substring(logs.length() - 4000) : logs;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        container.stop();
    }
}
