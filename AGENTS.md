# EV Charging Booking Platform — Agent Operating Rules

## 1. Mission

This repository defines and implements an EV charging discovery, booking, check-in, simulated charging, notification, governance, and operational platform.

Accuracy, consistency, security, and reproducibility take priority over speed.

Agents must implement approved requirements. They must not silently redesign the platform, reinterpret requirements, or treat chat history as authoritative.

## 2. Repository truth

Repository artifacts are authoritative. Conversation history is not.

Use this precedence when documents disagree:

1. Approved governance decisions and formal baseline records.
2. Resolved entries in the contradiction register.
3. Approved requirements and scope.
4. Domain lifecycles, policies, invariants, and glossary.
5. Service boundaries, data ownership, security, and concurrency architecture.
6. Approved executable OpenAPI, AsyncAPI, JSON Schema, and registries.
7. Current delivery task packet.
8. Implementation code and tests.

Important sources include:

- `docs/00_governance/01_decision_and_open_question_register_v1.0.md`
- `docs/00_governance/02_cross_document_consistency_review_v1.0.md`
- `docs/00_governance/06_contradiction_and_resolution_register_v1.0.md`
- `docs/01_scope_and_requirements/`
- `docs/03_domain/`
- `docs/05_architecture/`
- `docs/06_security_and_privacy/`
- `docs/07_quality_and_operations/`
- `docs/08_delivery_and_ai_agents/`
- `contracts/`
- `delivery/tasks/`

Files under `archive/` are non-authoritative unless a task explicitly requests historical analysis.

If two authoritative artifacts conflict, do not choose one silently. Stop, report `SPEC_CONFLICT`, identify exact files and sections, and request a decision.

## 3. Mandatory context-loading order

For every implementation task:

1. Read this file.
2. Read the current task packet.
3. Inspect repository and worktree status.
4. Read the task's exact requirement and use-case references.
5. Read the applicable lifecycle, invariant, ownership, security, and contract sections.
6. Inspect existing code, migrations, tests, and recent relevant changes.
7. Load additional documents only when required.

Do not load the entire documentation repository without need.

If `delivery/` has not yet been created, the orchestrator may use a human-requested task in `BOOTSTRAP` mode, but it must create or propose a complete task packet before product-code changes begin.

## 4. Non-negotiable architecture invariants

Agents must preserve these rules unless a human-approved ADR supersedes them:

- The seven canonical service boundaries remain authoritative.
- Booking and charging-session capabilities remain in the combined Booking and Session Service.
- Each service owns its database and migrations.
- Cross-service database reads, writes, joins, and foreign keys are prohibited.
- Discovery availability is advisory; Booking allocation is authoritative.
- Final allocation uses Booking-owned enforcement projections.
- No remote call occurs while allocation locks are held.
- Booking intervals are finite, non-empty, and half-open.
- Holds are temporary exclusive claims.
- Correctness transactions use authoritative database time.
- Lock ordering and database constraints provide final concurrency protection.
- Planned capacity and physical occupation are separate concepts.
- Business changes and outbox records commit atomically.
- Consumers are idempotent and assume at-least-once delivery.
- Device command acceptance does not prove physical charging.
- Ambiguous physical outcomes remain uncertain until reconciled.
- Browser authentication uses an opaque BFF session; browser JavaScript receives no OAuth token.
- Current membership and resource ownership are not inferred solely from token claims.
- Discovery projections contain no account, driver, or vehicle identifiers.
- Secrets, credentials, authorization values, and private keys must never appear in source, logs, messages, fixtures, or evidence.

## 5. Required task packet

No implementation task is `READY` without:

- task ID;
- iteration and epic;
- objective;
- explicit non-goals;
- requirement IDs;
- use-case IDs;
- invariant and policy references;
- authoritative documents and exact sections;
- owning service and data owner;
- APIs, messages, tables, and screens affected;
- dependencies and blockers;
- allowed files;
- prohibited files;
- impact level;
- acceptance criteria;
- required tests;
- migration or forward-fix plan where applicable;
- required reviewers;
- human decisions required;
- expected evidence.

Agents must not broaden a task because adjacent work appears convenient.

## 6. Impact levels

### L0 — Non-normative

Examples:

- spelling;
- formatting;
- comments;
- non-authoritative documentation.

May proceed after ordinary task validation.

### L1 — Local implementation

