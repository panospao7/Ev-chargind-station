package com.evplatform.stationoperations.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Polling outbox dispatcher (ARC-022 §8.1; FR-PLT-01/03).
 *
 * Dispatch uses a claim-then-send protocol: a single auto-commit UPDATE
 * claims a batch of PENDING rows by pushing their available_at forward
 * (FOR UPDATE SKIP LOCKED inside the claim), so no database lock or
 * transaction is ever held across a broker send. Claimed rows are then
 * published one by one with publisher confirms (synchronous
 * CorrelationData future). Failed or unconfirmed publishes increment the
 * attempt count and back off; exhaustion moves the row to QUARANTINED
 * (safe failure category recorded). Delivery is at-least-once: consumers
 * deduplicate via the inbox (FR-PLT-02). Cross-instance dispatch may
 * reorder per-aggregate facts across batches; consumers order by
 * aggregate version (ARC-014 §5). Wire envelopes carry the ARC-020 §2 /
 * ARC-004 §4 extension attributes, derived from the outbox columns at send
 * time (see {@link #enrichedPayload}).
 */
@Component
public class OutboxDispatcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * ARC-020 §2 / ARC-004 §4 dataschema mapping: message_type → the $id of
     * the executable event schema. Unmapped message types emit no dataschema
     * attribute (disclosed behavior; no fail-fast).
     */
    private static final Map<String, String> DATASCHEMA_BY_MESSAGE_TYPE = Map.of(
            "com.evplatform.station.published.v1",
            "https://schema-registry.example.com/events/station-published-event.json");

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final int maxAttempts;
    private final long backoffMs;
    private final long confirmTimeoutMs;
    private final int claimLeaseSeconds;

    public OutboxDispatcher(JdbcClient jdbc, RabbitTemplate rabbit,
                            @Value("${outbox.max-attempts:3}") int maxAttempts,
                            @Value("${outbox.backoff-ms:2000}") long backoffMs,
                            @Value("${outbox.confirm-timeout-ms:5000}") long confirmTimeoutMs,
                            @Value("${outbox.claim-lease-seconds:30}") int claimLeaseSeconds) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
        this.confirmTimeoutMs = confirmTimeoutMs;
        this.claimLeaseSeconds = claimLeaseSeconds;
    }

    /** Routing key derived from the registry naming convention:
     *  com.evplatform.<domain>.<event>.v1 → <domain>.<event> */
    static String routingKeyFor(String messageType) {
        String key = messageType;
        if (key.startsWith("com.evplatform.")) {
            key = key.substring("com.evplatform.".length());
        }
        if (key.endsWith(".v1")) {
            key = key.substring(0, key.length() - 3);
        }
        return key;
    }

    record OutboxRow(UUID messageId, String messageType, String payload, int attemptCount,
                     UUID correlationId, UUID causationId, UUID aggregateRef,
                     long aggregateVersion, String classification) {
    }

    /**
     * Claims one batch of due PENDING rows in a single auto-commit
     * statement. The UPDATE pushes available_at forward by the claim lease,
     * which makes the claim itself the mutual-exclusion mechanism: other
     * dispatcher instances (or the next poll) skip rows whose available_at
     * is still in the future. No transaction — and therefore no row lock —
     * is held across the broker send. attempt_count is deliberately NOT
     * incremented here; only markAttempt accounts failures.
     */
    private List<OutboxRow> claimBatch() {
        return jdbc.sql("""
                UPDATE station_operations.outbox_message
                SET available_at = now() + make_interval(secs => ?)
                WHERE message_id IN (
                    SELECT message_id
                    FROM station_operations.outbox_message
                    WHERE state = 'PENDING' AND available_at <= now()
                    ORDER BY aggregate_ref, aggregate_version, occurred_at
                    LIMIT 100
                    FOR UPDATE SKIP LOCKED)
                RETURNING message_id, message_type, payload, attempt_count,
                         correlation_id, causation_id, aggregate_ref, aggregate_version,
                         classification
                """)
                .param(claimLeaseSeconds)
                .query((rs, i) -> new OutboxRow(
                        rs.getObject("message_id", UUID.class),
                        rs.getString("message_type"),
                        rs.getString("payload"),
                        rs.getInt("attempt_count"),
                        rs.getObject("correlation_id", UUID.class),
                        rs.getObject("causation_id", UUID.class),
                        rs.getObject("aggregate_ref", UUID.class),
                        rs.getLong("aggregate_version"),
                        rs.getString("classification")))
                .list();
    }

    /**
     * Derives the wire envelope from the stored outbox payload by adding the
     * ARC-020 §2 / ARC-004 §4 extension attributes, sourced from the outbox
     * columns claimed in the same batch (single read, no second lookup):
     *
     * <ul>
     *   <li>{@code dataschema} — from {@link #DATASCHEMA_BY_MESSAGE_TYPE};
     *       omitted for unmapped message types (disclosed behavior, no
     *       fail-fast)</li>
     *   <li>{@code correlationid} — correlation_id column (NOT NULL)</li>
     *   <li>{@code aggregateid} — aggregate_ref column</li>
     *   <li>{@code aggregateversion} — aggregate_version column, as a JSON
     *       number</li>
     *   <li>{@code causationid} — only when causation_id is non-NULL; the
     *       attribute is omitted entirely otherwise (never serialized as
     *       null)</li>
     *   <li>{@code classification} — classification column (ARC-004 §4
     *       extension, value from the outbox column)</li>
     *   <li>{@code traceparent} — never emitted: there is no ambient trace
     *       context here and none is fabricated</li>
     * </ul>
     *
     * The stored payload is expected to be a JSON object (the CloudEvents
     * envelope the writer validated). A parse failure propagates to the
     * caller's catch in {@link #dispatchOnce()} and is retried then
     * quarantined like any other dispatch failure — malformed payloads are
     * never sent to the broker.
     */
    static String enrichedPayload(OutboxRow row) {
        ObjectNode envelope;
        try {
            envelope = (ObjectNode) MAPPER.readTree(row.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "outbox payload is not a JSON object; message_id=" + row.messageId(), e);
        }
        String dataschema = DATASCHEMA_BY_MESSAGE_TYPE.get(row.messageType());
        if (dataschema != null) {
            envelope.put("dataschema", dataschema);
        }
        envelope.put("correlationid", row.correlationId().toString());
        envelope.put("aggregateid", row.aggregateRef().toString());
        envelope.put("aggregateversion", row.aggregateVersion());
        if (row.causationId() != null) {
            envelope.put("causationid", row.causationId().toString());
        }
        envelope.put("classification", row.classification());
        return envelope.toString();
    }

    /** One dispatch pass over the claimed batch. Safe to call repeatedly. */
    public int dispatchOnce() {
        int published = 0;
        for (OutboxRow row : claimBatch()) {
            String routingKey = routingKeyFor(row.messageType());
            try {
                CorrelationData correlation = new CorrelationData(row.messageId().toString());
                rabbit.convertAndSend(RabbitTopologyConfiguration.DOMAIN_EXCHANGE, routingKey,
                        enrichedPayload(row),
                        m -> {
                            m.getMessageProperties().setCorrelationId(row.messageId().toString());
                            m.getMessageProperties().setContentType("application/json");
                            return m;
                        },
                        correlation);
                CorrelationData.Confirm confirm = correlation.getFuture()
                        .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
                if (confirm != null && confirm.isAck()) {
                    if (correlation.getReturned() != null) {
                        // Acked but returned: accepted by the broker yet
                        // unroutable to any queue (mandatory flagging).
                        markAttempt(row.messageId(), "UNROUTABLE");
                    } else {
                        markPublished(row.messageId());
                        published++;
                    }
                } else {
                    markAttempt(row.messageId(), confirm == null ? "confirm-timeout" : "nacked");
                }
            } catch (Exception e) {
                // failure_category is varchar(48): an over-long exception
                // class name would throw inside this catch and abort the
                // batch, so the category is truncated to the column bound.
                String category = e.getClass().getSimpleName();
                if (category.length() > 48) {
                    category = category.substring(0, 48);
                }
                markAttempt(row.messageId(), category);
            }
        }
        return published;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}",
               initialDelayString = "${outbox.initial-delay-ms:5000}")
    public void scheduledDispatch() {
        dispatchOnce();
    }

    /**
     * CAS publish confirmation: the state guard makes marking idempotent —
     * a row that was re-claimed (lease expiry after a slow send) and marked
     * by another pass cannot be flipped again here.
     */
    private void markPublished(UUID messageId) {
        jdbc.sql("""
                UPDATE station_operations.outbox_message
                SET state = 'PUBLISHED', published_at = now(), attempt_count = attempt_count + 1
                WHERE message_id = ? AND state = 'PENDING'
                """).param(messageId).update();
    }

    private void markAttempt(UUID messageId, String failureCategory) {
        jdbc.sql("""
                UPDATE station_operations.outbox_message
                SET attempt_count = attempt_count + 1,
                    failure_category = ?,
                    available_at = now() + make_interval(secs => ?),
                    state = CASE WHEN attempt_count + 1 >= ?
                                 THEN 'QUARANTINED' ELSE state END
                WHERE message_id = ? AND state = 'PENDING'
                """)
                .param(failureCategory)
                .param(backoffMs / 1000.0)
                .param(maxAttempts)
                .param(messageId)
                .update();
    }
}
