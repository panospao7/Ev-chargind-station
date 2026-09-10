package com.evplatform.libraries.securelogging;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redaction by construction (AGENTS.md §10): sensitive fields are removed
 * before serialization, never filtered after logging.
 */
public final class Redactor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Redactor() {
    }

    /** Recursively masks every sensitive key; returns a new map. */
    public static Map<String, Object> redact(Map<String, Object> input) {
        if (input == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            if (SensitiveFields.isSensitive(e.getKey())) {
                out.put(e.getKey(), SensitiveFields.MASK);
            } else if (e.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) nested;
                out.put(e.getKey(), redact(cast));
            } else {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /**
     * JSON-string convenience: parses, masks, re-serializes. Malformed or
     * unserializable input collapses to a fixed safe marker — it can never
     * echo the input back into a log.
     */
    public static String redactJson(String json) {
        if (json == null) {
            return SensitiveFields.MASK;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode tree = MAPPER.readTree(json);
            if (tree instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = MAPPER.convertValue(tree, Map.class);
                return MAPPER.writeValueAsString(redact(cast));
            }
            return MAPPER.writeValueAsString(tree);
        } catch (Exception e) {
            return "<unserializable: redacted>";
        }
    }
}
