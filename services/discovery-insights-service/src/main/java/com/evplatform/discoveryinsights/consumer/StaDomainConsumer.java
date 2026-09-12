package com.evplatform.discoveryinsights.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Consumes the four station-domain fact families from the
 * discovery.sta.domain quorum queue and maintains the Discovery search
 * projections:
 * <ul>
 *   <li>{@code com.evplatform.station.published.v1} → station_search_projection</li>
 *   <li>{@code com.evplatform.station.evse-configuration-changed.v1} →
 *       evse_search_projection</li>
 *   <li>{@code com.evplatform.station.connector-configuration-changed.v1} →
 *       connector_search_projection</li>
 *   <li>{@code com.evplatform.station.tariff-published.v1} →
 *       tariff_public_projection</li>
 * </ul>
 *
 * <p>Ordering state is PER FAMILY AND PER AGGREGATE (ARC-014 §5: aggregate
 * version is the ordering key): the applied version for an aggregate lives
 * on its OWN family's projection row (ARC-022 §9 — every projection records
 * its source aggregate version, and an older source version cannot replace
 * a newer one). The single projection_checkpoint row remains the
 * stream-level high-water mark for rebuild/resume bookkeeping across all
 * four families; it advances monotonically (never backwards) and records
 * the most recently observed version gap for operator visibility.</p>
 *
 * <p>Per-message pipeline, one DB transaction for all effects (ARC-022 §8.2:
 * completion and business effects commit atomically):</p>
 * <ol>
 *   <li>Envelope parse + required attributes; poison → reject to DLQ.</li>
 *   <li>Type filter: unknown types are dead-lettered as misrouted
 *       (disclosed choice: silently dropping would hide routing defects;
 *       the queue bindings are type-specific today, so anything else arriving
 *       here is an operator-visible anomaly).</li>
 *   <li>Per-type payload validation BEFORE any DB work (fail-closed):
 *       required payload fields must be present and well-typed — otherwise
 *       the fact is poison-classified and dead-lettered
 *       [category=PAYLOAD_INVALID].</li>
 *   <li>Aggregate/payload coherence PER FAMILY: envelope aggregateid must
 *       equal the payload's family aggregate ref (stationRef / evseRef /
 *       connectorRef / tariffVersionRef, both parsed as UUID); a mismatch is
 *       poison-classified and dead-lettered
 *       [category=AGGREGATE_PAYLOAD_MISMATCH] — the fact cannot be
 *       attributed to a trustworthy aggregate stream.</li>
 *   <li>Inbox insert ON CONFLICT DO NOTHING — an existing COMPLETED/SKIPPED
 *       row means duplicate → ack and stop (first delivery already finished;
 *       nothing else to do). An existing FAILED row is RETRYABLE, not a
 *       duplicate: it marks a previously rolled-back orphan attempt, so the
 *       fact is processed again (see the orphan step).</li>
 *   <li>Checkpoint version discipline (PER AGGREGATE, gated on the
 *       aggregate's OWN family table): v == the family row's applied
 *       version + 1 (or the aggregate's first fact) → apply upsert (§7.1
 *       older-cannot-replace guard is enforced in SQL), advance the stream
 *       checkpoint monotonically, audit, COMPLETED;</li>
 *   <li>Gap strategy (chosen, disclosed): v &gt; the family row's applied
 *       version + 1 → record the gap in the checkpoint (gap_from_version,
 *       gap_recorded_at) and APPLY THE FACT IMMEDIATELY in the same
 *       transaction. Rationale per family: station facts are FULL station
 *       snapshots; EVSE/connector facts carry the full public configuration
 *       of their aggregate; the tariff fact carries the full public version
 *       payload — so applying a later snapshot late is safe, any
 *       intermediate version that later arrives is rejected by the SQL guard
 *       (source_version &gt; existing only), and the aggregate's applied
 *       version still advances to the highest applied version. The gap stays
 *       recorded (gap_from_version/gap_recorded_at are intentionally NOT
 *       cleared after a gap-apply) for operator visibility until the next
 *       in-sequence fact overwrites the checkpoint row (which clears gap
 *       fields). The inbox row records attempt accounting and the gap
 *       evidence: a gap-apply fact completes with
 *       processing_outcome='COMPLETED' while KEEPING
 *       failure_category='VERSION_GAP' on the completed row (the checkpoint
 *       gap fields alone are transient — they are cleared by the next
 *       in-sequence fact — so the completed inbox row is the durable gap
 *       trace). Requeue-based gap redelivery loops were
 *       deliberately not implemented — they add redelivery-loop risk
 *       without correctness benefit given full payloads.</li>
 *   <li>Payload-shape RuntimeExceptions (Jackson coercion failures on
 *       malformed numerics, missing required fields) are PERMANENT → reject
 *       to DLQ [category=PAYLOAD_INVALID].</li>
 *   <li>Orphan handling (EVSE family only, disclosed): an EVSE fact whose
 *       stationRef has no station_search_projection row is an orphan fact
 *       (its parent snapshot has not arrived/applied yet). The business
 *       transaction ROLLS BACK (nothing is applied), the inbox row is
 *       marked FAILED [category=ORPHAN_FACT] in its own transaction, and
 *       the message is basicNack'ed WITH requeue=true so the broker
 *       redelivers it after the parent fact arrives; on redelivery the
 *       FAILED inbox row makes the fact retryable (not a duplicate).
 *       attempt_count bounds the loop: once the attempt count reaches
 *       {@link #ORPHAN_MAX_ATTEMPTS} the fact is rejected with
 *       requeue=false → DLQ, so a permanently-orphaned fact cannot loop
 *       forever.</li>
 *   <li>Transient DataAccessException → rollback and RETHROW THE ORIGINAL
 *       EXCEPTION (not wrapped in AmqpRejectAndDontRequeueException, which
 *       would bypass the retry interceptor and dead-letter immediately);
 *       the yml-configured container retry (3 attempts, 500ms initial)
 *       redelivers; the default RejectAndDontRequeueRecoverer then rejects
 *       to the DLQ. Corrected by I1-ENG-003 after the Boot 4.1.1 findings.</li>
 * </ol>
 *
 * <p>Manual acks (ackMode MANUAL): ack on every terminal outcome
 * (completed/skipped/duplicate), reject-without-requeue for poison/unknown
 * type, requeue for orphan facts (bounded by attempt_count), rethrow for
 * transient DB failures so the container retry/dead-letter path handles
 * redelivery. Cancellation-safe: the container stops delivery on shutdown;
 * unacked messages are requeued by the broker.</p>
 */
@Component
public class StaDomainConsumer {

    private static final Logger log = LoggerFactory.getLogger(StaDomainConsumer.class);

    static final String CONSUMER_NAME = "discovery-station-projection";
    static final String PROJECTION_NAME = "station_search_projection";
    static final String SOURCE_REF = "station-operations-service";

    static final String TYPE_STATION_PUBLISHED =
            "com.evplatform.station.published.v1";
    static final String TYPE_EVSE_CONFIGURATION_CHANGED =
            "com.evplatform.station.evse-configuration-changed.v1";
    static final String TYPE_CONNECTOR_CONFIGURATION_CHANGED =
            "com.evplatform.station.connector-configuration-changed.v1";
    static final String TYPE_TARIFF_PUBLISHED =
            "com.evplatform.station.tariff-published.v1";

    private static final Set<String> EXPECTED_TYPES = Set.of(
            TYPE_STATION_PUBLISHED,
            TYPE_EVSE_CONFIGURATION_CHANGED,
            TYPE_CONNECTOR_CONFIGURATION_CHANGED,
            TYPE_TARIFF_PUBLISHED);

    /** Returned by the per-family applied-version readers when the aggregate
     *  has no projection row yet (its first fact). Also the initial checkpoint
     *  value. */
    private static final long NO_ROW_SENTINEL = -1L;

    /** Orphan redelivery budget: attempt_count reaching this → requeue=false
     *  → DLQ (the count is incremented once per failed attempt). */
    private static final int ORPHAN_MAX_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final JdbcClient jdbc;

    public StaDomainConsumer(
            @Autowired(required = false) ObjectMapper injectedObjectMapper,
            PlatformTransactionManager transactionManager,
            JdbcClient jdbc) {
        this.objectMapper = injectedObjectMapper != null
                ? injectedObjectMapper : new ObjectMapper();
        this.tx = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
    }

    @RabbitListener(
            queues = RabbitTopologyConfiguration.STATION_DOMAIN_QUEUE,
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
        if (!EXPECTED_TYPES.contains(type)) {
            log.warn("Discovery consumer: unexpected message type '{}' on the "
                            + "station-domain queue rejected to DLQ [category=UNEXPECTED_TYPE]",
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

        // Per-type payload validation BEFORE any DB work (fail-closed,
        // security review finding 1): Jackson 2.x decimalValue() SILENTLY
        // coerces missing or wrong-typed numeric fields to zero, which would
        // project e.g. a connector at 0 W. Validate the types the projection
        // SQL depends on BEFORE the transaction; anything malformed is
        // poison-classified. The per-family aggregateid coherence check is
        // folded into the same pre-DB validation pass.
        JsonNode data = envelope.get("data");
        UUID familyAggregateId;
        try {
            familyAggregateId = validatePayload(type, data);
        } catch (PayloadValidationException invalid) {
            log.warn("Discovery consumer: malformed {} payload for message {} "
                            + "[category={}] ({})", type, messageId, invalid.category,
                    invalid.getMessage());
            rejectToDlq(channel, deliveryTag);
            return;
        }

        // Aggregate/payload coherence PER FAMILY: the aggregateid extension
        // attribute must reference the same aggregate as the payload's family
        // ref. A mismatch means the routing key and the fact body disagree —
        // the fact cannot be attributed to a trustworthy aggregate stream, so
        // it is poison-classified (basicNack requeue=false → DLQ) instead of
        // projecting an unattributable snapshot.
        UUID aggregateId;
        try {
            aggregateId = UUID.fromString(aggregateRef);
        } catch (IllegalArgumentException nonUuidAggregateId) {
            log.warn("Discovery consumer: envelope aggregateid is not a UUID rejected to DLQ "
                    + "[category=AGGREGATE_PAYLOAD_MISMATCH]");
            rejectToDlq(channel, deliveryTag);
            return;
        }
        if (!aggregateId.equals(familyAggregateId)) {
            log.warn("Discovery consumer: envelope aggregateid {} does not match payload "
                            + "aggregate ref {} rejected to DLQ "
                            + "[category=AGGREGATE_PAYLOAD_MISMATCH]",
                    aggregateId, familyAggregateId);
            rejectToDlq(channel, deliveryTag);
            return;
        }

        try {
            process(type, messageId, aggregateRef, aggregateVersion, correlationId, envelope);
            channel.basicAck(deliveryTag, false);
        } catch (OrphanFactException orphan) {
            // EVSE family only: the parent station snapshot has not been
            // applied yet. Mark the inbox row FAILED [ORPHAN_FACT] in its own
            // transaction, then requeue (bounded by attempt_count) so the
            // fact is retried after the parent arrives; once the attempt
            // budget is reached, dead-letter instead of looping forever.
            markInboxFailed(messageId, type, aggregateVersion, "ORPHAN_FACT");
            if (orphan.attemptCount >= ORPHAN_MAX_ATTEMPTS) {
                log.warn("Discovery consumer: orphan EVSE fact {} reached the attempt "
                                + "budget ({}) — rejected to DLQ [category=ORPHAN_FACT]",
                        messageId, orphan.attemptCount);
                rejectToDlq(channel, deliveryTag);
            } else {
                log.warn("Discovery consumer: orphan EVSE fact {} for station {} — "
                                + "requeued for redelivery [category=ORPHAN_FACT, attempt {}]",
                        messageId, orphan.stationRef, orphan.attemptCount);
                channel.basicNack(deliveryTag, false, true);
            }
        } catch (DataAccessException transientOrDbFailure) {
            // rollback already done by TransactionTemplate; let the container
            // retry (bounded) and finally dead-letter handle redelivery
            log.warn("Discovery consumer: transient DB failure for message {} "
                    + "[category=TRANSIENT_DB_FAILURE]", messageId);
            // Rethrow the ORIGINAL exception (do NOT wrap in
            // AmqpRejectAndDontRequeueException - that signals the container
            // to reject immediately, bypassing the retry interceptor). With
            // the yml-configured stateless retry + RejectAndDontRequeueRecoverer
            // default, the container retries bounded times, then the
            // recoverer rejects to the DLQ. Honest per Boot 4.1.1 findings.
            throw transientOrDbFailure;
        } catch (RuntimeException payloadShapeFailure) {
            // Payload-shape defects (Jackson coercion failures on malformed
            // numerics, missing required fields, illegal casts) are PERMANENT:
            // redelivery cannot repair them. Fail closed to the DLQ with a
            // permanent failure category instead of parking the message
            // unacked or misclassifying it as transient. Ordered AFTER the
            // DataAccessException catch: transient DB failures keep their
            // specific handling.
            log.warn("Discovery consumer: malformed payload for message {} "
                    + "[category=PAYLOAD_INVALID]", messageId, payloadShapeFailure);
            rejectToDlq(channel, deliveryTag);
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

    /**
     * Per-type payload validation + family aggregate extraction, BEFORE any
     * DB work (fail-closed). Throws {@link PayloadValidationException}
     * [category=PAYLOAD_INVALID] on any malformed field and
     * [category=AGGREGATE_PAYLOAD_MISMATCH] on a non-UUID family ref (the
     * fact cannot be attributed to an aggregate stream).
     *
     * @return the payload's family aggregate ref (stationRef / evseRef /
     *         connectorRef / tariffVersionRef)
     */
    private UUID validatePayload(String type, JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new PayloadValidationException("data is not an object",
                    "PAYLOAD_INVALID");
        }
        return switch (type) {
            case TYPE_STATION_PUBLISHED -> validateStationPublished(data);
            case TYPE_EVSE_CONFIGURATION_CHANGED -> validateEvseConfiguration(data);
            case TYPE_CONNECTOR_CONFIGURATION_CHANGED -> validateConnectorConfiguration(data);
            case TYPE_TARIFF_PUBLISHED -> validateTariffPublished(data);
            default -> throw new PayloadValidationException(
                    "unvalidated type " + type, "PAYLOAD_INVALID");
        };
    }

    /** station.published: UUID stationRef + numeric lat/lon (+ displayName). */
    private UUID validateStationPublished(JsonNode data) {
        if (!data.hasNonNull("stationRef") || !data.hasNonNull("publicRef")
                || !data.hasNonNull("displayName")
                // MINOR-4: hasNonNull before isNumber() — a MISSING numeric
                // field must hit this structured PAYLOAD_INVALID path with a
                // specific reason instead of an incidental NPE in the
                // downstream coercion (data.get(...) → null → isNumber() NPE
                // → catch-all, terminal behavior identical: DLQ).
                || !data.hasNonNull("latitude") || !data.get("latitude").isNumber()
                || !data.hasNonNull("longitude") || !data.get("longitude").isNumber()) {
            throw new PayloadValidationException(
                    "station.published payload missing required fields or non-numeric lat/lon",
                    "PAYLOAD_INVALID");
        }
        return uuidFamilyRef(data, "stationRef");
    }

    /**
     * evse-configuration-changed: UUID evseRef + UUID stationRef +
     * non-blank evseUid.
     */
    private UUID validateEvseConfiguration(JsonNode data) {
        if (!data.hasNonNull("evseRef") || !data.hasNonNull("stationRef")
                || !data.hasNonNull("evseUid")
                || data.get("evseUid").asText("").isBlank()) {
            throw new PayloadValidationException(
                    "evse-configuration-changed payload missing required fields "
                            + "or blank evseUid", "PAYLOAD_INVALID");
        }
        UUID evseRef = uuidFamilyRef(data, "evseRef");
        uuidFamilyRef(data, "stationRef"); // must also be a well-formed UUID
        return evseRef;
    }

    /**
     * connector-configuration-changed: UUID connectorRef + UUID evseRef +
     * non-blank connectorType + numeric maxPowerW &gt; 0 within int range.
     * MINOR-3: maxPowerW is stored as integer — a value beyond the int range
     * would silently wrap around through asInt(), so it is rejected
     * (PAYLOAD_INVALID) instead of being persisted corrupted.
     */
    private UUID validateConnectorConfiguration(JsonNode data) {
        if (!data.hasNonNull("connectorRef") || !data.hasNonNull("evseRef")
                || !data.hasNonNull("connectorType")
                || data.get("connectorType").asText("").isBlank()
                // MINOR-4: hasNonNull guard — a MISSING maxPowerW must hit
                // the structured PAYLOAD_INVALID path, not an incidental NPE.
                || !data.hasNonNull("maxPowerW")
                || !data.get("maxPowerW").isNumber()
                || data.get("maxPowerW").asLong() <= 0
                || data.get("maxPowerW").asLong() > Integer.MAX_VALUE) {
            throw new PayloadValidationException(
                    "connector-configuration-changed payload missing required fields, "
                            + "blank connectorType, or non-positive/out-of-int-range maxPowerW",
                    "PAYLOAD_INVALID");
        }
        UUID connectorRef = uuidFamilyRef(data, "connectorRef");
        uuidFamilyRef(data, "evseRef"); // must also be a well-formed UUID
        return connectorRef;
    }

    /**
     * tariff-published: UUID tariffRef + UUID tariffVersionRef + numeric
     * versionNumber within int range + non-blank currency + non-empty
     * components array, each with non-blank componentKind/unit and numeric
     * amountMinor. MINOR-3: versionNumber is stored as integer — a value
     * beyond the int range would silently wrap around through asInt(), so
     * it is rejected (PAYLOAD_INVALID) instead of being persisted corrupted.
     * (The executable schema declares versionNumber as a bare integer with
     * no minimum, so only the int-range bound is enforced here.)
     */
    private UUID validateTariffPublished(JsonNode data) {
        if (!data.hasNonNull("tariffRef") || !data.hasNonNull("tariffVersionRef")
                // MINOR-4: hasNonNull guard — a MISSING versionNumber must hit
                // the structured PAYLOAD_INVALID path, not an incidental NPE.
                || !data.hasNonNull("versionNumber")
                || !data.get("versionNumber").isNumber()
                || data.get("versionNumber").asLong() < Integer.MIN_VALUE
                || data.get("versionNumber").asLong() > Integer.MAX_VALUE
                || !data.hasNonNull("currency")
                || data.get("currency").asText("").isBlank()
                || !data.hasNonNull("components")
                || !data.get("components").isArray()
                || data.get("components").isEmpty()) {
            throw new PayloadValidationException(
                    "tariff-published payload missing required fields, blank currency, "
                            + "out-of-int-range versionNumber, "
                            + "or empty/missing components array", "PAYLOAD_INVALID");
        }
        for (JsonNode component : data.get("components")) {
            if (!component.isObject()
                    || !component.hasNonNull("componentKind")
                    || component.get("componentKind").asText("").isBlank()
                    || !component.hasNonNull("unit")
                    || component.get("unit").asText("").isBlank()
                    // MINOR-4: hasNonNull guard per component — a MISSING
                    // amountMinor must hit the structured PAYLOAD_INVALID
                    // path, not an incidental NPE.
                    || !component.hasNonNull("amountMinor")
                    || !component.get("amountMinor").isNumber()) {
                throw new PayloadValidationException(
                        "tariff component missing componentKind/unit or "
                                + "non-numeric amountMinor", "PAYLOAD_INVALID");
            }
        }
        UUID tariffVersionRef = uuidFamilyRef(data, "tariffVersionRef");
        uuidFamilyRef(data, "tariffRef"); // must also be a well-formed UUID
        return tariffVersionRef;
    }

    private UUID uuidFamilyRef(JsonNode data, String field) {
        String value = textOrNull(data, field);
        if (value == null) {
            throw new PayloadValidationException(
                    field + " missing", "PAYLOAD_INVALID");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException nonUuid) {
            throw new PayloadValidationException(
                    field + " is not a UUID", "AGGREGATE_PAYLOAD_MISMATCH");
        }
    }

    private static final class PayloadValidationException extends RuntimeException {
        final String category;

        PayloadValidationException(String message, String category) {
            super(message);
            this.category = category;
        }
    }

    /**
     * Carries the orphan evidence out of the rolled-back transaction.
     * {@code attemptCount} is the attempt number of the delivery that hit
     * the orphan (persisted attempt_count + 1); the caller compares it
     * against {@link #ORPHAN_MAX_ATTEMPTS}.
     */
    private static final class OrphanFactException extends RuntimeException {
        final UUID stationRef;
        final int attemptCount;

        OrphanFactException(UUID stationRef, int attemptCount) {
            super("orphan EVSE fact: station " + stationRef + " has no projection row");
            this.stationRef = stationRef;
            this.attemptCount = attemptCount;
        }
    }

    private void process(String type, UUID messageId, String aggregateRef,
                         long aggregateVersion, String correlationId, JsonNode envelope) {
        tx.executeWithoutResult(status -> {
            String existingOutcome = insertInboxRow(messageId, type, aggregateVersion);
            if (existingOutcome != null && !"FAILED".equals(existingOutcome)) {
                log.debug("Discovery consumer: duplicate delivery of message {} skipped",
                        messageId);
                return; // duplicate: first delivery already completed/skipped
            }
            // existingOutcome == null → fresh PROCESSING row;
            // existingOutcome == "FAILED" → retryable orphan attempt: the
            // previous attempt rolled back, so this delivery reprocesses.

            Checkpoint checkpoint = readOrInsertCheckpoint();

            // Version discipline is PER AGGREGATE within the fact's FAMILY
            // (ARC-014 §5): the aggregate's own projection row in ITS family
            // table holds its applied source version (ARC-022 §9 §7.1 guard).
            // The checkpoint is the stream high-water mark for rebuild/resume
            // bookkeeping only — a NEW aggregate's first fact (e.g. v0 after
            // another aggregate reached v5) must never be mistaken for an
            // out-of-order fact of an already-known aggregate.
            long appliedVersion = readFamilyAppliedVersion(type, aggregateRef);
            if (aggregateVersion <= appliedVersion) {
                markInboxSkipped(messageId, "OLDER_VERSION");
                log.debug("Discovery consumer: message {} for aggregate {} version {} "
                                + "not newer than the applied version {} — skipped",
                        messageId, aggregateRef, aggregateVersion, appliedVersion);
                return;
            }

            boolean inSequence = appliedVersion == NO_ROW_SENTINEL
                    || aggregateVersion == appliedVersion + 1;

            // EVSE orphan check first (disclosed): the parent station
            // snapshot must already be projected. Missing → the whole
            // transaction rolls back (inbox row included) and the fact is
            // requeued outside the transaction (bounded by attempt_count).
            if (TYPE_EVSE_CONFIGURATION_CHANGED.equals(type)) {
                UUID stationRef = UUID.fromString(
                        textOrNull(envelope.get("data"), "stationRef"));
                Integer stationRows = jdbc.sql("""
                                SELECT count(*) FROM discovery_insights.station_search_projection
                                WHERE station_ref = ?::uuid
                                """)
                        .param(stationRef.toString())
                        .query((rs, i) -> rs.getInt(1))
                        .single();
                if (stationRows == 0) {
                    throw new OrphanFactException(stationRef,
                            readInboxAttemptCount(messageId) + 1);
                }
            }

            applyProjection(type, envelope, aggregateRef, aggregateVersion);

            if (inSequence) {
                // Stream high-water mark: a fact that is in sequence for ITS
                // aggregate may still be older than the stream checkpoint (a
                // new aggregate's first fact after another advanced far
                // ahead) — never rewind, never clear recorded gaps.
                if (aggregateVersion > checkpoint.lastAppliedVersion()) {
                    advanceCheckpoint(messageId, aggregateVersion, null, null);
                }
            } else {
                long gapFrom = appliedVersion + 1;
                // Record the gap but never rewind the stream high-water mark
                // below the highest applied version.
                advanceCheckpoint(messageId,
                        Math.max(aggregateVersion, checkpoint.lastAppliedVersion()),
                        gapFrom, Instant.now());
                markInboxGapRecorded(messageId, gapFrom);
                log.warn("Discovery consumer: version gap {}..{} recorded and fact applied "
                        + "from full payload (message {})", gapFrom, aggregateVersion - 1,
                        messageId);
            }

            insertAuditRow(type, messageId, correlationId, envelope);
            if (inSequence) {
                markInboxCompleted(messageId);
            } else {
                // keep the gap evidence: the completed row retains the
                // VERSION_GAP failure category (markInboxGapRecorded set it
                // above; do not NULL it on completion)
                markInboxCompletedGap(messageId);
            }
        });
    }

    /**
     * @return {@code null} when a fresh PROCESSING row was inserted for
     *         (consumer, message_id); otherwise the EXISTING row's
     *         processing_outcome. A FAILED existing row is retryable (orphan
     *         redelivery); any other existing outcome means duplicate.
     */
    private String insertInboxRow(UUID messageId, String type, long aggregateVersion) {
        boolean inserted = jdbc.sql("""
                        INSERT INTO discovery_insights.inbox_message
                            (consumer_name, message_id, message_type, aggregate_type,
                             aggregate_version, processing_outcome)
                        VALUES (?, ?, ?, 'Station', ?, 'PROCESSING')
                        ON CONFLICT (consumer_name, message_id) DO NOTHING
                        RETURNING message_id
                        """)
                .param(CONSUMER_NAME)
                .param(messageId)
                .param(type)
                .param(aggregateVersion)
                .query((rs, i) -> rs.getString(1))
                .optional()
                .isPresent();
        if (inserted) {
            return null;
        }
        return jdbc.sql("""
                        SELECT processing_outcome FROM discovery_insights.inbox_message
                        WHERE consumer_name = ? AND message_id = ?
                        """)
                .param(CONSUMER_NAME)
                .param(messageId)
                .query((rs, i) -> rs.getString(1))
                .single();
    }

    /** Persisted attempt_count of the inbox row (0 for a fresh row). */
    private int readInboxAttemptCount(UUID messageId) {
        return jdbc.sql("""
                        SELECT attempt_count FROM discovery_insights.inbox_message
                        WHERE consumer_name = ? AND message_id = ?
                        """)
                .param(CONSUMER_NAME)
                .param(messageId)
                .query((rs, i) -> rs.getInt(1))
                .single();
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
     * The aggregate's own applied source version, read from ITS FAMILY's
     * projection row (ARC-022 §9: every projection records its source
     * aggregate version). Returns {@link #NO_ROW_SENTINEL} when the aggregate
     * has no row yet — its first fact. This is the per-family/per-aggregate
     * ordering gate; the global checkpoint row is stream bookkeeping only.
     */
    private long readFamilyAppliedVersion(String type, String aggregateRef) {
        String sql = switch (type) {
            case TYPE_STATION_PUBLISHED -> """
                    SELECT COALESCE(source_version, -1)
                    FROM discovery_insights.station_search_projection
                    WHERE station_ref = ?::uuid
                    """;
            case TYPE_EVSE_CONFIGURATION_CHANGED -> """
                    SELECT COALESCE(source_version, -1)
                    FROM discovery_insights.evse_search_projection
                    WHERE evse_ref = ?::uuid
                    """;
            case TYPE_CONNECTOR_CONFIGURATION_CHANGED -> """
                    SELECT COALESCE(source_version, -1)
                    FROM discovery_insights.connector_search_projection
                    WHERE connector_ref = ?::uuid
                    """;
            case TYPE_TARIFF_PUBLISHED -> """
                    SELECT COALESCE(source_version, -1)
                    FROM discovery_insights.tariff_public_projection
                    WHERE tariff_version_ref = ?::uuid
                    """;
            default -> throw new IllegalStateException("unhandled type " + type);
        };
        return jdbc.sql(sql)
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
     * Per-family dispatch: each family upserts its own table only (no
     * cross-family writes).
     */
    private void applyProjection(String type, JsonNode envelope, String sourceRef,
                                 long sourceVersion) {
        JsonNode data = envelope.get("data");
        switch (type) {
            case TYPE_STATION_PUBLISHED ->
                    applyStationPublished(data, sourceRef, sourceVersion);
            case TYPE_EVSE_CONFIGURATION_CHANGED ->
                    applyEvseConfiguration(data, sourceRef, sourceVersion);
            case TYPE_CONNECTOR_CONFIGURATION_CHANGED ->
                    applyConnectorConfiguration(data, sourceRef, sourceVersion);
            case TYPE_TARIFF_PUBLISHED ->
                    applyTariffPublished(data, sourceRef, sourceVersion);
            default -> throw new IllegalStateException("unhandled type " + type);
        }
    }

    private void applyStationPublished(JsonNode data, String sourceRef, long sourceVersion) {
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

    private void applyEvseConfiguration(JsonNode data, String sourceRef, long sourceVersion) {
        jdbc.sql("""
                        INSERT INTO discovery_insights.evse_search_projection
                            (evse_ref, station_ref, evse_uid, source_version, source_ref,
                             projection_state)
                        VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                        ON CONFLICT (evse_ref) DO UPDATE SET
                            station_ref    = EXCLUDED.station_ref,
                            evse_uid       = EXCLUDED.evse_uid,
                            source_version = EXCLUDED.source_version,
                            source_ref     = EXCLUDED.source_ref,
                            updated_at     = now()
                        WHERE EXCLUDED.source_version > evse_search_projection.source_version
                        """)
                .param(uuidOrNull(data, "evseRef"))
                .param(uuidOrNull(data, "stationRef"))
                .param(textOrNull(data, "evseUid"))
                .param(sourceVersion)
                .param(sourceRef)
                .update();
    }

    private void applyConnectorConfiguration(JsonNode data, String sourceRef,
                                             long sourceVersion) {
        jdbc.sql("""
                        INSERT INTO discovery_insights.connector_search_projection
                            (connector_ref, evse_ref, connector_type, max_power_w,
                             source_version, source_ref, projection_state)
                        VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                        ON CONFLICT (connector_ref) DO UPDATE SET
                            evse_ref        = EXCLUDED.evse_ref,
                            connector_type  = EXCLUDED.connector_type,
                            max_power_w     = EXCLUDED.max_power_w,
                            source_version  = EXCLUDED.source_version,
                            source_ref      = EXCLUDED.source_ref,
                            updated_at      = now()
                        WHERE EXCLUDED.source_version > connector_search_projection.source_version
                        """)
                .param(uuidOrNull(data, "connectorRef"))
                .param(uuidOrNull(data, "evseRef"))
                .param(textOrNull(data, "connectorType"))
                .param(data.get("maxPowerW").asInt())
                .param(sourceVersion)
                .param(sourceRef)
                .update();
    }

    private void applyTariffPublished(JsonNode data, String sourceRef, long sourceVersion) {
        jdbc.sql("""
                        INSERT INTO discovery_insights.tariff_public_projection
                            (tariff_version_ref, tariff_ref, version_number, currency,
                             components, source_version, source_ref, projection_state)
                        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, 'ACTIVE')
                        ON CONFLICT (tariff_version_ref) DO UPDATE SET
                            tariff_ref      = EXCLUDED.tariff_ref,
                            version_number  = EXCLUDED.version_number,
                            currency        = EXCLUDED.currency,
                            components      = EXCLUDED.components,
                            source_version  = EXCLUDED.source_version,
                            source_ref      = EXCLUDED.source_ref,
                            updated_at      = now()
                        WHERE EXCLUDED.source_version > tariff_public_projection.source_version
                        """)
                .param(uuidOrNull(data, "tariffVersionRef"))
                .param(uuidOrNull(data, "tariffRef"))
                .param(data.get("versionNumber").asInt())
                .param(textOrNull(data, "currency"))
                .param(data.get("components").toString())
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

    /**
     * Gap accounting on the inbox row: sets the VERSION_GAP category and
     * increments attempt_count — the SINGLE increment for a gap-apply
     * delivery (the completion step does not increment again, otherwise one
     * delivery would be accounted twice). Keeping the increment HERE means
     * the row's attempt_count is already meaningful at failure time, before
     * the outcome flips to COMPLETED. The VERSION_GAP category set here is
     * preserved by {@link #markInboxCompletedGap} when the gap fact
     * completes, so the completed inbox row keeps the gap evidence for
     * operators.
     */
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

    /** Completion for an in-sequence fact: no failure category to preserve. */
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

    /**
     * Completion for a gap-apply fact: COMPLETED, with the VERSION_GAP
     * failure_category PRESERVED on the completed row as durable gap
     * evidence (clearing it would erase the operator-visible trace of the
     * gap; the checkpoint's gap_from_version/gap_recorded_at fields alone
     * are overwritten by the next in-sequence fact). attempt_count is NOT
     * incremented here: {@link #markInboxGapRecorded} already counted this
     * delivery exactly once (a second increment would double-count one
     * delivery).
     */
    private void markInboxCompletedGap(UUID messageId) {
        jdbc.sql("""
                        UPDATE discovery_insights.inbox_message
                        SET processing_outcome = 'COMPLETED', completed_at = now(),
                            failure_category = 'VERSION_GAP'
                        WHERE consumer_name = ? AND message_id = ?
                        """)
                .param(CONSUMER_NAME)
                .param(messageId)
                .update();
    }

    /**
     * Orphan accounting: marks the inbox row FAILED [ORPHAN_FACT] in its OWN
     * transaction (the business transaction rolled back — the fact was not
     * applied), recording the message type and version for operators. The
     * attempt_count increment here bounds the requeue loop in
     * {@link #handle}: once the count reaches {@link #ORPHAN_MAX_ATTEMPTS}
     * the fact is dead-lettered instead of requeued. A FAILED row makes the
     * NEXT delivery retryable rather than a duplicate (see
     * {@link #insertInboxRow}).
     */
    private void markInboxFailed(UUID messageId, String type, long aggregateVersion,
                                 String category) {
        tx.executeWithoutResult(status ->
                jdbc.sql("""
                                INSERT INTO discovery_insights.inbox_message
                                    (consumer_name, message_id, message_type, aggregate_type,
                                     aggregate_version, processing_outcome, attempt_count,
                                     failure_category)
                                VALUES (?, ?, ?, 'Station', ?, 'FAILED', 1, ?)
                                ON CONFLICT (consumer_name, message_id) DO UPDATE SET
                                    processing_outcome = 'FAILED',
                                    attempt_count = inbox_message.attempt_count + 1,
                                    failure_category = EXCLUDED.failure_category
                                """)
                        .param(CONSUMER_NAME)
                        .param(messageId)
                        .param(type)
                        .param(aggregateVersion)
                        .param(category)
                        .update());
    }

    private void insertAuditRow(String type, UUID messageId, String correlationId,
                                JsonNode envelope) {
        String action = switch (type) {
            case TYPE_STATION_PUBLISHED -> "APPLY_STATION_PUBLISHED";
            case TYPE_EVSE_CONFIGURATION_CHANGED -> "APPLY_EVSE_CONFIGURATION";
            case TYPE_CONNECTOR_CONFIGURATION_CHANGED -> "APPLY_CONNECTOR_CONFIGURATION";
            case TYPE_TARIFF_PUBLISHED -> "APPLY_TARIFF_PUBLISHED";
            default -> throw new IllegalStateException("unhandled type " + type);
        };
        String target = switch (type) {
            case TYPE_STATION_PUBLISHED ->
                    "station/" + textOrNull(envelope.get("data"), "publicRef");
            case TYPE_EVSE_CONFIGURATION_CHANGED ->
                    "evse/" + textOrNull(envelope.get("data"), "evseUid");
            case TYPE_CONNECTOR_CONFIGURATION_CHANGED ->
                    "connector/" + textOrNull(envelope.get("data"), "connectorRef");
            case TYPE_TARIFF_PUBLISHED ->
                    "tariff/" + textOrNull(envelope.get("data"), "tariffVersionRef");
            default -> throw new IllegalStateException("unhandled type " + type);
        };
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
                                ?, ?, 'SUCCESS', ?, 'BUSINESS')
                        """)
                .param(UUID.randomUUID())
                .param(action)
                .param(target)
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
