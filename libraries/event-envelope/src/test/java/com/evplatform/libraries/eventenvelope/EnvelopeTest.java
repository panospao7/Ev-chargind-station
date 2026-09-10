package com.evplatform.libraries.eventenvelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvelopeTest {

    private static Map<String, Object> valid() {
        Map<String, Object> m = new HashMap<>();
        m.put("specversion", "1.0");
        m.put("type", "com.evplatform.booking.session.started.v1");
        m.put("source", "//booking-session-service");
        m.put("id", "123e4567-e89b-12d3-a456-426614174000");
        m.put("time", "2026-09-10T12:00:00Z");
        m.put("subject", "booking/b-1");
        return m;
    }

    @Test
    void validEnvelopeHasNoViolations() {
        assertTrue(CloudEventEnvelope.validate(valid()).isEmpty());
    }

    @Test
    void eachRequiredFieldIsEnforced() {
        for (String required : List.of("specversion", "type", "source", "id")) {
            Map<String, Object> m = valid();
            m.remove(required);
            assertTrue(CloudEventEnvelope.validate(m).stream()
                            .anyMatch(v -> v.contains(required)),
                    "removing " + required + " must be rejected");
        }
    }

    @Test
    void wrongSpecVersionAndBadTimeAreRejected() {
        Map<String, Object> m = valid();
        m.put("specversion", "0.3");
        assertTrue(CloudEventEnvelope.validate(m).stream().anyMatch(v -> v.contains("specversion")));

        Map<String, Object> t = valid();
        t.put("time", "yesterday-ish");
        assertTrue(CloudEventEnvelope.validate(t).stream().anyMatch(v -> v.contains("time")));
    }

    @Test
    void blankSourceIsRejected() {
        Map<String, Object> m = valid();
        m.put("source", "   ");
        assertTrue(CloudEventEnvelope.validate(m).stream().anyMatch(v -> v.contains("source")));
    }

    @Test
    void extensionAttributesAreTolerated() {
        Map<String, Object> m = valid();
        m.put("traceparent", "00-abc-def-01");
        assertTrue(CloudEventEnvelope.validate(m).isEmpty());
    }

    @Test
    void roundTripThroughJsonKeepsRequiredFields() throws Exception {
        CloudEventEnvelope envelope = new CloudEventEnvelope(
                "com.evplatform.booking.hold.created.v1",
                "//booking-session-service",
                "id-1", "2026-09-10T12:00:00Z", null, "application/json", Map.of("k", "v"));
        String json = new ObjectMapper().writeValueAsString(envelope.toMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = new ObjectMapper().readValue(json, Map.class);
        assertTrue(CloudEventEnvelope.validate(parsed).isEmpty());
    }

    /**
     * Parity guard: this validator must mirror the canonical executable
     * schema. If contracts/schemas/common/cloud-event.json drifts, this test
     * forces the library to be updated in the same change.
     */
    @Test
    void validatorMirrorsCanonicalSchema() throws Exception {
        Path schema = Path.of("..", "..", "contracts", "schemas", "common", "cloud-event.json");
        assertTrue(Files.exists(schema), "canonical schema must exist relative to the module");
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = new ObjectMapper().readValue(Files.readString(schema), Map.class);

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) doc.get("required");
        assertEquals(new ArrayList<>(List.of("specversion", "type", "source", "id")), required);

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) doc.get("properties");
        assertTrue(props.keySet().containsAll(
                List.of("specversion", "type", "source", "id", "time", "subject", "datacontenttype", "data")));
    }
}
