package com.evplatform.discoveryinsights.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import com.rabbitmq.client.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes com.evplatform.station.published.v1 facts from the
 * discovery.station.published quorum queue and maintains the
 * discovery_insights.station_search_projection.
 *
 * <p>Ordering state is PER AGGREGATE (ARC-014 §5: aggregate version is the
 * ordering key): the applied version for a station lives on its own
 * projection row (ARC-022 §9 — every projection records its source aggregate
 * version, and an older source version cannot replace a newer one). The
 * projection_checkpoint row is the stream-level high-water mark used for
 * rebuild/resume bookkeeping; it advances monotonically (never backwards)
 * and records the most recently observed version gap for operator
 * visibility.</p>
 *
 * <p>Per-message pipeline, one DB transaction for all effects (ARC-022 §8.2:
 * completion and business effects commit atomically):</p>
 * <ol>
 *   <li>Envelope parse + required attributes; poison → reject to DLQ.</li>
 *   <li>Type filter: unknown types are dead-lettered as misrouted
 *       (disclosed choice: silently dropping would hide routing defects;
 *       the queue binding is type-specific today, so anything else arriving
 *       here is an operator-visible anomaly).</li>
 *   <li>Inbox insert ON CONFLICT DO NOTHING — duplicate → ack and stop
 *       (first delivery already finished; nothing else to do).</li>
 *   <li>Checkpoint version discipline (PER AGGREGATE):
 *       v == station's applied version + 1 (or the station's first fact) →
 *       apply upsert (§7.1 older-cannot-replace guard is enforced in SQL),
 *       advance the stream checkpoint monotonically, audit, COMPLETED;</li>
 *   <li>Gap strategy (chosen, disclosed): v &gt; station's applied version + 1
 *       → record the gap in the checkpoint (gap_from_version,
 *       gap_recorded_at) and APPLY THE FACT IMMEDIATELY in the same
 *       transaction. Rationale: the payload is a FULL station snapshot
 *       (station-published-event.json), so applying a later snapshot late is
 *       safe — any intermediate version that later arrives is rejected by
 *       the SQL guard (source_version &gt; existing only), and the
 *       station's applied version still advances to the highest applied
 *       version. The gap stays recorded (gap_from_version/gap_recorded_at
 *       are intentionally NOT cleared after a gap-apply) for operator
 *       visibility until the next in-sequence fact overwrites the
 *       checkpoint row (which clears gap fields). The inbox row records
 *       attempt accounting; requeue-based gap redelivery loops were
 *       deliberately not implemented — they add redelivery-loop risk
 *       without correctness benefit given full snapshots.</li>
 *   <li>Transient DataAccessException → rollback and rethrow; the container
 *       retry (spring.rabbitmq.listener.simple.retry: 3 attempts, 500ms
 *       initial) redelivers; exhausted retries dead-letter via the broker
 *       (container reject → DLQ). Disclosed simplification per task packet.</li>
 * </ol>
 *
 * <p>Manual acks (ackMode MANUAL): ack on every terminal outcome
 * (completed/skipped/duplicate), reject-without-requeue for poison/unknown
 * type, rethrow for transient DB failures so the container retry/dead-letter
 * path handles redelivery. Cancellation-safe: the container stops delivery
 * on shutdown; unacked messages are requeued by the broker.</p>
 */
@Component
public class StationPublishedConsumer {

    private static final Logger log = LoggerFactory.getLogger(StationPublishedConsumer.class);

    static final String CONSUMER_NAME = "discovery-station-projection";
    static final String PROJECTION_NAME = "station_search_projection";
    static final String SOURCE_REF = "station-operations-service";
    static final String EXPECTED_TYPE = "com.evplatform.station.published.v1";
    /** Returned by {@link #readStationAppliedVersion} when the station has no
     *  projection row yet (its first fact). Also the initial checkpoint value. */
    private static final long NO_ROW_SENTINEL = -1L;

    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final JdbcClient jdbc;
    private final int gapRetryAttempts;

    public StationPublishedConsumer(
            @Autowired(required = false) ObjectMapper injectedObjectMapper,
            PlatformTransactionManager transactionManager,
            JdbcClient jdbc,
            @org.springframework.beans.factory.annotation.Value(
                    "${discovery.consumer.gap-retry-attempts:3}") int gapRetryAttempts) {
        this.objectMapper = injectedObjectMapper != null
                ? injectedObjectMapper : new ObjectMapper();
        this.tx = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
        this.gapRetryAttempts = gapRetryAttempts;
    }

    @RabbitListener(
            queues = RabbitTopologyConfiguration.STATION_PUBLISHED_QUEUE,
            ackMode = "MANUAL",
            concurrency = "1")
    void onMessage(Message message, Channel channel,
                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        handle(message, channel, deliveryTag);
    }

