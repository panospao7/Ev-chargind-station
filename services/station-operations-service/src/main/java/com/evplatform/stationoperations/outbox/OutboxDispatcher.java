package com.evplatform.stationoperations.outbox;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Polling outbox dispatcher (ARC-022 §8.1; FR-PLT-01/03).
 *
 * Publishes PENDING rows in per-aggregate version order using publisher
 * confirms (synchronous CorrelationData future) with mandatory flagging.
 * Failed or unconfirmed publishes increment the attempt count and back off;
 * exhaustion moves the row to QUARANTINED (safe failure category recorded).
 * Delivery is at-least-once: consumers deduplicate via the inbox
 * (FR-PLT-02).
 */
@Component
public class OutboxDispatcher {

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final int maxAttempts;
    private final long backoffMs;
    private final long confirmTimeoutMs;

    public OutboxDispatcher(JdbcClient jdbc, RabbitTemplate rabbit,
                            @Value("${outbox.max-attempts:3}") int maxAttempts,
                            @Value("${outbox.backoff-ms:2000}") long backoffMs,
                            @Value("${outbox.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
        this.confirmTimeoutMs = confirmTimeoutMs;
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

    record OutboxRow(UUID messageId, String messageType, String payload, int attemptCount) {
    }

    private List<OutboxRow> pendingBatch() {
        return jdbc.sql("""
                SELECT message_id, message_type, payload, attempt_count
                FROM station_operations.outbox_message
                WHERE state = 'PENDING' AND available_at <= now()
                ORDER BY aggregate_ref, aggregate_version, occurred_at
                LIMIT 100
                """)
                .query((rs, i) -> new OutboxRow(
                        rs.getObject("message_id", UUID.class),
                        rs.getString("message_type"),
                        rs.getString("payload"),
                        rs.getInt("attempt_count")))
                .list();
    }

    /** One dispatch pass over the pending batch. Safe to call repeatedly. */
    public int dispatchOnce() {
        int published = 0;
        for (OutboxRow row : pendingBatch()) {
            String routingKey = routingKeyFor(row.messageType());
            try {
                CorrelationData correlation = new CorrelationData(row.messageId().toString());
                rabbit.convertAndSend(RabbitTopologyConfiguration.DOMAIN_EXCHANGE, routingKey, row.payload(),
                        m -> {
                            m.getMessageProperties().setCorrelationId(row.messageId().toString());
                            return m;
                        },
                        correlation);
                CorrelationData.Confirm confirm = correlation.getFuture()
                        .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
                if (confirm != null && confirm.isAck()) {
                    markPublished(row.messageId());
                    published++;
                } else {
                    markAttempt(row.messageId(), confirm == null ? "confirm-timeout" : "nacked");
                }
            } catch (Exception e) {
                markAttempt(row.messageId(), e.getClass().getSimpleName());
            }
        }
        return published;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}",
               initialDelayString = "${outbox.initial-delay-ms:5000}")
    public void scheduledDispatch() {
        dispatchOnce();
    }

    private void markPublished(UUID messageId) {
        jdbc.sql("""
                UPDATE station_operations.outbox_message
                SET state = 'PUBLISHED', published_at = now(), attempt_count = attempt_count + 1
                WHERE message_id = ?
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
