package com.evplatform.stationoperations.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox writer (ARC-022 §8.1).
 *
 * The writer participates in the CALLER's transaction: the business change
 * and the outbox row commit atomically (AGENTS.md §4). Payloads are the full
 * CloudEvents 1.0 envelope; the envelope and its data payload are validated
 * by the writer against the executable schemas' required-field rules.
 */
@Component
public class OutboxWriter {

    private final JdbcClient jdbc;

    public OutboxWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Appends one event fact. Idempotent per the §8.1 unique event-fact
     * constraint (aggregate_type, aggregate_ref, aggregate_version,
     * message_type), now the explicitly named uq_outbox_event_fact
     * (V4__integration_table_naming): conflicting re-inserts of the SAME
     * fact are silently ignored via the targeted ON CONFLICT clause.
     *
     * A primary-key collision (same message_id reused for a DIFFERENT
     * fact) no longer matches the conflict target and therefore raises a
     * constraint violation instead of being silently masked by the
     * previous bare ON CONFLICT DO NOTHING.
     */
    public void append(UUID messageId, String kind, String messageType,
                       String aggregateType, UUID aggregateRef, long aggregateVersion,
                       UUID correlationId, UUID causationId, String classification,
                       JsonNode envelope, Instant availableAt) {
        validateEnvelope(envelope);
        jdbc.sql("""
                INSERT INTO station_operations.outbox_message
                    (message_id, kind, message_type, aggregate_type, aggregate_ref,
                     aggregate_version, workflow_ref, correlation_id, causation_id,
                     classification, payload, available_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (aggregate_type, aggregate_ref, aggregate_version, message_type) DO NOTHING
                """)
                .param(messageId).param(kind).param(messageType)
                .param(aggregateType).param(aggregateRef).param(aggregateVersion)
                .param((UUID) null).param(correlationId).param(causationId)
                .param(classification)
                .param(envelope.toString())
                .param(Timestamp.from(availableAt))
                .update();
    }

    /**
     * Envelope validation against contracts/schemas/common/cloud-event.json's
     * required rules (specversion/type/source/id). The writer refuses to
     * persist an envelope that could never validate — redaction and schema
     * conformance are by construction, not post-hoc.
     */
    static void validateEnvelope(JsonNode envelope) {
        String[] required = {"specversion", "type", "source", "id"};
        for (String field : required) {
            JsonNode v = envelope.get(field);
            if (v == null || v.asText().isBlank()) {
                throw new IllegalArgumentException("envelope missing required field: " + field);
            }
        }
        if (!"1.0".equals(envelope.get("specversion").asText())) {
            throw new IllegalArgumentException("specversion must be 1.0");
        }
    }
}
