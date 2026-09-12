# DEV-I1-DSC-001-01 — Version-gap handling strategy deviation

- **Task ID:** I1-DSC-001 (L2; branch task/i1-dsc-001-discovery)
- **Deviation from:** packet AC-02 + assumptions ("hold-and-retry with
  bounded attempts then skip-with-recorded-gap, honestly disclosed")
- **Implemented strategy:** apply-immediately-from-full-snapshot with the
  gap recorded in `projection_checkpoint` (`gap_from_version`,
  `gap_recorded_at`); checkpoint remains a monotonic stream high-water mark
  (GREATEST, never rewinds); per-aggregate ordering is enforced by the
  station row's own `source_version` plus the §7.1 SQL guard
  (`WHERE EXCLUDED.source_version > station_search_projection.source_version`).
- **Owner approval:** interactive session decision, 2026-09-12 ("Approve
  deviation"), recorded after independent data review F-1 flagged the
  missing authority record.
- **Rationale (accepted by owner):** StationPublished payloads are complete
  snapshots of the station's public reference data, so applying a
  higher-version fact immediately is always safe and never leaves stale
  public data; hold-and-retry would delay correct data for no correctness
  gain. ARC-014 §5's invariant ("critical business correctness never
  depends solely on event arrival order") is satisfied by construction.
- **Consequences:** the packet's `gap-retry-attempts` configuration is
  removed as dead (with `max-delivery-retries`); gap evidence remains
  visible in checkpoint fields and (after the fix round) in the inbox row's
  preserved `VERSION_GAP` failure category.
- **Scope:** this deviation is specific to the StationPublished consumer's
  gap handling in W1-S1. Future event families with partial/delta payloads
  must not inherit it without re-analysis.