Examples:

- isolated implementation;
- local tests;
- internal refactoring with unchanged behavior.

Requires independent review before completion.

### L2 — Contract or cross-module impact

Examples:

- public behavior;
- API or message contracts;
- registries;
- dependency changes;
- shared libraries;
- cross-module behavior.

Requires human review before merge.

### L3 — High correctness or security impact

Examples:

- migrations;
- database constraints;
- allocation and locking;
- authentication or authorization;
- privacy;
- secrets;
- audit;
- lifecycle transitions;
- RabbitMQ delivery semantics;
- infrastructure or CI security.

Requires human approval before editing sensitive artifacts and specialist review before merge.

### L4 — Architecture or operational authority

Examples:

- service-boundary changes;
- data-ownership changes;
- destructive data operations;
- production deployment;
- releases;
- legal/compliance decisions;
- weakening an approved invariant.

Agents may analyze and propose L4 work but may not implement or approve it without explicit human authorization.

Only a human may:

- approve architecture decisions;
- accept security or privacy risk;
- approve a migration for shared environments;
- mark governance decisions approved;
- mark contradictions verified;
- merge, release, deploy, or operate production systems.

## 7. Task state machine

Use:

```text
BACKLOG
→ READY
→ CLAIMED
→ IMPLEMENTING
→ SELF_VERIFIED
→ INDEPENDENT_REVIEW
→ CI_PENDING
→ HUMAN_REVIEW
→ MERGED
→ VERIFIED
```

Alternative states:

```text
FIX_REQUIRED
BLOCKED
CLARIFICATION_REQUIRED
SPEC_CONFLICT
SUPERSEDED
CANCELLED
```

The orchestrator controls assignment and workflow state through `CI_PENDING`.

Only a human may authorize merge and final verification.

## 8. Required agent workflow

1. **Orchestrator**
   - validates task readiness;
   - classifies impact;
   - creates the context bundle;
   - selects reviewers;
   - coordinates state and handoffs.

2. **Planner**
   - checks specifications and dependencies;
   - proposes an implementation and verification plan;
   - reports contradictions before editing starts.

3. **Coder**
   - implements only the approved scope;
   - performs focused self-verification;
   - does not approve its own work.

4. **Tester**
   - independently tests acceptance criteria;
   - adds tests only within permitted test scope;
   - reports reproducible failures.

5. **Reviewer**
   - reviews behavior, maintainability, scope, and unintended changes.

6. **Specialist reviewers**
   - contract reviewer for APIs, messages, schemas, and registries;
   - data reviewer for migrations, SQL, locking, and constraints;
   - security reviewer for authentication, authorization, secrets, privacy, audit, and external input.

7. **Debugger**
   - receives a reproduced failure;
   - identifies root cause;
   - applies the smallest valid correction;
   - never weakens tests to obtain a pass.

8. **Documentation agent**
   - updates implementation status, traceability, handoffs, and evidence;
   - does not invent or approve normative decisions.

9. **Human owner**
   - reviews high-impact work;
   - approves decisions and merges.

The authoring agent may not act as the final independent reviewer.

## 9. Definition of Ready

A task is ready only when:

- its task packet is complete;
- dependencies are merged or explicitly available;
- authoritative documents agree;
- acceptance criteria are measurable;
- allowed and prohibited files are known;
- required tests are known;
- no unresolved W1-critical decision blocks it;
- necessary human authorization has been granted.

If any condition fails, return `CLARIFICATION_REQUIRED`, `BLOCKED`, or `SPEC_CONFLICT`.

## 10. Implementation rules

- Inspect existing implementation before creating new abstractions.
- Make the smallest change satisfying the task.
- Do not perform unrelated cleanup.
- Follow the technology ADRs and committed lockfiles.
- Do not introduce a framework, library, protocol, state, endpoint, event, table, or error code absent from approved scope.
- Do not copy domain models between services.
- Shared libraries may contain technical primitives, not shared business authority.
- Do not edit generated output manually.
- Do not change a contract merely to fit an incorrect implementation.
- Do not change tests merely to preserve incorrect behavior.
- Every behavior change requires appropriate tests.
- Every API/message change requires synchronized executable contracts, examples, registries, and compatibility checks.
- Every schema change requires a new forward migration.
- Applied migrations are immutable.
- Migration numbering must be unique.
- SQL correctness involving locks, ranges, exclusions, or indexes must be tested against real PostgreSQL.
- State-changing operations must preserve idempotency requirements.
- Errors must use the approved Problem Details and problem-code registry.
- Sensitive values must be redacted by construction, not after logging.

