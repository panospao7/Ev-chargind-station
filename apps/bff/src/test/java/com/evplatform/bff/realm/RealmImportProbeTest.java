package com.evplatform.bff.realm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I1-IAM-002 AC-06 probe: a fresh Keycloak 26.6
 * (feature: token-exchange-standard) is bootstrapped by
 * {@link KeycloakExchangeFixture} (bare import + Admin-REST finalization,
 * per deviation I1-IAM-002-BOOTSTRAP-01 option A); this test asserts the
 * finalized configuration via the Admin API and proves the token-exchange
 * grant is processed semantically by the server.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealmImportProbeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    static final KeycloakExchangeFixture FX = KeycloakExchangeFixture.start();

    private String base() {
        return FX.base();
    }

    private String adminToken() throws Exception {
        return FX.adminToken();
    }

    private HttpResponse<String> adminGet(String token, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @Order(1)
    void realmImportsAndSvcClientConfigSurvives() throws Exception {
        String token = adminToken();

        JsonNode clients = MAPPER.readTree(
                adminGet(token, "/admin/realms/" + KeycloakExchangeFixture.REALM
                        + "/clients?max=200").body());
        boolean bff = false;
        int svcClients = 0;
        int svcWithAudMapper = 0;
        int svcWithStx = 0;
        for (JsonNode c : clients) {
            String id = c.path("id").asText();
            String clientId = c.path("clientId").asText();
            if (clientId.equals("ev-bff")) {
                bff = true;
            }
            if (clientId.startsWith("svc-")) {
                svcClients++;
                JsonNode mappers = MAPPER.readTree(adminGet(token,
                        "/admin/realms/" + KeycloakExchangeFixture.REALM
                                + "/clients/" + id + "/protocol-mappers/models").body());
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
                        "/admin/realms/" + KeycloakExchangeFixture.REALM
                                + "/clients/" + id).body());
                if ("true".equals(full.path("attributes")
                        .path("standard.token.exchange.enabled").asText(null))) {
                    svcWithStx++;
                }
            }
        }
        assertTrue(bff, "ev-bff client must exist");
        assertEquals(7, svcClients, "seven svc-* clients must exist");
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
                "/realms/" + KeycloakExchangeFixture.REALM + "/protocol/openid-connect/token",
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
}
