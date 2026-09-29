package com.evplatform.bff.realm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * I1-IAM-002 phase-3 fixture: a clean Keycloak 26.6
 * (feature: token-exchange-standard) with the deterministic Option-A
 * bootstrap proven in deviation I1-IAM-002-BOOTSTRAP-01:
 *
 *   bare-realm Admin-REST import (users and realm settings, no clients)
 *   → bare client POSTs
 *   → per-client attribute finalization via PUT
 *     (generated real JWKS for ev-bff; STX-v2 attribute on svc-* targets)
 *   → audience mappers via protocol-mappers/add-models
 *
 * Keycloak 26.6 quirk (documented in the deviation): attributes and mappers
 * created inside the realm-import payload are dropped silently — hence the
 * create-bare/update-finalize split. Ev-bff's signing key pair is generated
 * per fixture run (never committed) and its public JWK is PUT into the
 * client so client_assertion signatures verify.
 */
public final class KeycloakExchangeFixture {

    public static final String REALM = "ev-local";
    public static final String BFF_CLIENT = "ev-bff";
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public final GenericContainer<?> keycloak;
    private static volatile PrivateKey fixturePrivateKey;
    private static volatile String fixtureKid;

    private volatile String cachedAdminToken;

    private KeycloakExchangeFixture(GenericContainer<?> keycloak) {
        this.keycloak = keycloak;
    }

