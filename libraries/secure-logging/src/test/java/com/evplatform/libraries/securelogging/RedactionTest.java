package com.evplatform.libraries.securelogging;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactionTest {

    @Test
    void neverLogListIsMaskedByName() {
        // mirrors ENG-001 doc §9 "Never log" entries
        Map<String, Object> in = Map.of(
                "accessToken", "Bearer at_123",
                "refreshToken", "rt_123",
                "clientSecret", "cs_123",
                "password", "hunter2",
                "authorization", "Basic abc",
                "privateKey", "-----BEGIN",
                "qrSecretValue", "qr_123",
                "emailAddress", "driver@example.com");
        Map<String, Object> out = Redactor.redact(in);
        out.values().forEach(v ->
                assertEquals(SensitiveFields.MASK, v, "every never-log field must be masked"));
    }

    @Test
    void caseAndSeparatorVariantsAreCaught() {
        assertTrue(SensitiveFields.isSensitive("access_token"));
        assertTrue(SensitiveFields.isSensitive("ACCESS-TOKEN"));
        assertTrue(SensitiveFields.isSensitive("Authorization"));
        assertTrue(SensitiveFields.isSensitive("client.secret"));
        assertTrue(SensitiveFields.isSensitive("start_authorization_secret"));
    }

    @Test
    void technicalFieldsSurvive() {
        Map<String, Object> in = Map.of(
                "booking_ref", "8f14e45f-ea09-...", "state", "HELD", "attempt", 3);
        Map<String, Object> out = Redactor.redact(in);
        assertEquals(in, out, "technical references must pass through untouched");
    }

    @Test
    void nestedMapsAreRedactedRecursively() {
        Map<String, Object> in = Map.of(
                "booking_ref", "b-1",
                "context", Map.of("sessionToken", "tok", "correlationId", "c-1"));
        Map<String, Object> out = Redactor.redact(in);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) out.get("context");
        assertEquals(SensitiveFields.MASK, nested.get("sessionToken"));
        assertEquals("c-1", nested.get("correlationId"));
    }

    @Test
    void redactJsonMasksAndNeverEchoesMalformedInput() {
        String out = Redactor.redactJson("{\"booking_ref\":\"b-1\",\"password\":\"hunter2\"}");
        assertTrue(out.contains(SensitiveFields.MASK));
        assertFalse(out.contains("hunter2"));

        String malformed = Redactor.redactJson("{not json with password:hunter2}");
        assertFalse(malformed.contains("hunter2"), "malformed input must never echo");
        assertTrue(malformed.contains("<unserializable"));
    }

    @Test
    void unnamedValuesFailClosed() {
        assertTrue(SensitiveFields.isSensitive(null), "unnamed values are treated as sensitive");
    }
}
