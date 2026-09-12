-- I1-MSG-002 (m1+m5): explicit stable constraint names per ARC-022 §10; additive only; V3 immutable per ARC-022 §12.
ALTER TABLE station_operations.outbox_message RENAME CONSTRAINT "outbox_message_aggregate_type_aggregate_ref_aggregate_versi_key" TO uq_outbox_event_fact;
ALTER TABLE station_operations.idempotency_record RENAME CONSTRAINT "idempotency_record_principal_identity_operation_target_reso_key" TO uq_idempotency_scope;
CREATE INDEX ix_idempotency_expiry ON station_operations.idempotency_record (expires_at);