    public String base() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }

    public String issuer() {
        return base() + "/realms/" + REALM;
    }

    public String adminToken() throws Exception {
        if (cachedAdminToken != null && !cachedAdminToken.isBlank()) {
            return cachedAdminToken;
        }
        HttpResponse<String> r = postForm("/realms/master/protocol/openid-connect/token",
                "grant_type=password&client_id=admin-cli&username=admin&password=admin");
        if (r.statusCode() != 200) {
            throw new IllegalStateException("admin token failed: " + r.body());
        }
        cachedAdminToken = MAPPER.readTree(r.body()).path("access_token").asText();
        return cachedAdminToken;
    }

    public HttpResponse<String> postForm(String path, String form) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> admin(String method, String path, String jsonBody)
            throws Exception {
        HttpResponse<String> r = adminOnce(method, path, jsonBody);
        if (r.statusCode() == 401) {
            // cached admin token expired — refresh once and retry
            cachedAdminToken = null;
            r = adminOnce(method, path, jsonBody);
        }
        return r;
    }

    private HttpResponse<String> adminOnce(String method, String path, String jsonBody)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Authorization", "Bearer " + adminToken())
                .header("Content-Type", "application/json");
        if ("POST".equals(method)) {
            b.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        } else if ("PUT".equals(method)) {
            b.PUT(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        } else {
            b.GET();
        }
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** ROPC on the security-test-client for a seeded test user. */
    public String ropcUserToken(String username, String password) throws Exception {
        HttpResponse<String> r = postForm("/realms/" + REALM + "/protocol/openid-connect/token",
                "grant_type=password&client_id=security-test-client"
                        + "&client_secret=security-test-client-only-dev"
                        + "&username=" + username + "&password=" + password
                        + "&scope=openid");
        JsonNode body = MAPPER.readTree(r.body());
        if (!body.hasNonNull("access_token")) {
            throw new IllegalStateException("ROPC failed: " + r.body());
        }
        return body.get("access_token").asText();
    }

    /** Signs a private_key_jwt client assertion for ev-bff (§8.2: ≤60 s, unique jti). */
    public String evBffClientAssertion() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(BFF_CLIENT)
                .subject(BFF_CLIENT)
                .audience(issuer())
                .jwtID(UUID.randomUUID().toString())
                .expirationTime(new Date((now + 60) * 1000))
                .issueTime(new Date(now * 1000))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(fixtureKid).build(),
                claims);
        JWSSigner signer = new RSASSASigner(fixturePrivateKey);
        jwt.sign(signer);
        return jwt.serialize();
    }

    /** Boots the container and runs the deterministic bootstrap. */
    public static KeycloakExchangeFixture start() {
        try {
            Path realmFile = Path.of("..", "..", "infra", "local", "keycloak",
                    "realms", "ev-local-realm.json").toAbsolutePath().normalize();
            GenericContainer<?> kc = new GenericContainer<>("quay.io/keycloak/keycloak:26.6")
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                    .withCommand("start-dev --features=token-exchange-standard")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forLogMessage(".*Keycloak.*started.*", 1)
                            .withStartupTimeout(Duration.ofMinutes(6)))
                    .withLogConsumer(frame -> System.err.print(frame.getUtf8String()));
            kc.start();
            KeycloakExchangeFixture fx = new KeycloakExchangeFixture(kc);
            waitUntilServerUp(fx);
            fx.bootstrap();
            return fx;
        } catch (Exception e) {
            // container output is streamed to stderr by the log consumer
            throw new IllegalStateException("keycloak fixture failed", e);
        }
    }

    /** Waits for the ADMIN server (the realm is created by bootstrap after this). */
    private static void waitUntilServerUp(KeycloakExchangeFixture fx) throws Exception {
        long deadline = System.currentTimeMillis() + 240_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(
                                URI.create(fx.base() + "/admin/realms")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                // 401 means the admin API is answering
                if (r.statusCode() == 401 || r.statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // starting
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("keycloak admin API did not come up");
    }

    /**
     * Deterministic finalization: the dir import provides the identity
     * baseline (users, realm settings) but drops client attributes and
     * mappers, so every client is finalized over the Admin API:
     * attributes via PUT (generated real JWKS for ev-bff; STX-v2 attribute
     * on svc-* targets), audience mappers via add-models.
     */
    private void bootstrap() throws Exception {
        String at = adminToken();
        JsonNode realm = MAPPER.readTree(java.nio.file.Files.readString(
                Path.of("..", "..", "infra", "local", "keycloak", "realms",
                        "ev-local-realm.json").toAbsolutePath().normalize()));

        // 1. bare realm (users and settings; clients are created below —
        //    dir-import/imported clients reject attribute finalization)
        ObjectNode bareRealm = MAPPER.createObjectNode();
        bareRealm.put("realm", realm.path("realm").asText());
        bareRealm.put("enabled", true);
        bareRealm.put("sslRequired", "none");
        bareRealm.put("registrationAllowed", false);
        bareRealm.put("resetPasswordAllowed", false);
        bareRealm.put("verifyEmail", realm.path("verifyEmail").asBoolean(false));
        bareRealm.set("users", realm.path("users").deepCopy());
        HttpResponse<String> realmPost = admin("POST", "/admin/realms", bareRealm.toString());
        if (realmPost.statusCode() != 201) {
            throw new IllegalStateException("bare realm import failed: "
                    + realmPost.statusCode() + " " + realmPost.body());
        }

        // 2. create clients bare, then finalize attributes via PUT and mappers
        //    via add-models (creation drops attributes/mappers on KC 26.6)
        for (JsonNode client : realm.path("clients")) {
            String clientId = client.path("clientId").asText();
            boolean target = clientId.startsWith("svc-");
            ObjectNode bareClient = MAPPER.createObjectNode();
            bareClient.put("clientId", clientId);
            bareClient.put("enabled", true);
            bareClient.put("protocol", "openid-connect");
            bareClient.put("publicClient", client.path("publicClient").asBoolean(false));
            bareClient.put("serviceAccountsEnabled", target);
            bareClient.put("clientAuthenticatorType",
                    client.path("clientAuthenticatorType").asText("client-jwt"));
            bareClient.put("standardFlowEnabled",
                    client.path("standardFlowEnabled").asBoolean(false));
            bareClient.put("directAccessGrantsEnabled",
                    client.path("directAccessGrantsEnabled").asBoolean(false));
            if (client.hasNonNull("secret")) {
                bareClient.put("secret", client.path("secret").asText());
            }
            HttpResponse<String> post = admin("POST", "/admin/realms/" + REALM
                    + "/clients", bareClient.toString());
            if (post.statusCode() != 201) {
                throw new IllegalStateException("client create failed for "
                        + clientId + ": " + post.statusCode() + " " + post.body());
            }
        }

        // 3. ev-bff signing key: generated per fixture run (never committed)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        java.security.interfaces.RSAPublicKey pub =
                (java.security.interfaces.RSAPublicKey) kp.getPublic();
        PrivateKey priv = kp.getPrivate();
        String kid = "probe-key-1";
        ObjectNode jwk = MAPPER.createObjectNode();
        jwk.put("kty", "RSA");
        jwk.put("alg", "RS256");
        jwk.put("use", "sig");
        jwk.put("kid", kid);
        jwk.put("n", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(stripLeadingZero(pub.getModulus().toByteArray())));
        jwk.put("e", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(stripLeadingZero(pub.getPublicExponent().toByteArray())));
        ObjectNode jwks = MAPPER.createObjectNode();
        jwks.putArray("keys").add(jwk);

        // 4. finalize: attributes via PUT, mappers via add-models
        // fetch the client list ONCE and index by clientId — avoids any
        // server-side query-parameter filtering anomalies on this build
        JsonNode allClients = MAPPER.readTree(admin("GET", "/admin/realms/" + REALM
                + "/clients?max=500", null).body());
        java.util.Map<String, String> uuidByClientId = new java.util.HashMap<>();
        for (JsonNode c : allClients) {
            uuidByClientId.put(c.path("clientId").asText(), c.path("id").asText());
        }
        for (JsonNode client : realm.path("clients")) {
            String clientId = client.path("clientId").asText();
            boolean target = clientId.startsWith("svc-");
            String clientUuid = uuidByClientId.get(clientId);
            ObjectNode attrs = client.path("attributes").deepCopy();
            attrs.put("standard.token.exchange.enabled", target ? "true" : "false");
            if (clientId.equals(BFF_CLIENT)) {
                attrs.put("jwks.string", jwks.toString());
            }
            // attributes-ONLY PUT: including clientId in an update payload
            // triggers KC 26.x rename-duplicate validation → 409
            ObjectNode putBody = MAPPER.createObjectNode();
            putBody.set("attributes", attrs);
            HttpResponse<String> put = admin("PUT", "/admin/realms/" + REALM
                    + "/clients/" + clientUuid, putBody.toString());
            if (put.statusCode() != 204) {
                throw new IllegalStateException("attribute finalize failed for "
                        + clientId + ": " + put.statusCode() + " " + put.body());
            }
            JsonNode mappers = client.path("protocolMappers");
            if (mappers.isArray() && !mappers.isEmpty()) {
                HttpResponse<String> mm = admin("POST", "/admin/realms/" + REALM
                        + "/clients/" + clientUuid + "/protocol-mappers/add-models",
                        mappers.toString());
                if (mm.statusCode() != 204 && mm.statusCode() != 201) {
                    throw new IllegalStateException("mapper finalize failed for "
                            + clientId + ": " + mm.statusCode() + " " + mm.body());
                }
            }
        }

        fixturePrivateKey = priv;
        fixtureKid = kid;
    }


    public PrivateKey privateKey() {
        return fixturePrivateKey;
    }

    public String kid() {
        return fixtureKid;
    }

    private static byte[] stripLeadingZero(byte[] b) {
        if (b.length > 1 && b[0] == 0) {
            byte[] out = new byte[b.length - 1];
            System.arraycopy(b, 1, out, 0, out.length);
            return out;
        }
        return b;
    }
}