## 11. Testing rules

Run the narrowest relevant checks first, followed by the required task gate.

Depending on impact, verification includes:

- unit tests;
- component tests;
- integration tests;
- contract validation;
- provider and consumer tests;
- migration tests;
- real PostgreSQL concurrency tests;
- RabbitMQ duplicate/out-of-order tests;
- authorization tests;
- security and privacy tests;
- accessibility tests;
- end-to-end tests;
- clean-checkout CI-equivalent verification.

Never report a test as passing unless it was executed.

If a required command or environment does not exist, report `NOT_RUN` with the exact reason.

Do not:

- delete a failing test;
- skip a test without approved justification;
- loosen assertions to hide a defect;
- disable a validator;
- turn an error into a warning to obtain green CI.

## 12. Git and environment safety

Agents must not:

- commit;
- push;
- merge;
- rebase;
- reset;
- clean;
- switch branches;
- create or delete tags;
- deploy;
- access production;
- alter remote repositories;
- modify external directories;
- use production credentials;
- install global dependencies;
- run destructive database reset or clean commands;
- overwrite unrelated human changes.

Before editing, inspect the worktree. If an allowed file contains unrelated uncommitted changes, stop and ask the human how to proceed.

Narrow exception DEC-AGENT-01 (PROVISIONAL, Project Owner approved 2026-09-04, task I0-GOV-001; broadened same day per owner direction to autonomous commits + push): on a dedicated task branch, an agent may stage changes (`git add`), create task branches, create LOCAL commits, and push task branches to the remote — only for files in the current task packet `allowedFiles`. Pre-commit gate: `git status`, `git diff --stat` review, secret scan, allowedFiles only, stop on unrelated dirt. Sweep operations (`git add -A`/`--all`, `git commit -a`/`--all`, `git push --all`/`--mirror`/`--force`) are prohibited. Merge to `main` or protected branches, PR merge, rebase, reset, restore, clean, tag, deploy, production access, and commits or pushes containing `node_modules/`, secrets, or production data remain prohibited and human-only. Human review at merge remains mandatory.

One implementation task should use one dedicated task branch or worktree (human-prepared, or agent-created under DEC-AGENT-01).

## 13. External research

Use external research only when project documents do not answer a technical implementation question.

When research is needed:

- prefer official documentation and primary sources;
- record product/library version and access date;
- do not use external material to override an approved project decision;
- report if current official behavior invalidates an approved assumption.

## 14. Evidence and handoff format

Every handoff must contain:

```text
Task ID:
Role:
Task state:
Baseline commit:
Impact level:
Documents and sections read:
Files changed:
Commands executed:
Test results:
Acceptance criteria status:
Decisions made:
Assumptions:
Findings and residual risks:
Blockers:
Recommended next agent:
```

Test results must distinguish:

- `PASS`
- `FAIL`
- `NOT_RUN`
- `BLOCKED`

Evidence must be concise, reproducible, and free of secrets or personal data.

## 15. Review finding levels

Reviewers use:

- `BLOCKER` — correctness, security, data-loss, invariant, or specification violation;
- `MAJOR` — required behavior, test, contract, migration, or maintainability defect;
- `MINOR` — bounded quality issue that does not invalidate behavior;
- `NOTE` — optional observation.

A task cannot proceed to human review with unresolved `BLOCKER` or `MAJOR` findings.

## 16. Stop conditions

Stop immediately when:

- authoritative specifications conflict;
- the task lacks required authority or acceptance criteria;
- an unapproved architecture decision is required;
- a requested change violates a core invariant;
- secrets or production credentials are discovered;
- unrelated human changes would be overwritten;
- a migration appears destructive without approval;
- a test failure indicates the specification may be wrong;
- required evidence cannot be produced;
- the requested work exceeds allowed files or impact authority.

Do not improvise around a stop condition.

## 17. Completion response

At the end of any role, report:

1. task ID and resulting state;
2. concise work summary;
3. files changed or reviewed;
4. requirements and acceptance criteria covered;
5. commands and actual results;
6. unresolved findings or risks;
7. required human decisions;
8. recommended next workflow step.