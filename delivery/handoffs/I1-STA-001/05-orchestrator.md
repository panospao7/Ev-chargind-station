---
role: orchestrator
taskId: I1-STA-001
previousState: HUMAN_REVIEW
resultingState: VERIFIED
baselineCommit: b73faffacedd3dd2e80cd3422aa3e9463a81bc9c
candidateCommit: 1c05c59e5e394e12e9d8d1c943aaab91a88b47f1
mergeCommit: 072fd31027896421f97fef64213a730d7feeef57
impactLevel: L3
date: 2026-09-11T21:40:00Z
---

# I1-STA-001 — Orchestrator close-out (VERIFIED)

## Merge and CI evidence

Owner merged PR #17 (candidate 1c05c59e) on 2026-09-11. At the merge commit
072fd310 both scoped workflows are **green**: the new **Service Tests**
workflow (first execution — Station Operations suite on JDK 25 temurin) and
**Database Migrations**. G3 correctly not triggered (no G3-scope paths
changed in this diff); G3 remains green on main from 6050685a.

## Residual register (standing disclosures, per owner's documentation requirement)

1. **Observability stub** — `infra/local/compose.observability.yaml` is an
   empty profile by design (I1-ENG-001); ARC-015 M1's "basic observability"
   criterion completes with a dedicated observability packet.
2. **POC-01 / POC-03 / POC-04 not yet formally executed** (ARC-015 §10) —
   required before the identity task, EPIC-10 allocation, and messaging
   reliance respectively. DAT/STA test infrastructure already exercises the
   PostgreSQL/RabbitMQ substrate those PoCs formalize.
3. **Derived column-level designs** — ARC-022 §9 fixes table names only;
   STA-001 column designs derive from DOM-002 states, the glossary, and
   S1-03 constants; version states DRAFT/ACTIVE/RETIRED are a documented
   design following §5.7 snapshot semantics (reviewer/owner accepted via
   merge).
4. **Local JDK 21** — authoritative Java-25 execution now proven in CI
   (three workflows); local builds remain diagnostic release 21 until a JDK
   25 is installed locally.
5. **Traceability rows still PLANNED** (42) — flip requires implemented and
   CI-verified features; none qualify yet.
6. **Control-plane count regressions** — two transient incidents (ENG-002,
   STA-001 rounds) where a masked exit code let a count slip into a push;
   both corrected immediately; gate commands now run under `pipefail`.
7. **Prior-task residuals unchanged**: JDK-25 local build (item 4); STA
   reset's trigger bypass confined to the migrator maintenance path.

## Control plane

I1-STA-001 VERIFIED; counts: 7 VERIFIED, 0 in flight. W1-S1 wave-1
foundation (control plane, toolchain, contracts, runway, kernel, persistence
foundation, infrastructure seed) is complete.

## Recommended next packet

**Messaging foundation** (I1-MSG-001, EPIC-03 completion + POC-04): per-
service outbox tables (ARC-022 §8) in station_operations_db, an outbox
dispatcher, RabbitMQ topology per the approved contract model, and STA
publication events for the seeded stations — this is the prerequisite for
Discovery projections (S1-02) and everything downstream in the S1 journey.
