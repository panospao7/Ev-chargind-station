package com.evplatform.bff.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Outbound OAuth 2.0 Token Exchange client (RFC 8693) against the Keycloak
 * token endpoint (I1-IAM-002, SEC-P03).
 *
 * <p>Request (form-urlencoded, EXACT field set — no {@code scope} parameter:
 * the audience parameter downscopes only and the exchanged token's scopes
 * are governed by the downscope-assertion-grant-enforcer client policy):</p>
 *
 * <ul>
 *   <li>{@code grant_type=urn:ietf:params:oauth:grant-type:token-exchange}</li>
 *   <li>{@code subject_token} — the user's session access token</li>
 *   <li>{@code subject_token_type=urn:ietf:params:oauth:token-type:access_token}</li>
 *   <li>{@code requested_token_type=urn:ietf:params:oauth:token-type:access_token}</li>
 *   <li>{@code audience} — the target service client id</li>
 *   <li>{@code client_id=ev-bff}</li>
 *   <li>{@code client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer}</li>
 *   <li>{@code client_assertion} — fresh private_key_jwt assertion</li>
 * </ul>
 *
 * <p>Failures (any HTTP status, transport error, or malformed body) map to a
 * typed {@link ExchangeResult.Failure} carrying the OAuth {@code error} /
 * {@code error_description} when the IdP provided them — never an exception
 * carrying token material. Token, assertion, and response-body values are
 * NEVER logged (SEC-001 §5.3, ARC-SEC-21): only the outcome and the target
 * audience appear in log lines.</p>
 */
@Component
public class TokenExchangeClient {

    private static final Logger log = LoggerFactory.getLogger(TokenExchangeClient.class);

    static final String GRANT_TYPE_TOKEN_EXCHANGE =
            "urn:ietf:params:oauth:grant-type:token-exchange";
    static final String TOKEN_TYPE_ACCESS_TOKEN =
            "urn:ietf:params:oauth:token-type:access_token";
    static final String CLIENT_ASSERTION_TYPE_JWT_BEARER =
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private final RestClient restClient;
    private final ClientAssertionFactory assertionFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenExchangeClient(ClientAssertionFactory assertionFactory,
                               @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
                               String issuer) {
        this.restClient = RestClient.builder()
                .baseUrl(issuer + "/protocol/openid-connect/token")
                .build();
        this.assertionFactory = assertionFactory;
    }

    /**
     * Successful exchange: the downstream access token plus its lifetime.
     * Token values are held only in memory and never logged.
     */
    public record Exchanged(String accessToken, long expiresIn, String issuedTokenType)
            implements ExchangeResult {
    }

    /**
     * Failed exchange: OAuth error code/description when the IdP supplied
     * them, or a transport/malformed-response classification otherwise.
     */
    public record Failure(String error, String errorDescription)
            implements ExchangeResult {
    }

    /** Typed exchange outcome. */
    public sealed interface ExchangeResult permits Exchanged, Failure {
    }

    /**
     * Exchanges {@code subjectToken} for an audience-limited token for
     * {@code audience} (the target service client id).
     */
    public ExchangeResult exchange(String subjectToken, String audience) {
        String assertion = assertionFactory.createAssertion();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", GRANT_TYPE_TOKEN_EXCHANGE);
        form.put("subject_token", subjectToken);
        form.put("subject_token_type", TOKEN_TYPE_ACCESS_TOKEN);
        form.put("requested_token_type", TOKEN_TYPE_ACCESS_TOKEN);
        form.put("audience", audience);
        form.put("client_id", ClientAssertionFactory.CLIENT_ID);
        form.put("client_assertion_type", CLIENT_ASSERTION_TYPE_JWT_BEARER);
        form.put("client_assertion", assertion);
        String body = form.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        try {
            // No onStatus handler: DefaultRestClient throws
            // RestClientResponseException for 4xx/5xx, which the catch below
            // maps via parseError (a no-op handler would SUPPRESS the
            // exception and feed an error body to parseSuccess instead).
            String responseBody = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseSuccess(responseBody, audience);
        } catch (RestClientResponseException e) {
            return parseError(e.getResponseBodyAsString(), e.getStatusCode().value(), audience);
        } catch (org.springframework.web.client.RestClientException e) {
            log.info("Token exchange transport failure for audience (error class={})",
                    e.getClass().getSimpleName());
            return new Failure("transport_error",
                    "The token endpoint could not be reached.");
        }
    }

    private ExchangeResult parseSuccess(String responseBody, String audience) {
        if (responseBody == null || responseBody.isBlank()) {
            log.info("Token exchange returned an empty response for audience");
            return new Failure("invalid_response", "Empty token endpoint response.");
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            String accessToken = node.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                log.info("Token exchange response lacked access_token for audience");
                return new Failure("invalid_response",
                        "Token endpoint response did not contain an access token.");
            }
            JsonNode expiresInNode = node.path("expires_in");
            long expiresIn = expiresInNode.isNumber() ? expiresInNode.asLong() : 0L;
            String issuedTokenType = node.path("issued_token_type").asText(null);
            return new Exchanged(accessToken, expiresIn, issuedTokenType);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.info("Token exchange response was not valid JSON for audience");
            return new Failure("invalid_response",
                    "Token endpoint response was not valid JSON.");
        }
    }

    private ExchangeResult parseError(String responseBody, int statusCode, String audience) {
        String error = "server_error";
        String description = "Token endpoint rejected the exchange (HTTP " + statusCode + ").";
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(responseBody);
                String oauthError = node.path("error").asText(null);
                String oauthDescription = node.path("error_description").asText(null);
                if (oauthError != null && !oauthError.isBlank()) {
                    error = oauthError;
                    description = oauthDescription != null && !oauthDescription.isBlank()
                            ? oauthDescription : description;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // keep the generic classification
            }
        }
        log.info("Token exchange rejected for audience (httpStatus={}, error={})",
                statusCode, error);
        return new Failure(error, description);
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
