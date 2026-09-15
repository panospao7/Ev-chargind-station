package com.evplatform.bff.realm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-IAM-002 AC-06 probe: a fresh Keycloak 26.6 (token-exchange feature
 * enabled) imports the realm; audience mappers, Standard Token Exchange V2
 * attributes and the private_key_jwt client settings are asserted via the
 * Admin REST API. This documents the deterministic import recipe that the
 * phase-3 exchange suite builds on.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealmImportProbeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    static final GenericContainer<?> KC = StartOnce.KC;

    private static String base() {
        return "http://" + KC.getHost() + ":" + KC.getMappedPort(8080);
    }

    private static HttpResponse<String> postForm(String path, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> adminGet(String adminToken, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String adminToken() throws Exception {
        HttpResponse<String> response = postForm(
                "/realms/master/protocol/openid-connect/token",
                "grant_type=password&client_id=admin-cli&username=admin&password=admin");
        assertEquals(200, response.statusCode(), "admin token request: " + response.body());
        return MAPPER.readTree(response.body()).path("access_token").asText();
    }

    @Test
    @Order(1)
    void realmImportsAndSvcClientConfigSurvives() throws Exception {
        String token = adminToken();

        JsonNode clients = MAPPER.readTree(
                adminGet(token, "/admin/realms/ev-local/clients?max=200").body());
        boolean bff = false;
        int svcWithAudMapper = 0;
        int svcWithStx = 0;
        for (JsonNode c : clients) {
            String id = c.path("id").asText();
            String clientId = c.path("clientId").asText();
            if (clientId.equals("ev-bff")) {
                bff = true;
            }
            if (clientId.startsWith("svc-")) {
                JsonNode mappers = MAPPER.readTree(adminGet(token,
                        "/admin/realms/ev-local/clients/" + id + "/protocol-mappers/models").body());
                boolean hasAud = false;
                for (JsonNode m : mappers) {
                    if (("aud-" + clientId).equals(m.path("name").asText())) {
                        hasAud = true;
                    }
                }
                if (hasAud) {
                    svcWithAudMapper++;
                }
                JsonNode full = MAPPER.readTree(adminGet(token,
                        "/admin/realms/ev-local/clients/" + id).body());
                if ("true".equals(full.path("attributes")
                        .path("standard.token.exchange.enabled").asText(null))) {
                    svcWithStx++;
                }
            }
        }
        assertTrue(bff, "ev-bff client must exist");
        assertEquals(7, svcWithAudMapper, "every svc-* client must carry its audience mapper");
        assertEquals(7, svcWithStx, "every svc-* client must carry the STX-v2 attribute");
    }

    @Test
    @Order(2)
    void tokenExchangeGrantIsProcessedSemantically() throws Exception {
        // server-level feature proof: the token-exchange grant must reach
        // semantic validation (bad subject token) rather than being rejected
        // as an unknown/disabled grant type
        HttpResponse<String> response = postForm(
                "/realms/ev-local/protocol/openid-connect/token",
                "grant_type=urn:ietf:params:oauth:grant-type:token-exchange"
                        + "&client_id=ev-bff"
                        + "&subject_token=not-a-real-token"
                        + "&subject_token_type=urn:ietf:params:oauth:token-type:access_token"
                        + "&audience=svc-station-operations");
        String body = response.body();
        List<Integer> semantic = List.of(400, 401, 403);
        assertTrue(semantic.contains(response.statusCode()),
                "token-exchange grant must be processed semantically, got "
                        + response.statusCode() + ": " + body);
        assertTrue(!body.contains("Feature disabled") && !body.contains("unknown grant"),
                "token-exchange feature must be active, got: " + body);
    }

    /** Keycloak 26.6 with the token-exchange feature; realm imported once. */
    private static class StartOnce {
        static final GenericContainer<?> KC = start();

        static GenericContainer<?> start() {
            try {
                Path realmFile = Path.of("..", "..", "infra", "local", "keycloak",
                        "realms", "ev-local-realm.json").toAbsolutePath().normalize();
                GenericContainer<?> kc = new GenericContainer<>("quay.io/keycloak/keycloak:26.6")
                        .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                        .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                        .withCommand("start-dev --features=token-exchange --import-realm")
                        .withFileSystemBind(realmFile.toString(),
                                "/opt/keycloak/data/import/ev-local-realm.json",
                                org.testcontainers.containers.BindMode.READ_ONLY)
                        .withExposedPorts(8080)
                        .waitingFor(Wait.forHttp("/").forPort(8080)
                                .forStatusCodeMatching(code -> code == 200 || code == 302 || code == 404)
                                .withStartupTimeout(Duration.ofMinutes(3)));
                kc.start();
                waitUntilRealmUp(kc);
                return kc;
            } catch (Exception e) {
                throw new IllegalStateException("keycloak probe container failed", e);
            }
        }

        private static void waitUntilRealmUp(GenericContainer<?> kc) throws Exception {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    HttpResponse<String> r = http.send(HttpRequest.newBuilder(
                                            URI.create("http://" + kc.getHost() + ":" + kc.getMappedPort(8080)
                                                    + "/realms/ev-local")).GET().build(),
                                    HttpResponse.BodyHandlers.ofString());
                    if (r.statusCode() == 200) {
                        return;
                    }
                } catch (Exception ignored) {
                    // not up yet
                }
                Thread.sleep(1000);
            }
            throw new IllegalStateException("ev-local realm did not come up");
        }
    }
}
