# DEV-I1-DSC-002-01 — Tariff projection naming and opening-hours deferral

- **Task ID:** I1-DSC-002 (L2; branch task/i1-dsc-002-depth)
- **Owner approval:** interactive session decisions, 2026-09-12.

## 1. Tariff projection naming

- **Deviation from:** ARC-022 §9's Discovery table list, which names
  `station_search_projection`, `evse_search_projection`,
  `connector_search_projection`, `advisory_availability_projection`,
  `projection_checkpoint` — and no tariff table.
- **Decision:** Discovery's V3 migration creates
  **`tariff_public_projection`** — distinct from Booking-owned
  `tariff_version_projection` (ARC-022 §7.1, which holds booking snapshots,
  not Discovery's public pricing copy). The task packet itself mandates "a
  tariff projection"; §9's list is read as non-exhaustive for
  projection-shaped read models.
- **Rationale:** the packet's authority (owner-approved) requires the data
  home; inventing a Booking-style name would risk conflation with the
  §7.1 table; a distinct public-projection name is honest about ownership
  and purpose.
- **Follow-up:** a future governance-hygiene task may add
  `tariff_public_projection` to ARC-022 §9's list so document and schema
  agree (docs/** is prohibited in this task).

## 2. Opening-hours deferral

- **Deviation from:** FR-DIS-02's W1-S1 MUST list, which includes
  opening-hours display.
- **Decision:** the details response **omits** `openingHours` this slice.
- **Rationale (verified):** no allowed path carries hours to Discovery —
  the wire payloads (station-published/updated schemas) contain no hours
  field; contracts/schemas/** and the message registry are prohibited
  files in this task; the hours rows live only in station_operations_db
  (cross-service reads are an invariant violation).
- **Fix path:** a future contract task adds an hours field to a station
  event family (schema + registry + producer emission + consumer), after
  which the details response gains `openingHours`.
- **Honesty:** the API contract extension in this task does NOT declare an
  openingHours field, so the response shape remains contract-true — the
  deferral is tracked in traceability rather than silently dropped.