    void handle(Message message, Channel channel, long deliveryTag) throws Exception {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(message.getBody());
        } catch (Exception parseFailure) {
            log.warn("Discovery consumer: unparseable envelope ({} bytes) rejected to DLQ "
                    + "[category=ENVELOPE_PARSE_FAILED]", message.getBody().length);
            rejectToDlq(channel, deliveryTag);
            return;
        }

        if (!requiredEnvelopeAttributesPresent(envelope)) {
            log.warn("Discovery consumer: envelope missing required attributes rejected to DLQ "
                    + "[category=ENVELOPE_INVALID]");
            rejectToDlq(channel, deliveryTag);
            return;
        }

        String type = envelope.get("type").asText();
        if (!EXPECTED_TYPE.equals(type)) {
            log.warn("Discovery consumer: unexpected message type '{}' on the "
                            + "station.published queue rejected to DLQ [category=UNEXPECTED_TYPE]",
                    type);
            rejectToDlq(channel, deliveryTag);
            return;
        }

        UUID messageId;
        try {
            messageId = UUID.fromString(envelope.get("id").asText());
        } catch (IllegalArgumentException badId) {
            log.warn("Discovery consumer: envelope id is not a UUID rejected to DLQ "
                    + "[category=ENVELOPE_INVALID]");
            rejectToDlq(channel, deliveryTag);
            return;
        }

        if (!envelope.hasNonNull("aggregateid") || !envelope.hasNonNull("aggregateversion")
                || !envelope.get("aggregateversion").isNumber()) {
            log.warn("Discovery consumer: envelope missing aggregate extension attributes "
                    + "rejected to DLQ [category=ENVELOPE_INVALID]");
            rejectToDlq(channel, deliveryTag);
            return;
        }

        String aggregateRef = envelope.get("aggregateid").asText();
        long aggregateVersion = envelope.get("aggregateversion").asLong();
        String correlationId = envelope.hasNonNull("correlationid")
                ? envelope.get("correlationid").asText() : null;

