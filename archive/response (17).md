OpenCode discovers project skills at `.opencode/skills/<name>/SKILL.md`; each requires YAML frontmatter with a matching lowercase skill name and description. ([opencode.ai](https://opencode.ai/docs/skills?utm_source=openai))

<.opencode/skills/task-packet/SKILL.md>
---
name: task-packet
description: Create or validate a repository-visible implementation task packet with authority references, bounded scope, acceptance criteria, dependencies, tests, review gates, and evidence requirements.
compatibility: opencode
metadata:
  project: ev-charging-platform
  category: delivery-governance
---

# Task Packet Skill

## Purpose

Use this skill when:

- creating a new implementation task;
- validating whether a task is ready;
- preparing context for planner or coder agents;
- splitting an oversized task;
- checking that a task has sufficient authority and evidence requirements.

A task packet is the complete repository-visible authority for one bounded unit of delivery.

Chat history is not a substitute for a task packet.

## Canonical location

```text
delivery/tasks/<TASK-ID>.yaml
```

Supporting artifacts:

```text
delivery/handoffs/<TASK-ID>/
delivery/evidence/<TASK-ID>/
delivery/deviations/<TASK-ID>/
```

## Required preparation

Before creating or validating a task packet:

1. Read `AGENTS.md`.
2. Read `delivery/status.yaml`.
3. Read the applicable iteration record.
4. Inspect the current repository baseline.
5. Identify exact governing documents and sections.
6. Check existing related tasks and dependencies.
7. Check for unresolved contradictions or open decisions.
8. Never invent missing authority.

## Required packet schema

```yaml
schemaVersion: "1.0"

task:
  id: "ITERATION-AREA-NNN"
  title: ""
  state: "BACKLOG"
  iteration: ""
  epic: ""
  releaseWave: "W1"
  sliceApplicability: "W1-S1"
  impactLevel: "L1"
  owner: ""
  createdAt: ""
  baselineCommit: ""

objective: ""

nonGoals:
  - ""

authority:
  governance:
    - path: ""
      section: ""
      decisionIds: []
      purpose: ""
  requirements:
    - id: ""
      path: ""
      section: ""
  useCases:
    - id: ""
      path: ""
      section: ""
  domain:
    - path: ""
      section: ""
      invariantIds: []
  architecture:
    - path: ""
      section: ""
      decisionIds: []
  securityPrivacy:
    - path: ""
      section: ""
      controlIds: []
  contracts:
    - path: ""
      identifiers: []

ownership:
  owningService: ""
  dataOwner: ""
  supportingServices: []
  prohibitedOwners: []

affectedArtifacts:
  services: []
  modules: []
  tables: []
  projections: []
  migrations: []
  apiOperations: []
  commands: []
  events: []
  schemas: []
  registries: []
  screens: []
  infrastructure: []

dependencies:
  tasks: []
  contracts: []
  infrastructure: []
  humanDecisions: []

blockers: []

scope:
  allowedFiles: []
  prohibitedFiles: []
  maximumExpectedDiff: ""
  generatedFilesPolicy: ""

acceptanceCriteria:
  - id: "AC-01"
    statement: ""
    authorityReferences: []
    evidenceRequired: []
    testTypes: []

tests:
  focusedCommands: []
  requiredSuites: []
  positiveCases: []
  negativeCases: []
  boundaryCases: []
  concurrencyCases: []
  securityCases: []
  contractCases: []
  migrationCases: []
  accessibilityCases: []

persistence:
  affected: false
  owningDatabase: ""
  migrationRequired: false
  migrationStrategy: ""
  forwardFixStrategy: ""
  rollbackStrategy: ""
  seedResetImpact: ""

contracts:
  affected: false
  compatibilityClassification: ""
  requiredUpdates: []
  requiredExamples: []
  requiredConsumerTests: []

securityPrivacy:
  affected: false
  threatChanges: []
  dataClassifications: []
  authorizationPolicies: []
  rateLimitPolicies: []
  auditRequirements: []
  prohibitedData: []

reviews:
  planner: true
  tester: true
  generalReviewer: true
  contractReviewer: false
  dataReviewer: false
  securityReviewer: false
  accessibilityReviewer: false
  humanApprovalRequired: false
  requiredHumanRole: ""

evidence:
  requiredFiles: []
  requiredCiChecks: []
  requiredCommitSha: true
  requiredRunReference: true

assumptions: []
openQuestions: []
```

## Validation rules

### Identity

- Task ID must be unique.
- Title must describe one bounded outcome.
- Iteration, epic, release wave, and slice must be explicit.
- Baseline commit must be immutable when implementation begins.

### Objective

The objective must describe an observable result.

Bad:

> Implement booking improvements.

Good:

> Add exact-EVSE hold persistence that permits only one concurrent winning hold and returns the canonical allocation-conflict response to losers.

### Non-goals

Non-goals must prevent scope expansion.

Examples:

- no automatic EVSE assignment;
- no rescheduling;
- no operator UI;
- no production deployment;
- no change to Booking lifecycle states.

### Authority

Every normative requirement must reference:

- exact repository path;
- exact heading or section;
- applicable decision, requirement, use-case, invariant, or contract identifier.

Do not reference only a document title.

If authorities disagree, return `SPEC_CONFLICT`.

### Ownership

Confirm:

- one authoritative service;
- one data owner;
- no cross-service database access;
- supporting services do not become co-owners;
- shared libraries do not acquire business authority.

### Scope

Allowed files must be explicit enough to prevent unrelated edits.

Prohibited files should include sensitive areas not authorized by the task.

Examples:

- governance documents;
- domain lifecycle documents;
- contracts;
- migrations;
- CI workflows;
- security configuration;
- unrelated services.

### Acceptance criteria

Each criterion must:

- be independently testable;
- describe behavior rather than implementation preference;
- identify required evidence;
- map to authority;
- include failure behavior where relevant.

Avoid:

- “works correctly”;
- “handle errors”;
- “add tests”;
- “improve performance.”

### Tests

Select applicable categories:

- positive;
- negative;
- boundary;
- state transition;
- idempotency;
- concurrency;
- authorization;
- privacy;
- contract;
- migration;
- provider/consumer;
- accessibility;
- resilience;
- end-to-end.

A concurrency or PostgreSQL invariant requires real PostgreSQL evidence.

### Impact level

Use:

- `L0`: non-normative.
- `L1`: isolated implementation.
- `L2`: contract, shared module, or public behavior.
- `L3`: migration, concurrency, security, privacy, audit, infrastructure, lifecycle.
- `L4`: architecture, ownership, destructive operation, production, release, legal decision.

Do not lower impact to avoid review.

### Human approval

Explicit approval is required before editing when the task contains:

- L4 work;
- destructive migration;
- ownership or boundary change;
- approved lifecycle change;
- security-risk acceptance;
- privacy-risk acceptance;
- production operation;
- contract-breaking change without an approved migration path.

## Definition of Ready

A task is `READY` only when:

- all required fields are complete;
- authorities agree;
- dependencies are satisfied;
- acceptance criteria are measurable;
- scope is bounded;
- required tests are known;
- review requirements are correct;
- no W1-critical blocker remains;
- required pre-edit approval exists.

## Oversized-task indicators

Split a task when it:

- affects more than one independently deployable capability;
- mixes foundation, feature, and deployment work;
- combines contract design and several service implementations;
- requires unrelated migrations;
- has more than one independently demonstrable outcome;
- cannot be reviewed as one coherent change;
- is expected to exceed the repository’s task-diff limit.

Split by dependency, not by arbitrary file count.

## Output format

When validating a task, return:

```text
TASK_PACKET_STATUS:
TASK_ID:
RECOMMENDED_STATE:
IMPACT_LEVEL:

Missing fields:
Authority conflicts:
Dependency blockers:
Scope concerns:
Acceptance-criteria concerns:
Test gaps:
Required reviewers:
Human approval required:
Recommended corrections:
```

Allowed statuses:

```text
READY
INCOMPLETE
BLOCKED
SPEC_CONFLICT
HUMAN_APPROVAL_REQUIRED
SPLIT_REQUIRED
```

## Prohibited behavior

Never:

- fabricate authority references;
- silently resolve contradictions;
- mark a dependency complete without evidence;
- create vague acceptance criteria;
- omit negative tests from high-risk behavior;
- treat chat history as approval;
- place secrets or personal data in task packets;
- mark a task ready merely because implementation has already started.
</.opencode/skills/task-packet/SKILL.md>

<.opencode/skills/architecture-check/SKILL.md>
---
name: architecture-check
description: Review a task, plan, or diff against approved service boundaries, data ownership, lifecycle, consistency, messaging, security, privacy, and operational architecture.
compatibility: opencode
metadata:
  project: ev-charging-platform
  category: architecture-review
---

# Architecture Check Skill

## Purpose

Use this skill when reviewing:

- an implementation plan;
- a proposed dependency;
- a service interaction;
- a new table or projection;
- an API or message;
- a shared library;
- a transaction or workflow;
- an implementation diff;
- a proposed deviation from approved architecture.

This skill detects architectural violations. It does not approve architectural changes.

## Required inputs

Collect:

- task ID;
- baseline commit;
- task objective and non-goals;
- impact level;
- complete affected-file list;
- governing documents and sections;
- implementation plan or diff;
- affected services, data, contracts, and workflows;
- tests and evidence available.

## Authority order

Use the authority order defined in `AGENTS.md`.

At minimum inspect applicable sections of:

```text
docs/00_governance/
docs/01_scope_and_requirements/
docs/03_domain/
docs/05_architecture/
docs/06_security_and_privacy/
contracts/registries/
delivery/tasks/
```

Do not use archived documents as current authority.

## Review procedure

## 1. Scope and ownership

Determine:

- business capability being changed;
- authoritative service;
- authoritative database;
- supporting services;
- projection owners;
- public and internal contract surfaces.

Check:

- exactly one authoritative owner exists;
- no service assumes authority owned elsewhere;
- no cross-service database read, write, join, or foreign key exists;
- no duplicated aggregate becomes independently authoritative;
- no shared library contains business policy or aggregate ownership;
- browser code does not call internal services directly.

## 2. Canonical service boundaries

Verify the approved service topology remains intact.

Look for:

- new undeclared services;
- renamed services that create aliases;
- capability leakage into the BFF;
- Notification owning business transactions;
- Discovery making authoritative allocation decisions;
- Device Integration owning Booking or Session state;
- Governance becoming an operational coordinator;
- direct browser-to-device communication.

Any service-boundary or ownership change is L4.

## 3. Domain lifecycle and invariants

For each affected aggregate:

- identify its canonical lifecycle;
- verify every source and target state;
- verify transition guards;
- distinguish persistent states from processing phases;
- verify terminal and uncertain-state semantics;
- verify emitted facts follow committed state.

Check especially:

- Booking and SessionAttempt are distinct;
- device acceptance does not prove charging;
- unresolved physical outcomes remain uncertain;
- equipment failure is not classified as `NO_SHOW`;
- capacity remains blocked while occupation is uncertain;
- retry uses a new authorization and attempt;
- allowed actions are server-authoritative.

Unknown or invented lifecycle states are blocking findings.

## 4. Data consistency and transaction boundaries

Check:

- correctness uses authoritative database time;
- intervals are finite, non-empty, and half-open;
- required guards are locked in canonical order;
- no remote call occurs while locks are held;
- all correctness predicates are revalidated after locking;
- constraints provide final integrity protection;
- business state, audit, idempotency result, and outbox commit atomically where required;
- retries rerun the complete transaction safely;
- planned allocation and physical occupation remain separate.

For allocation-related changes, require data review and real PostgreSQL tests.

## 5. Integration architecture

For REST:

- synchronous call is appropriate;
- target owns the requested decision;
- failure behavior is explicit;
- optional preflight does not become authoritative;
- browser/BFF/internal surfaces remain separate.

For messaging:

- commands have one logical handler;
- events describe committed facts;
- delivery assumes at least once;
- consumers use inbox/idempotency;
- ordering/version behavior is explicit;
- retries and quarantine are bounded;
- message publication uses outbox;
- message names come from the canonical registry.

Look for dual mutation paths that can bypass the same invariant.

## 6. Projection architecture

Check:

- source aggregate version is retained;
- older versions cannot overwrite newer ones;
- duplicates are harmless;
- version gaps become unknown/degraded where required;
- rebuild procedure exists;
- authoritative writes do not depend on an unboundedly stale projection;
- Booking-local enforcement projections fail closed;
- Discovery projections remain advisory;
- Discovery contains no account, driver, or vehicle identifiers.

## 7. Security architecture

Check:

- browser uses opaque BFF session;
- OAuth tokens are not exposed to browser JavaScript;
- CSRF protects browser mutations;
- internal APIs require target-audience service tokens;
- current membership and ownership are validated authoritatively;
- delegated actor assertions are operation/resource/audience bound;
- cross-tenant access fails;
- secrets are not logged or included in messages;
- privileged actions produce audit evidence;
- data classification is preserved across boundaries.

Security-sensitive changes require security review.

## 8. Failure and reconciliation architecture

Check behavior for:

- network timeout;
- database conflict;
- broker outage;
- duplicate delivery;
- out-of-order event;
- device disconnect;
- stale projection;
- stale telemetry;
- provider failure;
- partial workflow completion;
- manual reconciliation.

Failures must not create false success or silently release protected capacity.

## 9. Operational architecture

Check:

- health and readiness semantics;
- structured logs and trace correlation;
- no sensitive payload logging;
- bounded retries;
- metrics for critical workflows;
- configuration is environment-specific;
- local assumptions are not represented as production guarantees;
- deployment does not bypass migration or security gates.

## 10. Traceability

Verify the change traces to:

- requirement;
- use case;
- domain invariant;
- owning service;
- contract;
- persistence artifact;
- test;
- release wave.

Missing traceability is at least a major finding for L2/L3 work.

## Finding severity

### BLOCKER

- service/data ownership violation;
- cross-service database access;
- invalid lifecycle transition;
- security boundary bypass;
- allocation correctness defect;
- false physical success;
- data-loss risk;
- unresolved specification contradiction.

### MAJOR

- missing required failure behavior;
- duplicate authoritative contract;
- insufficient projection/version handling;
- missing outbox/inbox behavior;
- significant traceability or test gap;
- unapproved dependency or shared business model.

### MINOR

- bounded maintainability or clarity issue;
- noncritical naming inconsistency;
- missing nonessential operational detail.

### NOTE

- optional improvement outside the task’s Definition of Done.

## Finding format

```text
FINDING_ID:
SEVERITY:
CATEGORY:
LOCATION:
AUTHORITY:
OBSERVATION:
ARCHITECTURAL_IMPACT:
REQUIRED_CORRECTION:
REQUIRED_EVIDENCE:
```

## Required output

```text
ARCHITECTURE_CHECK_STATUS:
TASK_ID:
BASELINE_COMMIT:
IMPACT_LEVEL:

Capabilities reviewed:
Service ownership:
Data ownership:
Lifecycle assessment:
Transaction assessment:
Integration assessment:
Projection assessment:
Security/privacy assessment:
Failure/reconciliation assessment:
Operational assessment:
Traceability assessment:
Findings:
Required specialist reviews:
Human decisions required:
Recommended state:
```

Allowed statuses:

```text
PASS_NO_BLOCKERS
FAIL_FINDINGS
BLOCKED
SPEC_CONFLICT
HUMAN_APPROVAL_REQUIRED
```

A passing architecture check is not approval to change architecture.
</.opencode/skills/architecture-check/SKILL.md>

<.opencode/skills/migration-review/SKILL.md>
---
name: migration-review
description: Review PostgreSQL and Flyway migration changes for ownership, forward-only safety, constraints, indexes, locking, upgrade compatibility, role separation, seed/reset behavior, and reproducible evidence.
compatibility: opencode
metadata:
  project: ev-charging-platform
  category: data-review
---

# Migration Review Skill

## Purpose

Use this skill for any change involving:

- Flyway migrations;
- database schemas;
- tables or columns;
- constraints or indexes;
- PostgreSQL extensions;
- data backfills;
- role or grant changes;
- seed or reset behavior;
- retention or anonymization;
- transaction and lock procedures;
- schema compatibility.

Migration work is at least L3 unless the task authority explicitly classifies a nonexecuting draft otherwise.

## Required inputs

Collect:

- task ID;
- baseline and candidate commit;
- owning service and database;
- migration paths;
- current supported schema version;
- deployment assumptions;
- complete migration diff;
- associated persistence specification;
- affected API/message behavior;
- test evidence;
- forward-fix strategy.

## Mandatory authority checks

Confirm:

- the owning service owns every changed object;
- no cross-service foreign key or database access is introduced;
- table and state names match approved domain and persistence documents;
- migration ordering follows repository conventions;
- the migration corresponds to an approved requirement.

Return `SPEC_CONFLICT` when persistence documents and domain authority disagree.

## Review procedure

## 1. Migration identity and immutability

Verify:

- migration version/name is unique;
- applied migrations were not edited;
- correction uses a new migration;
- filename matches project Flyway convention;
- migration ordering is deterministic;
- checksums are expected to remain stable;
- migration contains no developer-specific path or environment assumption.

Editing an already-applied migration is a blocker.

## 2. Ownership and namespace

Check:

- object belongs to the service database;
- schema and object names are canonical;
- no foreign key references another service database;
- no shared operational database becomes cross-service authority;
- runtime, migrator, and operations roles remain distinct;
- object ownership and grants are explicit where required.

## 3. Data types

Verify appropriate use of:

- `uuid` for internal references where approved;
- bounded text types or validated text;
- `timestamptz` for instants;
- `date` or local-time types only for genuinely local business concepts;
- `tstzrange` for approved intervals;
- `numeric` for monetary calculations;
- integer units for power/energy where approved;
- `jsonb` only when structure and validation policy are explicit.

Flag ambiguous timestamps, floating-point money, or unbounded opaque JSON.

## 4. Nullability and defaults

Check:

- nullable columns represent legitimate states;
- defaults do not fabricate business meaning;
- server or database time is used consistently;
- adding a required column to existing data has a safe population sequence;
- default expressions are stable and supported;
- temporary migration defaults are removed when appropriate.

## 5. Constraints

Verify required:

- primary keys;
- unique keys;
- foreign keys inside the owning database only;
- check constraints;
- interval shape constraints;
- state-field consistency;
- exclusion constraints;
- source/version uniqueness;
- idempotency uniqueness.

Check that:

- every constraint has a stable explicit name;
- constraints map to safe application problem codes where relevant;
- state-transition logic is not falsely claimed to be enforced by a current-row `CHECK`;
- runtime roles cannot disable constraints.

## 6. Indexes

For every query or lock procedure affected:

- identify predicate and ordering;
- verify supporting indexes;
- avoid redundant indexes;
- ensure partial-index predicates are immutable and semantically safe;
- verify range and GiST indexes match conflict queries;
- verify outbox, expiry, reconciliation, and projection indexes;
- consider write cost and index size.

Require query-plan evidence for critical allocation paths when available.

## 7. Locking and concurrency

Check:

- canonical lock order;
- bounded lock timeout;
- authoritative database time;
- no network call while locks are held;
- conflict predicate and constraint agree;
- race safety does not depend only on application prechecks;
- retries rerun the complete transaction;
- deadlock/constraint failures map to approved outcomes;
- physical occupation and planned capacity remain separate.

Require real concurrent PostgreSQL tests for allocation, claims, restrictions, occupations, and idempotency.

## 8. Migration execution safety

Classify the migration:

```text
ADDITIVE
BACKFILL
CONSTRAINT_HARDENING
INDEX_CHANGE
DATA_TRANSFORMATION
DESTRUCTIVE
ROLE_OR_PERMISSION
EXTENSION
```

Assess:

- expected lock strength and duration;
- table rewrite risk;
- data volume assumptions;
- transaction behavior;
- deployment order;
- compatibility with old and new application versions;
- failure halfway through;
- restart/retry behavior;
- operational monitoring.

Do not assume a local empty database proves upgrade safety.

## 9. Expand-and-contract behavior

For incompatible changes, require an approved sequence:

1. add compatible structure;
2. deploy dual-compatible code if needed;
3. backfill safely;
4. validate data;
5. switch reads/writes;
6. enforce new constraint;
7. remove old structure in a later approved migration.

Destructive contraction requires explicit human approval.

## 10. Backfill review

Check:

- deterministic selection;
- bounded batches where needed;
- restartability;
- no silent data truncation;
- explicit invalid-data handling;
- safe transaction size;
- progress and failure observability;
- post-backfill validation;
- retention/privacy requirements.

Do not allow fabricated values merely to satisfy a constraint.

## 11. Roles and privileges

Verify:

- migrator has DDL rights;
- runtime has only required DML rights;
- runtime cannot alter schema;
- audit history cannot be updated/deleted by runtime;
- restricted operations roles are bounded;
- default/public privileges are not overly broad;
- local convenience grants do not become production defaults.

## 12. Seed and reset

Check:

- seed data uses deterministic synthetic references;
- service-owned bootstrap preserves ownership;
- cross-service SQL seeding is prohibited;
- reset is local/test only;
- destructive reset requires explicit confirmation;
- no reset endpoint ships in production;
- seed version/hash can be verified;
- projection rebuild occurs after source seeding.

## 13. Retention and anonymization

When affected, verify:

- deletion owner;
- legal/provisional policy status;
- hold exceptions;
- anonymization preserves required aggregate evidence;
- indexes support retention selection;
- retention role cannot rewrite history;
- audit evidence records the operation safely.

## Required tests

At minimum require applicable:

1. migrate empty database;
2. migrate from previous supported version;
3. validate Flyway checksums;
4. verify expected objects and grants;
5. verify runtime DDL denial;
6. test every critical constraint;
7. test duplicate/idempotency constraints;
8. test concurrent conflict behavior;
9. test query/index behavior for critical paths;
10. test seed/reset determinism;
11. test migration failure and forward recovery;
12. test old/new application compatibility when required.

## Prohibited corrections

Never recommend:

- editing an applied migration;
- disabling a constraint to pass tests;
- using cross-service foreign keys;
- giving runtime DDL rights;
- running destructive clean/reset in shared environments;
- weakening concurrency assertions;
- deleting invalid production data without approved handling;
- claiming rollback when only a forward fix is safe.

## Finding format

```text
FINDING_ID:
SEVERITY:
CATEGORY:
MIGRATION:
LOCATION:
AUTHORITY:
OBSERVATION:
DATA_OR_OPERATIONAL_IMPACT:
REQUIRED_CORRECTION:
REQUIRED_TEST:
```

Use `BLOCKER`, `MAJOR`, `MINOR`, or `NOTE`.

## Required output

```text
MIGRATION_REVIEW_STATUS:
TASK_ID:
OWNING_SERVICE:
OWNING_DATABASE:
BASELINE_VERSION:
TARGET_VERSION:

Migration inventory:
Immutability assessment:
Ownership assessment:
Schema assessment:
Constraint/index assessment:
Concurrency assessment:
Execution-risk classification:
Upgrade compatibility:
Role/grant assessment:
Seed/reset assessment:
Retention/privacy assessment:
Test evidence:
Findings:
Human approval required:
Forward-fix assessment:
Recommended state:
```

Allowed statuses:

```text
PASS_NO_BLOCKERS
FAIL_FINDINGS
BLOCKED
SPEC_CONFLICT
HUMAN_APPROVAL_REQUIRED
```

A passing review does not approve execution in a shared or production environment.
</.opencode/skills/migration-review/SKILL.md>

<.opencode/skills/contract-review/SKILL.md>
---
name: contract-review
description: Review OpenAPI, AsyncAPI, JSON Schema, message, lifecycle, problem-code, policy, authorization, UI, and traceability contracts for correctness, compatibility, security, and consistency.
compatibility: opencode
metadata:
  project: ev-charging-platform
  category: contract-review
---

# Contract Review Skill

## Purpose

Use this skill when a task affects:

- OpenAPI;
- AsyncAPI;
- JSON Schema;
- API examples;
- commands or events;
- problem codes;
- lifecycle registries;
- policy registries;
- authorization registries;
- traceability;
- generated clients;
- provider/consumer fixtures;
- compatibility rules.

Contract changes are at least L2. Security-sensitive or lifecycle-impacting contract work may be L3.

## Required inputs

Collect:

- task ID;
- baseline and candidate commit;
- affected operations/messages/schemas;
- governing requirements and use cases;
- domain lifecycle authority;
- service/data owner;
- selected specification versions;
- compatibility policy;
- complete contract diff;
- generated outputs;
- validator and test evidence.

## Authority rule

Determine the current authoritative sources before reviewing.

Normally:

- domain documents own lifecycle and invariants;
- service architecture owns interaction responsibility;
- contract governance owns format/version policy;
- canonical registries own executable names and mappings;
- OpenAPI owns REST wire contracts;
- AsyncAPI plus JSON Schema owns message wire contracts.

If two executable or normative sources disagree, return `SPEC_CONFLICT`.

Do not choose whichever artifact validates more easily.

## Review procedure

## 1. Contract inventory

List all affected:

- API operations;
- request/response schemas;
- security schemes;
- problem codes;
- commands;
- events;
- channels/exchanges;
- message schemas;
- lifecycle entries;
- policies;
- examples;
- generated clients;
- traceability entries.

Detect stale aliases and duplicate normative definitions.

## 2. Specification and dialect

Confirm:

- each file declares the approved specification version;
- OpenAPI schemas use the approved OpenAPI dialect;
- standalone message schemas use the approved JSON Schema dialect;
- AsyncAPI tooling supports the selected schema format;
- cross-dialect reuse follows the approved conversion/equivalence strategy;
- `$ref` paths resolve deterministically;
- validator versions are pinned.

Do not “fix” compatibility by silently changing specification versions.

## 3. REST operation review

For every operation verify:

- unique stable `operationId`;
- owning service;
- release wave and slice;
- requirement/use-case trace;
- correct public, BFF, or internal surface;
- authentication scheme;
- authorization-policy reference;
- CSRF requirement for browser mutations;
- idempotency requirement;
- `If-Match`/version behavior where applicable;
- request content type;
- success status;
- asynchronous versus synchronous semantics;
- all applicable Problem Details responses;
- safe examples;
- pagination/filter/sorting behavior;
- server time, freshness, version, and allowed actions where required.

Charging start/stop acceptance must not claim physical completion.

## 4. Security-surface review

Check:

### Browser/BFF

- opaque secure session cookie;
- no browser bearer-token requirement;
- CSRF on mutations;
- safe same-origin behavior;
- no secret in browser payloads.

### Internal APIs

- audience-specific service tokens;
- explicit calling-service scope;
- delegated human context where required;
- authoritative resource checks;
- no unsigned actor headers as authorization.

### Public APIs

- anonymous versus authenticated behavior is explicit;
- rate-limit policy exists;
- error responses do not enable enumeration.

## 5. Problem-code review

For every code verify:

- one canonical name;
- one HTTP status;
- retryability;
- safe title/detail;
- allowed parameters;
- applicable operations;
- release wave;
- UI mapping where relevant.

Check especially:

- 412 for failed version preconditions where approved;
- 409 for state or idempotency conflicts;
- 422 for semantically invalid requests;
- 503 for unavailable or untrustworthy operational evidence;
- 429 with retry guidance for rate limits.

Raw SQL, framework, stack-trace, or foreign-resource details are prohibited.

## 6. Message inventory review

For every message verify:

- canonical logical name;
- versioned type;
- kind: event, command, or telemetry;
- producer;
- event consumers or exactly one command handler;
- exchange/channel and routing key;
- schema path;
- data classification;
- aggregate reference/version;
- correlation/causation requirements;
- idempotency behavior;
- ordering behavior;
- timeout and outcomes for commands;
- retry and quarantine behavior;
- release wave;
- deprecated aliases.

Events describe committed past-tense facts.

Commands use imperative intent and must not be listed as events.

## 7. Message semantics

Check:

- command acceptance is distinct from completed outcome;
- command outcomes include accepted/rejected/timed-out/unresolved as applicable;
- duplicate delivery is safe;
- version gaps have defined behavior;
- no exactly-once transport assumption exists;
- event publication uses outbox;
- consumers use inbox/idempotency;
- payload contains only data required by consumers.

## 8. JSON Schema review

For every schema verify:

- `$schema`;
- unique stable `$id`;
- title and description;
- required fields;
- property types and formats;
- bounds and patterns;
- enum authority;
- `additionalProperties` policy;
- nullability representation;
- `$ref` resolution;
- examples;
- invalid fixtures;
- compatibility behavior;
- classification.

Do not use schema validation as a substitute for domain authorization or lifecycle validation.

## 9. Data-minimization review

Check:

- Discovery messages reject account, driver, subject, email, vehicle, or contact identifiers;
- authorization secrets are absent;
- tokens and credentials are absent;
- device credentials are absent;
- public errors do not expose another Booking reference;
- event consumers receive only purpose-required data;
- pseudonymous identifiers remain correctly classified as personal data where applicable.

## 10. Lifecycle registry review

Verify:

- owning service/module;
- exact persistent states;
- processing-only phases;
- terminal/quasi-terminal flags;
- permitted transitions;
- transition guards;
- emitted facts.

The registry must exactly reproduce approved domain authority.

Unknown states or forbidden transitions are blockers.

## 11. Policy and authorization registries

Check:

- policy version and owner;
- release applicability;
- authoritative decision reference;
- operation coverage;
- minimum role/scope;
- membership and ownership requirements;
- assurance level;
- authentication age;
- audit category;
- rate-limit policy;
- existence masking;
- default-deny behavior.

Every protected W1 operation must map to one authorization policy.

## 12. Traceability

Every W1 contract entry must trace to:

- requirement;
- use case;
- owner;
- persistence or projection effect;
- security control;
- test;
- epic/task;
- release wave.

Traceability may not claim implementation or verification without evidence.

## 13. Compatibility review

Classify each change:

```text
NON_BREAKING
POTENTIALLY_BREAKING
BREAKING
SEMANTIC_BREAK
DOCUMENTATION_ONLY
```

Review:

- removed operations/messages/fields;
- new required fields;
- changed types/formats;
- tightened constraints;
- enum additions/removals;
- status-code changes;
- changed defaults;
- renamed events;
- routing changes;
- changed security requirements;
- altered error semantics;
- consumer tolerance for optional additions.

Follow the project’s approved compatibility policy. Do not assume every optional-field or enum addition is safe for every generated client.

Breaking changes require explicit versioning and human approval.

## 14. Examples and fixtures

Require:

- one valid example per operation/message;
- malformed example;
- semantically invalid example;
- authorization failure;
- idempotency retry;
- changed-payload idempotency failure;
- problem response;
- data-minimization assertion.

Examples must validate against the actual executable schema.

## 15. Generated artifacts

Check:

- generated clients compile;
- generated output was not manually edited;
- generation is reproducible;
- source contract is identifiable;
- browser clients are not generated from internal APIs;
- stale generated output is detected in CI.

## 16. Required validation

Run applicable project commands for:

- OpenAPI structural validation;
- OpenAPI style rules;
- AsyncAPI validation;
- JSON Schema syntax and reference validation;
- example validation;
- registry validation;
- duplicate operation/message detection;
- compatibility comparison;
- generated-client compilation;
- provider tests;
- consumer tests;
- privacy checks;
- documentation consistency.

Record exact commands and versions.

## Finding severity

### BLOCKER

- executable contract contradicts domain authority;
- security boundary is wrong;
- duplicate canonical meaning;
- invalid lifecycle;
- Discovery personal-data leak;
- unresolved references;
- breaking change without approval/versioning.

### MAJOR

- missing operation outcome;
- missing problem response;
- missing compatibility evidence;
- invalid/missing examples;
- incomplete traceability;
- command without one handler or outcomes;
- inconsistent status or retry semantics.

### MINOR

- bounded description, naming, or example-quality issue.

### NOTE

- optional future improvement.

## Finding format

```text
FINDING_ID:
SEVERITY:
CONTRACT_TYPE:
IDENTIFIER:
LOCATION:
AUTHORITY:
OBSERVATION:
COMPATIBILITY_OR_SECURITY_IMPACT:
REQUIRED_CORRECTION:
REQUIRED_VALIDATION:
```

## Required output

```text
CONTRACT_REVIEW_STATUS:
TASK_ID:
BASELINE_COMMIT:
CANDIDATE_COMMIT:
IMPACT_LEVEL:

Contract inventory:
Authority alignment:
OpenAPI assessment:
AsyncAPI assessment:
JSON Schema assessment:
Registry assessment:
Security/privacy assessment:
Compatibility classification:
Examples/fixtures assessment:
Generated-artifact assessment:
Traceability assessment:
Validation commands and results:
Findings:
Human approval required:
Recommended state:
```

Allowed statuses:

```text
PASS_NO_BLOCKERS
FAIL_FINDINGS
BLOCKED
SPEC_CONFLICT
HUMAN_APPROVAL_REQUIRED
```

A passing contract review is not merge approval.
</.opencode/skills/contract-review/SKILL.md>

To avoid approval prompts for routine use, set selected agents’ `permission.skill` entries to `allow` for these four skill names; otherwise your existing `ask` policy will prompt before loading them. ([opencode.ai](https://opencode.ai/docs/skills?utm_source=openai))