        try {
            process(messageId, aggregateRef, aggregateVersion, correlationId, envelope);
            channel.basicAck(deliveryTag, false);
        } catch (DataAccessException transientOrDbFailure) {
            // rollback already done by TransactionTemplate; let the container
            // retry (bounded) and finally dead-letter handle redelivery
            log.warn("Discovery consumer: transient DB failure for message {} "
                    + "[category=TRANSIENT_DB_FAILURE]", messageId);
            throw new AmqpRejectAndDontRequeueException(
                    "transient DB failure; container retry then DLQ", transientOrDbFailure);
        }
    }

    private boolean requiredEnvelopeAttributesPresent(JsonNode envelope) {
        return envelope != null
                && envelope.hasNonNull("specversion")
                && envelope.hasNonNull("type")
                && envelope.hasNonNull("source")
                && envelope.hasNonNull("id");
    }

    private void rejectToDlq(Channel channel, long deliveryTag) throws Exception {
        channel.basicNack(deliveryTag, false, false);
    }

    private void process(UUID messageId, String aggregateRef, long aggregateVersion,
                         String correlationId, JsonNode envelope) {
        tx.executeWithoutResult(status -> {
            boolean first = insertInboxRow(messageId, aggregateVersion);
            if (!first) {
                log.debug("Discovery consumer: duplicate delivery of message {} skipped",
                        messageId);
                return; // duplicate: first delivery already completed
            }

            Checkpoint checkpoint = readOrInsertCheckpoint();

            // Version discipline is PER AGGREGATE (ARC-014 §5): the station's
            // own projection row holds its applied source version (ARC-022 §9
            // §7.1 guard). The checkpoint is the stream high-water mark for
            // rebuild/resume bookkeeping only — a NEW station's first fact
            // (e.g. v0 after another station reached v5) must never be
            // mistaken for an out-of-order fact of an already-known station.
            long stationAppliedVersion = readStationAppliedVersion(aggregateRef);
            if (aggregateVersion <= stationAppliedVersion) {
                markInboxSkipped(messageId, "OLDER_VERSION");
                log.debug("Discovery consumer: message {} for station {} version {} "
                        + "not newer than the station's applied version {} — skipped",
                        messageId, aggregateRef, aggregateVersion, stationAppliedVersion);
                return;
            }

            boolean inSequence = stationAppliedVersion == NO_ROW_SENTINEL
                    || aggregateVersion == stationAppliedVersion + 1;

            applyProjection(envelope, aggregateRef, aggregateVersion);

            if (inSequence) {
                // Stream high-water mark: a fact that is in sequence for ITS
                // station may still be older than the stream checkpoint (a
                // new station's first fact after another station advanced
                // far ahead) — never rewind, never clear recorded gaps.
                if (aggregateVersion > checkpoint.lastAppliedVersion()) {
                    advanceCheckpoint(messageId, aggregateVersion, null, null);
                }
            } else {
                long gapFrom = stationAppliedVersion + 1;
                // Record the gap but never rewind the stream high-water mark
                // below the highest applied version.
                advanceCheckpoint(messageId,
                        Math.max(aggregateVersion, checkpoint.lastAppliedVersion()),
                        gapFrom, Instant.now());
                markInboxGapRecorded(messageId, gapFrom);
                log.warn("Discovery consumer: version gap {}..{} recorded and fact applied "
                        + "from full snapshot (message {})", gapFrom, aggregateVersion - 1,
                        messageId);
            }

            insertAuditRow(messageId, correlationId, envelope);
            markInboxCompleted(messageId);
        });
    }

    /** @return true when this delivery is the first for (consumer, message_id). */
    private boolean insertInboxRow(UUID messageId, long aggregateVersion) {
        return jdbc.sql("""
                        INSERT INTO discovery_insights.inbox_message
                            (consumer_name, message_id, message_type, aggregate_type,
                             aggregate_version, processing_outcome)
                        VALUES (?, ?, ?, 'Station', ?, 'PROCESSING')
                        ON CONFLICT (consumer_name, message_id) DO NOTHING
                        RETURNING message_id
                        """)
                .param(CONSUMER_NAME)
                .param(messageId)
                .param(EXPECTED_TYPE)
                .param(aggregateVersion)
                .query((rs, i) -> rs.getString(1))
                .optional()
                .isPresent();
    }

    private record Checkpoint(long lastAppliedVersion) {
    }

    private Checkpoint readOrInsertCheckpoint() {
        jdbc.sql("""
                        INSERT INTO discovery_insights.projection_checkpoint
                            (projection_name, source_ref, last_applied_version)
                        VALUES (?, ?, -1)
                        ON CONFLICT (projection_name, source_ref) DO NOTHING
                        """)
                .param(PROJECTION_NAME)
                .param(SOURCE_REF)
                .update();
        return readCheckpoint();
    }

    private Checkpoint readCheckpoint() {
        return jdbc.sql("""
                        SELECT last_applied_version
                        FROM discovery_insights.projection_checkpoint
                        WHERE projection_name = ? AND source_ref = ?
                        """)
                .param(PROJECTION_NAME)
                .param(SOURCE_REF)
                .query((rs, i) -> new Checkpoint(rs.getLong("last_applied_version")))
                .single();
    }

    /**
     * The station's own applied source version, read from its projection row
     * (ARC-022 §9: every projection records its source aggregate version).
     * Returns {@link #NO_ROW_SENTINEL} when the station has no row yet —
     * its first fact. This is the per-aggregate ordering gate; the global
     * checkpoint row is stream bookkeeping only.
     */
    private long readStationAppliedVersion(String aggregateRef) {
        return jdbc.sql("""
                        SELECT COALESCE(source_version, -1)
                        FROM discovery_insights.station_search_projection
                        WHERE station_ref = ?::uuid
                        """)
                .param(aggregateRef)
                .query((rs, i) -> rs.getLong(1))
                .optional()
                .orElse(NO_ROW_SENTINEL);
    }

    /**
     * Stream high-water mark update. {@code targetVersion} is clamped by the
     * caller to never rewind the mark; {@code last_applied_message_id} and
     * the gap fields change only when the mark strictly advances (all SET
     * expressions see the pre-update row, so the CASE guards compare against
     * the OLD value). An update that does not carry a gap clears existing
     * gap fields when the mark strictly advances (the next in-sequence fact
     * overwrites the recorded gap); a below-the-mark update leaves them
     * untouched.
     */
    private void advanceCheckpoint(UUID messageId, long targetVersion,
                                   Long gapFrom, Instant gapRecordedAt) {
        if (gapFrom == null) {
            jdbc.sql("""
                            UPDATE discovery_insights.projection_checkpoint
                            SET last_applied_version = GREATEST(last_applied_version, ?),
                                last_applied_message_id =
                                    CASE WHEN ? > last_applied_version
                                         THEN ? ELSE last_applied_message_id END,
                                gap_from_version =
                                    CASE WHEN ? > last_applied_version
                                         THEN NULL ELSE gap_from_version END,
                                gap_recorded_at =
                                    CASE WHEN ? > last_applied_version
                                         THEN NULL ELSE gap_recorded_at END,
                                updated_at = now()
                            WHERE projection_name = ? AND source_ref = ?
                            """)
                    .param(targetVersion)
                    .param(targetVersion).param(messageId)
                    .param(targetVersion)
                    .param(targetVersion)
                    .param(PROJECTION_NAME)
                    .param(SOURCE_REF)
                    .update();
        } else {
            jdbc.sql("""
                            UPDATE discovery_insights.projection_checkpoint
                            SET last_applied_version = GREATEST(last_applied_version, ?),
                                last_applied_message_id =
                                    CASE WHEN ? > last_applied_version
                                         THEN ? ELSE last_applied_message_id END,
                                gap_from_version = ?, gap_recorded_at = ?, updated_at = now()
                            WHERE projection_name = ? AND source_ref = ?
                            """)
                    .param(targetVersion)
                    .param(targetVersion).param(messageId)
                    .param(gapFrom)
                    .param(Timestamp.from(gapRecordedAt))
                    .param(PROJECTION_NAME)
                    .param(SOURCE_REF)
                    .update();
        }
    }

    /**
     * §7.1 older-cannot-replace guard at the SQL level: the DO UPDATE runs
     * only when the incoming snapshot is strictly newer than the stored one.
     */
    private void applyProjection(JsonNode envelope, String sourceRef, long sourceVersion) {
        JsonNode data = envelope.get("data");
        jdbc.sql("""
                        INSERT INTO discovery_insights.station_search_projection
                            (station_ref, public_ref, display_name, address_line, city,
                             postal_code, country_code, latitude, longitude,
                             source_version, source_ref, projection_state)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                        ON CONFLICT (station_ref) DO UPDATE SET
                            public_ref     = EXCLUDED.public_ref,
                            display_name   = EXCLUDED.display_name,
                            address_line   = EXCLUDED.address_line,
                            city           = EXCLUDED.city,
                            postal_code    = EXCLUDED.postal_code,
                            country_code   = EXCLUDED.country_code,
                            latitude       = EXCLUDED.latitude,
                            longitude      = EXCLUDED.longitude,
                            source_version = EXCLUDED.source_version,
                            source_ref     = EXCLUDED.source_ref,
                            updated_at     = now()
                        WHERE EXCLUDED.source_version > station_search_projection.source_version
                        """)
                .param(uuidOrNull(data, "stationRef"))
                .param(textOrNull(data, "publicRef"))
                .param(textOrNull(data, "displayName"))
                .param(textOrNull(data, "addressLine"))
                .param(textOrNull(data, "city"))
                .param(textOrNull(data, "postalCode"))
                .param(textOrNull(data, "countryCode"))
                .param(data.get("latitude").decimalValue())
                .param(data.get("longitude").decimalValue())
                .param(sourceVersion)
                .param(sourceRef)
                .update();
    }

    private void markInboxSkipped(UUID messageId, String category) {
        jdbc.sql("""
                        UPDATE discovery_insights.inbox_message
                        SET processing_outcome = 'SKIPPED', completed_at = now(),
                            attempt_count = attempt_count + 1, failure_category = ?
                        WHERE consumer_name = ? AND message_id = ?
                        """)
                .param(category)
                .param(CONSUMER_NAME)
                .param(messageId)
                .update();
    }

    /** Gap accounting on the inbox row (attempt bookkeeping for operators). */
    private void markInboxGapRecorded(UUID messageId, long gapFrom) {
        jdbc.sql("""
                        UPDATE discovery_insights.inbox_message
                        SET attempt_count = attempt_count + 1, failure_category = 'VERSION_GAP'
                        WHERE consumer_name = ? AND message_id = ?
                        """)
                .param(CONSUMER_NAME)
                .param(messageId)
                .update();
    }

    private void markInboxCompleted(UUID messageId) {
        jdbc.sql("""
                        UPDATE discovery_insights.inbox_message
                        SET processing_outcome = 'COMPLETED', completed_at = now(),
                            failure_category = NULL
                        WHERE consumer_name = ? AND message_id = ?
                        """)
                .param(CONSUMER_NAME)
                .param(messageId)
                .update();
    }

    private void insertAuditRow(UUID messageId, String correlationId, JsonNode envelope) {
        String publicRef = textOrNull(envelope.get("data"), "publicRef");
        UUID correlation = null;
        if (correlationId != null) {
            try {
                correlation = UUID.fromString(correlationId);
            } catch (IllegalArgumentException nonUuidCorrelation) {
                correlation = null; // audit must never fail the business effect
            }
        }
        jdbc.sql("""
                        INSERT INTO discovery_insights.audit_event
                            (audit_ref, actor, calling_service, action, target, outcome,
                             correlation_id, classification)
                        VALUES (?, 'system', 'discovery-insights-service',
                                'APPLY_STATION_PUBLISHED', ?, 'SUCCESS', ?, 'BUSINESS')
                        """)
                .param(UUID.randomUUID())
                .param("station/" + publicRef)
                .param(correlation)
                .update();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        String value = textOrNull(node, field);
        return value == null ? null : UUID.fromString(value);
    }
}
