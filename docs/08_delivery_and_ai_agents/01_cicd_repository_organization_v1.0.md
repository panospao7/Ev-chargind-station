# CI/CD and Repository Organization Strategy v1.0

**Document ID:** ARC-014  
**Version:** 1.0  
**Status:** IN_REVIEW  
**Owner:** Delivery / Platform Architect  
**Last reviewed:** 2026-07-12  
**Depends on:** ARC-001–013  
**Authoritative for:** Repository structure, branching, CI/CD workflows, artifact production, supply-chain controls, release promotion and rollback

---

## 1. Purpose

This document defines:

- Monorepo organization
- Git branching and pull-request rules
- GitHub Actions architecture
- Build and test pipelines
- Contract generation and compatibility checks
- Database migration validation
- Container-image production
- SBOM, signing and provenance
- GitOps promotion through Flux
- Infrastructure delivery
- Release and versioning policy
- Dependency automation
- Artifact retention
- Rollback and forward-fix procedures
- CI/CD security and quality gates

---

## 2. Core delivery model

The selected model is:

```text
Short-lived branch
  → Pull request
  → Required CI
  → Merge to main
  → Build immutable images
  → Sign and attest
  → Automated promotion PR
  → Promotion checks
  → Merge deployment state
  → Flux reconciliation
  → In-cluster migrations and rollout
  → Flux commit status
  → Post-deployment verification
```

Principles:

1. `main` is always releasable.
2. No long-lived `develop` branch exists.
3. CI builds artifacts; Flux performs deployment.
4. GitHub Actions does not run ordinary `kubectl apply`.
5. Every deployment is represented by a Git commit.
6. Workloads use immutable image digests.
7. Pull-request artifacts cannot become release artifacts.
8. Production and reference promotions use already-built artifacts.
9. Database rollback does not depend on down migrations.
10. Every release is traceable to source, tests, SBOM and provenance.

---

# 3. Monorepo decision

Use one GitHub monorepo for:

- Angular frontend
- BFF
- Java services
- Charger Simulator
- OpenAPI and AsyncAPI contracts
- Database migrations
- Infrastructure as Code
- Kubernetes manifests
- Documentation
- Cross-service tests
- Release evidence

## 3.1 Rationale

Advantages:

- Atomic contract and consumer changes
- One documentation and architecture baseline
- One dependency-update process
- Easier individual-project navigation
- Shared CI governance
- Coordinated application and GitOps promotion
- Simplified traceability

Accepted disadvantages:

- Larger pipelines
- More path-based build logic
- Broader repository permissions
- Potential coupling through shared build configuration

The monorepo must not become a shared-domain monolith.

---

# 4. Repository structure

```text
/
├── README.md
├── pom.xml
├── mvnw
├── mvnw.cmd
├── package.json
├── package-lock.json
├── .nvmrc
├── .editorconfig
│
├── apps/
│   ├── web/
│   └── bff/
│
├── services/
│   ├── account-service/
│   ├── station-operations-service/
│   ├── booking-session-service/
│   ├── device-integration-service/
│   ├── discovery-insights-service/
│   ├── notification-service/
│   └── governance-support-service/
│
├── simulator/
│   └── charger-simulator/
│
├── contracts/
│   ├── openapi/
│   ├── asyncapi/
│   ├── json-schema/
│   ├── examples/
│   └── compatibility/
│
├── libraries/
│   ├── correlation/
│   ├── secure-logging/
│   ├── event-envelope/
│   └── test-support/
│
├── tests/
│   ├── e2e/
│   ├── performance/
│   ├── concurrency/
│   ├── resilience/
│   ├── security/
│   ├── accessibility/
│   └── recovery/
│
├── infra/
│   ├── tofu/
│   ├── bootstrap/
│   └── clusters/
│
├── docs/
├── scripts/
├── release-manifests/
└── .github/
    ├── workflows/
    ├── actions/
    ├── CODEOWNERS
    ├── dependabot.yml
    ├── pull_request_template.md
    └── ISSUE_TEMPLATE/
```

---

# 5. Repository dependency rules

1. Services cannot import another service’s implementation.
2. Shared libraries contain technical concerns only.
3. Shared libraries cannot contain:
   - Booking rules
   - Lifecycle decisions
   - Persistence entities
   - Authorization policy
   - Domain aggregates
4. Cross-service integration occurs through approved contracts.
5. Generated contract sources are build outputs, not manually edited.
6. Every service owns its migrations.
7. Infrastructure modules cannot depend on application source.
8. Browser code cannot import internal API contracts.
9. Test-support code cannot become production business logic.
10. ArchUnit and repository scripts enforce these rules.

---

# 6. Maven organization

The root `pom.xml` is an aggregator and build-policy parent.

It manages:

- Java version
- Spring BOMs
- Plugin versions
- Test conventions
- Code formatting
- JaCoCo
- Static analysis
- Reproducible-build settings

Each deployable remains an independent Maven module.

Examples:

```text
./mvnw -pl services/booking-session-service -am verify
./mvnw -pl apps/bff -am package
./mvnw verify
```

A root build must not create runtime coupling between services.

---

# 7. Node and frontend organization

The root npm workspace manages:

- Angular application
- Contract tooling
- AsyncAPI tooling
- Frontend tests
- Documentation checks

Rules:

- `package-lock.json` is mandatory.
- CI uses `npm ci`.
- Global npm dependencies are prohibited.
- Generated Angular API clients are created before compilation.
- Generated clients are not manually modified.
- Angular output is embedded into the BFF release artifact.

---

# 8. Git workflow

Use trunk-based development with short-lived branches.

Approved prefixes:

- `feat/`
- `fix/`
- `docs/`
- `arch/`
- `test/`
- `refactor/`
- `chore/`
- `hotfix/`
- `promotion/`

Branches should normally live less than five working days.

## 8.1 Commit convention

Use Conventional Commit-style subjects:

```text
feat(booking): add hold confirmation guard
fix(device): preserve uncertain stop outcome
docs(architecture): approve CI/CD strategy
```

Pull-request titles follow the same convention.

The squash-merge commit uses the approved pull-request title.

## 8.2 Main branch

Direct pushes to `main` are prohibited.

Changes enter through pull requests only.

Use:

- Squash merge
- Linear history
- Automatic source-branch deletion
- No force push
- No branch deletion
- Required status checks
- Signed/verified commits

GitHub rulesets can require pull requests, status checks, signed commits, linear history and blocked force pushes. ([docs.github.com](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets))

## 8.3 Individual-project review policy

While the repository has one maintainer:

- A second-person approval is not mandatory.
- The pull-request checklist is mandatory.
- All required automated checks must pass.
- The owner performs an explicit final review before merge.

When another qualified maintainer joins:

- Require one independent approval for ordinary changes.
- Require CODEOWNER approval for security, allocation, migrations, contracts and infrastructure.

---

# 9. Protected paths

Changes to these paths receive heightened review:

```text
.github/workflows/**
contracts/**
services/booking-session-service/**
services/device-integration-service/**
infra/**
**/db/migration/**
docs/05_architecture/**
```

The pull request must identify:

- Affected requirements
- Affected invariants
- Security/privacy impact
- Contract impact
- Migration impact
- Rollback or forward-fix strategy

---

# 10. GitHub Actions security baseline

## 10.1 Workflow permissions

Global default:

```yaml
permissions: {}
```

Each job grants only required permissions.

Examples:

- Test jobs: `contents: read`
- GHCR publication: `contents: read`, `packages: write`
- Attestation: `id-token: write`, `attestations: write`
- Promotion bot: `contents: write`, `pull-requests: write`

GitHub recommends explicitly granting the `GITHUB_TOKEN` least privilege. ([docs.github.com](https://docs.github.com/en/actions/tutorials/authenticate-with-github_token))

## 10.2 Action pinning

Every external action is pinned to a full commit SHA:

```yaml
uses: actions/checkout@<full-sha> # v6
```

Version comments remain for readability.

GitHub supports enforcing full-length SHA pinning for actions. ([docs.github.com](https://docs.github.com/en/enterprise-cloud%40latest/admin/enforcing-policies/enforcing-policies-for-your-enterprise/enforcing-policies-for-github-actions-in-your-enterprise))

## 10.3 Action allowlist

Initially allow only:

- `actions/*`
- `github/*`
- `docker/*`
- `sigstore/*`
- Approved security vendors
- Project-owned local actions

A new third-party action requires:

- Source review
- Permission review
- Maintainer/reputation review
- Full-SHA pinning
- Documented purpose

## 10.4 Untrusted pull requests

Pull-request workflows:

- Receive no deployment secrets.
- Have read-only repository access.
- Cannot publish images.
- Cannot create releases.
- Cannot modify GitOps state.
- Cannot access infrastructure credentials.

GitHub withholds ordinary secrets from fork pull-request workflows and restricts their `GITHUB_TOKEN`. ([docs.github.com](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows?ref=content-for-engineers-by-engineers-atomist-blog))

`pull_request_target` must not check out or execute untrusted pull-request code.

## 10.5 Runner policy

Use GitHub-hosted ephemeral runners for:

- Pull requests
- Main builds
- Packaging
- Security scans

Do not attach a persistent self-hosted runner to the public repository. GitHub warns that public-repository fork pull requests can expose self-hosted runners to untrusted code. ([docs.github.com](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners?learn=hosting_your_own_runners))

Cluster bootstrap remains a controlled administrative-workstation operation unless an isolated ephemeral runner design is approved later.

---

# 11. Workflow catalogue

```text
.github/workflows/
├── pr-ci.yml
├── main-build.yml
├── promotion-ci.yml
├── release.yml
├── nightly.yml
├── dependency-review.yml
├── codeql.yml
├── infra-validate.yml
├── infra-plan.yml
├── infra-apply.yml
├── docs.yml
└── manual-recovery-validation.yml
```

Reusable workflows:

```text
.github/workflows/reusable/
├── java-module.yml
├── frontend.yml
├── contracts.yml
├── container-image.yml
├── security-scan.yml
├── infrastructure.yml
└── test-report.yml
```

Reusable workflows cannot increase the permissions provided by their caller. ([docs.github.com](https://docs.github.com/en/actions/reference/workflows-and-actions/reusing-workflow-configurations))

---

# 12. Change classification

The first CI job calculates affected areas from Git history.

Categories:

- Documentation only
- Frontend
- BFF
- Individual Java service
- Shared technical library
- REST contracts
- Event contracts
- Database migrations
- Infrastructure
- Security-sensitive
- Allocation-sensitive

Broad rebuild triggers:

- Root `pom.xml`
- Maven Wrapper
- Root npm files
- Shared contract schemas
- Shared technical libraries
- GitHub Actions
- Build scripts
- Container builder configuration

A full build always runs for:

- Release tags
- Nightly validation
- Root build-policy changes
- Uncertain classification

Path classification is an optimization, not a correctness boundary.

---

# 13. Pull-request pipeline

## Stage 1 — Repository validation

- Commit/PR title
- Formatting
- Documentation links
- Architecture metadata
- File naming
- Forbidden generated files
- Secret scan
- Workflow lint
- Change classification

## Stage 2 — Contracts

- OpenAPI lint and bundle
- AsyncAPI validation
- JSON Schema validation
- Example validation
- Breaking-change comparison
- Client/interface generation
- Generated-source compilation

AsyncAPI provides an official CLI validation path, while `oasdiff` can detect potentially breaking OpenAPI changes. ([asyncapi.com](https://www.asyncapi.com/docs/guides/validate))

## Stage 3 — Application checks

Affected modules run:

- Compile
- Unit tests
- Component tests
- Static analysis
- Architecture tests
- Coverage
- Mutation tests where selected

## Stage 4 — Integration tests

- PostgreSQL Testcontainers
- RabbitMQ Testcontainers
- Migration tests
- Outbox/inbox tests
- REST contract tests
- Message contract tests
- Keycloak integration where affected

## Stage 5 — Frontend

- `npm ci`
- Angular compile
- Lint
- Unit/component tests
- API-client generation
- Accessibility checks
- Bundle-budget checks

## Stage 6 — Infrastructure

Untrusted CI performs:

- OpenTofu format
- `init -backend=false`
- Validate
- Static security scan
- Kustomize build
- Helm rendering
- Kubernetes schema validation
- Deprecated-API detection
- Secret detection
- Image-digest policy checks

## Stage 7 — Security

- Dependency review
- CodeQL
- Dependency vulnerability scan
- Container-definition scan
- IaC scan
- License policy
- Secret scan

GitHub dependency review can prevent vulnerable dependencies from being introduced by a pull request, and CodeQL provides code scanning for Java, JavaScript/TypeScript and Actions workflows. ([docs.github.com](https://docs.github.com/en/code-security/concepts/supply-chain-security/dependency-review?ref=thestack.technology))

## Stage 8 — Required gate

A final `ci/required` job:

- Depends on every applicable required job.
- Runs with `if: always()`.
- Fails if a required job failed or was unexpectedly skipped.
- Is the stable branch-ruleset check.

---

# 14. Main build pipeline

A merge to `main` triggers:

1. Full trusted change classification.
2. Re-execution of required tests.
3. Production-mode compilation.
4. Artifact generation.
5. Container-image creation.
6. Vulnerability scanning.
7. SBOM creation.
8. Image signing.
9. Provenance attestation.
10. Publication to GHCR.
11. Promotion pull-request creation.

Only `main` or a protected release tag may publish deployable images.

---

# 15. Multi-architecture image production

Produce:

- `linux/arm64`
- `linux/amd64`

GitHub provides ARM64 and x64 Linux-hosted runners, including `ubuntu-24.04-arm`. ([docs.github.com](https://docs.github.com/en/actions/reference/runners/github-hosted-runners))

Process:

1. Build the application artifact once.
2. Record its SHA-256 hash.
3. Build architecture-specific images from the same artifact.
4. Scan each image.
5. Publish temporary architecture tags.
6. Create the OCI multi-architecture index.
7. Resolve the final index digest.
8. Sign and attest the final digest.
9. Remove temporary tags according to retention policy.

QEMU emulation is not used for official release builds unless native ARM runners become unavailable and a reviewed exception is recorded.

---

# 16. Image naming

Examples:

```text
ghcr.io/panospao7/ev-booking-account-service
ghcr.io/panospao7/ev-booking-booking-session-service
ghcr.io/panospao7/ev-booking-bff
ghcr.io/panospao7/ev-booking-charger-simulator
```

Tags:

- `sha-<12-character-commit>`
- `platform-v<semver>` for platform releases
- `pr-<number>-<sha>` only for disposable preview use

Deployment manifests use:

```text
repository/image:tag@sha256:<digest>
```

Tags improve readability; the digest determines deployed content.

GHCR publication uses the repository-scoped `GITHUB_TOKEN`, not a personal access token. ([docs.github.com](https://docs.github.com/en/packages/managing-github-packages-using-github-actions-workflows/publishing-and-installing-a-package-with-github-actions?learn=continuous_deployment&learnProduct=actions))

---

# 17. Software supply-chain evidence

Every release image receives:

- OCI metadata labels
- SPDX or CycloneDX SBOM
- Dependency inventory
- Vulnerability scan result
- GitHub build-provenance attestation
- SBOM attestation
- Keyless Cosign signature
- Source commit
- Workflow identity
- Build timestamp
- Architecture list

GitHub artifact attestations associate provenance with the repository, workflow, commit and triggering event and can also carry SBOM attestations. ([docs.github.com](https://docs.github.com/en/enterprise-cloud%40latest/actions/concepts/security/artifact-attestations))

Sigstore keyless signing binds an ephemeral signing key to an OIDC identity and records signing evidence through its trust infrastructure. ([docs.sigstore.dev](https://docs.sigstore.dev/cosign/signing/overview/))

Images are signed by digest, not by mutable tag.

---

# 18. Admission verification

Deploy the Sigstore Policy Controller in two stages.

## Stage 1 — Audit

- Verify custom application images.
- Record invalid or absent provenance.
- Do not block deployment.
- Validate GitHub repository and workflow identities.

## Stage 2 — Enforce

After the proof of concept:

- Reject unsigned custom images.
- Reject images without valid provenance.
- Reject images built outside approved workflows.
- Reject images not referenced by digest.
- Exempt only explicitly approved infrastructure images.

GitHub documents enforcement of artifact attestations through the Sigstore Policy Controller. ([docs.github.com](https://docs.github.com/en/actions/concepts/security/kubernetes-admissions-controller))

---

# 19. Promotion pull requests

After a successful main build, automation creates:

```text
promotion/reference-<commit>
```

The pull request updates:

- Image digest
- Release identifier
- Migration Job identifier
- Expected contract version
- Release verification Job
- Deployment metadata

Use a dedicated GitHub App with only:

- Contents write
- Pull requests write
- Metadata read

A GitHub App token is preferred because automation-created pull requests can trigger CI normally; ordinary `GITHUB_TOKEN` events have recursion restrictions. ([docs.github.com](https://docs.github.com/en/actions/concepts/security/github_token))

Promotion pull requests cannot modify application source.

CI rejects a promotion PR containing unrelated files.

---

# 20. Promotion checks

A promotion PR must pass:

1. Image digest exists.
2. Multi-architecture index contains ARM64 and AMD64.
3. Signature is valid.
4. Provenance identifies the approved repository/workflow.
5. SBOM exists.
6. No blocking vulnerability exists.
7. Migration compatibility is valid.
8. Kubernetes manifests render.
9. Network and security policies remain valid.
10. Resource limits exist.
11. Image digest matches the release manifest.
12. Source commit passed required CI.

---

# 21. Flux deployment

After promotion merge:

1. Flux detects the `main` change.
2. Infrastructure dependencies reconcile.
3. Migration Jobs run.
4. Application Deployments roll out.
5. Kubernetes health checks execute.
6. Release verification Job runs.
7. Flux reports reconciliation status to GitHub.

Flux can associate reconciliation results with Git commits and update GitHub commit status without granting GitHub Actions access to the cluster. ([fluxcd.io](https://fluxcd.io/flux/monitoring/alerts/))

The Flux GitHub App requires only:

- Repository contents read
- Commit statuses write
- Metadata read

---

# 22. Database migration pipeline

## 22.1 Pull request

For every changed migration set:

- Verify filenames/order.
- Verify applied migrations are unchanged.
- Migrate an empty database.
- Upgrade from the previous supported release.
- Run service integration tests.
- Validate constraints and indexes.
- Run rollback-compatibility analysis.
- Detect destructive SQL.
- Produce a migration report.

## 22.2 Deployment

The same application image digest is used for:

- Migration Job
- Runtime Deployment

The Migration Job:

- Runs a migration-only entry point.
- Uses the migrator database role.
- Runs before application rollout.
- Is unique per service and release.
- Is idempotent through Flyway history.
- Blocks deployment on failure.

Runtime Pods:

- Use runtime credentials.
- Have Flyway execution disabled.
- Cannot perform DDL.

## 22.3 Contract migrations

Removing a column, table, state or index requires:

1. Expand release
2. Data migration/backfill
3. Compatible application deployment
4. Verification that old versions are absent
5. Separate contract release

Automatic down migrations are prohibited.

---

# 23. Contract delivery

Contract source files are authoritative.

CI produces:

- Bundled OpenAPI documents
- Bundled AsyncAPI documents
- JSON Schemas
- Generated Angular clients
- Generated Java interfaces/DTOs
- Human-readable documentation
- Compatibility report
- Contract examples

Generated sources are normally not committed.

The build must regenerate them deterministically.

A generated-output hash difference from an identical source contract fails reproducibility verification.

Breaking contracts require:

- New major contract version
- Migration plan
- Consumer impact analysis
- Recorded approval

---

# 24. Infrastructure pipeline

## 24.1 Pull request

Runs without cloud credentials:

- Format
- Validate
- Static security scan
- Provider lock verification
- Module tests
- Manifest rendering
- Cost-delta estimation where possible

## 24.2 Trusted plan

After merge to `main`, a protected workflow creates an authoritative OpenTofu plan using:

- The exact merged commit
- Remote state
- Protected environment secrets
- Read-only or minimally scoped provider credentials where possible

The plan artifact contains:

- Plan hash
- Commit
- Tool/provider versions
- Resource summary
- Cost estimate
- Expiry

Sensitive values are removed from displayed output.

## 24.3 Apply

Apply requires:

- Manual `workflow_dispatch`
- Protected GitHub environment
- Owner approval
- Exact unexpired plan hash
- Concurrency lock
- No unreviewed source change

GitHub environments can protect secrets, require approval and restrict deployment branches; concurrency groups prevent overlapping deployments. ([docs.github.com](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/control-deployments))

## 24.4 Cluster bootstrap

Initial K3s bootstrap and full disaster recovery run from a controlled administrative workstation over the approved network path.

The public repository does not receive a persistent infrastructure-connected runner.

---

# 25. GitHub environments

Define:

- `release`
- `reference-infrastructure-plan`
- `reference-infrastructure-apply`
- `reference-promotion`
- `production-infrastructure-plan`
- `production-infrastructure-apply`
- `production-promotion`

Rules:

- Only protected branches/tags
- Required approval for apply/release
- Environment-specific secrets
- One deployment at a time
- No secret access before approval
- Deployment history retained

Actual production environments remain disabled until production readiness.

---

# 26. Release versioning

Use Semantic Versioning for the platform:

```text
platform-v0.1.0
platform-v0.2.0
platform-v1.0.0
```

Before implementation readiness, versions remain `0.x`.

Each platform release contains:

- Platform version
- Git commit
- Image digests
- Contract versions
- Database migration versions
- Infrastructure revision
- SBOM references
- Attestation references
- Test-evidence references
- Known limitations
- Rollback compatibility

Individual services are identified operationally by:

- Platform release
- Git commit
- Image digest

Independent service SemVer is deferred until services require genuinely independent external release cycles.

---

# 27. Release workflow

A platform release requires:

1. Reference deployment succeeded.
2. Required E2E tests passed.
3. Security and accessibility gates passed.
4. Migration report passed.
5. No blocking vulnerability exists.
6. Release evidence is complete.
7. Release manifest is generated.
8. Owner approves the `release` environment.
9. Protected tag is created.
10. GitHub Release is published.
11. Release image tags are attached to existing digests.
12. Release evidence is archived.

Artifacts are never rebuilt from the release tag.

The release refers to the exact images already tested in the reference environment.

---

# 28. Rollback

## 28.1 Application rollback

Create an expedited pull request reverting image digests to a previously approved release.

Requirements:

- Previous image remains available.
- Current database schema is backward compatible.
- Rollback manifest passes security and schema checks.
- Flux performs the rollout.
- Verification Job runs.

## 28.2 Database incompatibility

If the previous application cannot run against the current schema:

- Do not deploy it.
- Apply a forward corrective release.
- Preserve data.
- Record the incident.

## 28.3 GitOps emergency change

Direct cluster changes require break-glass authorization.

Any emergency direct change must be:

- Time-limited
- Audited
- Backported into Git immediately, or
- Explicitly reverted

---

# 29. Dependency automation

Use Dependabot for:

- Maven
- npm
- GitHub Actions
- Container references
- OpenTofu providers

Configuration:

- Weekly grouped ordinary updates
- Immediate security updates
- Separate Spring, Angular, Keycloak and infrastructure updates
- Maximum open-PR limit
- No automatic major-version merge
- No automatic framework-minor merge without tests
- GitHub Actions references kept pinned to commit SHA

GitHub recommends dependency review and Dependabot for maintaining workflow dependencies securely. ([docs.github.com](https://docs.github.com/en/actions/reference/security/secure-use?learn=getting_started&learnProduct=actions))

---

# 30. Vulnerability policy

| Severity | Normal remediation target |
|---|---:|
| Critical | 24 hours |
| High | 7 days |
| Medium | 30 days |
| Low | 90 days |

A vulnerability blocks release when:

- It is Critical or High and applicable.
- It affects authentication, allocation, privacy or supply-chain controls.
- It is known to be actively exploitable.
- No compensating control is verified.

Exceptions require:

- Risk owner
- Justification
- Compensating controls
- Expiry date
- Tracking issue

Secret findings have no ordinary exception path.

---

# 31. Caching

Permitted caches:

- Maven repository
- npm cache
- OpenAPI/AsyncAPI tool downloads
- Buildpack layers
- Test-browser downloads

Rules:

- Keys include lockfile/toolchain hashes.
- Fork PRs cannot populate trusted release caches.
- Privileged builds do not trust executable artifacts from untrusted workflows.
- Caches contain no credentials.
- Cache misses never change build correctness.
- Release packaging verifies dependency locks.

---

# 32. Artifact retention

| Artifact | Retention |
|---|---:|
| Pull-request reports | 14 days |
| Main build reports | 90 days |
| Nightly reports | 30 days |
| Performance reports | 180 days |
| Security reports | 180 days |
| Release evidence | Supported release lifetime plus 1 year |
| SBOM/provenance/signatures | Same as release image |
| Recovery-drill evidence | 1 year |
| Disposable PR images | 14 days |
| Unreleased main images | 90 days |
| Supported release images | Entire support lifetime |
| Previous rollback images | Minimum 180 days |

Release evidence is additionally copied to protected object storage where GitHub retention is insufficient.

---

# 33. Scheduled workflows

## Nightly

- Full monorepo build
- Full integration suite
- Migration upgrade suite
- Contract regeneration
- Architecture rules
- Container build smoke test
- Secret/dependency scan

## Weekly

- Allocation concurrency suite
- Property-based suite
- CodeQL full analysis
- Dependency inventory
- Image rebuild reproducibility
- Kubernetes manifest validation
- Documentation link scan

## On demand

- Performance tests
- Resilience tests
- Infrastructure plan
- Reference promotion
- Release
- Recovery validation

Performance and destructive resilience tests do not run against production automatically.

---

# 34. Test and build evidence

Every trusted build records:

- Repository
- Commit
- Branch/tag
- Workflow and run ID
- Runner architecture
- Java/Node/Maven/npm versions
- Dependency-lock hashes
- Contract hashes
- Database migration versions
- Test results
- Coverage
- Security findings
- Image digests
- SBOM digest
- Signature and attestation references

GitHub artifact attestations allow consumers to verify container-image provenance against the source repository. ([docs.github.com](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations))

---

# 35. Required repository settings

1. Enable repository rulesets.
2. Protect `main`.
3. Protect `platform-v*` tags.
4. Require `ci/required`.
5. Require pull requests.
6. Require signed commits.
7. Require linear history.
8. Block force pushes.
9. Block deletion.
10. Enable secret scanning.
11. Enable push protection.
12. Enable dependency graph.
13. Enable dependency review.
14. Enable CodeQL.
15. Set default workflow permission to read-only.
16. Restrict allowed Actions.
17. Disable Actions from approving their own pull requests.
18. Enable automatic branch deletion.
19. Configure GHCR package permissions.
20. Configure protected environments.

Push protection can block detected secrets before they are committed and supports controlled bypass review. ([docs.github.com](https://docs.github.com/en/code-security/concepts/secret-security/bypass-requests))

---

# 36. Pipeline concurrency

Use concurrency groups:

```text
pr-{pull-request-number}
main-{branch}
promotion-reference
infra-reference
release
```

Rules:

- Superseded PR runs are cancelled.
- Main publication is serialized per commit.
- Only one environment promotion runs at a time.
- Infrastructure applies never overlap.
- Release workflows never overlap.
- Migration Jobs serialize per logical database.

---

# 37. Failure behaviour

## CI failure

- Merge blocked
- Evidence retained
- No artifact promoted

## Main packaging failure

- No promotion PR
- Existing deployment unchanged

## Signing/attestation failure

- Image cannot be promoted
- Unsigned image remains non-deployable

## Promotion failure

- GitOps state unchanged
- Existing release continues

## Flux failure

- Commit status fails
- Alert generated
- Existing healthy workloads retained where Kubernetes rollout allows

## Migration failure

- Application rollout blocked
- Previous application remains active
- Forward correction required

## Post-deployment verification failure

- Deployment marked failed
- Automatic rollback is not performed blindly
- Operator selects rollback or forward fix based on schema compatibility

---

# 38. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-CICD-01 | Use one monorepo for code, contracts, infrastructure and documentation. |
| ARC-CICD-02 | Use trunk-based development with short-lived branches. |
| ARC-CICD-03 | Protect `main` and require pull requests. |
| ARC-CICD-04 | Use squash merging and Conventional Commit-style PR titles. |
| ARC-CICD-05 | Use GitHub Actions for CI, packaging and controlled infrastructure operations. |
| ARC-CICD-06 | Use GitHub-hosted runners for public-repository CI. |
| ARC-CICD-07 | Prohibit persistent self-hosted runners for untrusted public-repository workflows. |
| ARC-CICD-08 | Pin every external Action to a full commit SHA. |
| ARC-CICD-09 | Default `GITHUB_TOKEN` permissions to none and grant per job. |
| ARC-CICD-10 | Never expose deployment secrets to pull-request workflows. |
| ARC-CICD-11 | Build and publish deployable images only from `main` or protected release tags. |
| ARC-CICD-12 | Produce ARM64 and AMD64 OCI images. |
| ARC-CICD-13 | Deploy images by immutable digest. |
| ARC-CICD-14 | Generate SBOM, signatures and provenance for every release image. |
| ARC-CICD-15 | Use a dedicated GitHub App to create promotion pull requests. |
| ARC-CICD-16 | Use Flux rather than GitHub Actions for Kubernetes deployment. |
| ARC-CICD-17 | Report Flux reconciliation through GitHub commit status. |
| ARC-CICD-18 | Run database migrations as pre-rollout Jobs. |
| ARC-CICD-19 | Use the application image digest for both migration and runtime execution. |
| ARC-CICD-20 | Prohibit automatic down migrations. |
| ARC-CICD-21 | Use protected environment approval for infrastructure apply and release. |
| ARC-CICD-22 | Use Dependabot for dependency automation. |
| ARC-CICD-23 | Retain release evidence for the supported release lifetime plus one year. |
| ARC-CICD-24 | Use promotion pull requests for reference and future production environments. |
| ARC-CICD-25 | Introduce image-attestation admission enforcement after an audit-mode proof of concept. |

---

# 39. Open questions

| ID | Question | Resolution |
|---|---|---|
| ARC-CICD-OQ-01 | Final GitHub App identity and permissions | Repository setup |
| ARC-CICD-OQ-02 | Final container scanner | Implementation setup |
| ARC-CICD-OQ-03 | Final SBOM format or dual SPDX/CycloneDX output | Supply-chain proof of concept |
| ARC-CICD-OQ-04 | Multi-architecture Buildpacks implementation details | Packaging proof of concept |
| ARC-CICD-OQ-05 | Final admission-policy rules | Security proof of concept |
| ARC-CICD-OQ-06 | Final mutation-test schedule | Pipeline performance testing |
| ARC-CICD-OQ-07 | Final artifact-storage cost and retention | Operations review |
| ARC-CICD-OQ-08 | Whether merge queue becomes useful with additional contributors | Repository growth |
| ARC-CICD-OQ-09 | Final production promotion reviewers | Production readiness |
| ARC-CICD-OQ-10 | Final off-GitHub repository mirror | Disaster-recovery setup |
| ARC-CICD-OQ-11 | Exact infrastructure-plan sanitization | IaC proof of concept |
| ARC-CICD-OQ-12 | Whether preview environments justify their cost | Implementation review |

---

# 40. Acceptance criteria

This strategy is approved when:

1. Every deployable has an identified build path.
2. Every contract has a validation and compatibility path.
3. Pull-request code receives no deployment secrets.
4. Main cannot be changed through direct push.
5. Required CI can block merge.
6. Release artifacts originate only from trusted workflows.
7. Every deployed image uses a digest.
8. Every release image has an SBOM, signature and provenance.
9. Database migrations are verified before rollout.
10. Runtime services cannot perform DDL.
11. GitHub Actions does not directly manage ordinary Kubernetes deployments.
12. Flux reports deployment status to GitHub.
13. Infrastructure apply requires explicit approval.
14. Rollback preserves database compatibility.
15. Dependency and security updates are automated but gated.
16. Release evidence maps to requirements and tests.
17. No persistent public-repository runner exposes infrastructure access.
18. A full main-to-Flux deployment proof of concept succeeds.

---

# 41. Consequences

## Positive

- Atomic cross-service changes
- Strong supply-chain traceability
- No direct push-based cluster deployment
- Immutable and verifiable images
- Consistent quality gates
- Controlled schema migrations
- Low secret exposure
- Clear release and rollback history
- Practical workflow for an individual developer

## Negative

- Monorepo CI requires path-classification logic.
- Multi-architecture builds increase pipeline time.
- Promotion pull requests add an extra merge.
- Signing and attestation add operational complexity.
- Protected infrastructure plans require manual approval.
- Full concurrency and security suites may be slow.
- One repository contains a broad security boundary.

These costs are accepted to make every implementation and deployment reproducible, reviewable and attributable.

---

# 42. Next architecture artifact

The next document is:

**Implementation Epics and Dependency Roadmap v1.0**

It must define:

- Epic order
- Vertical slices
- Architecture-enabler work
- Dependency graph
- Proofs of concept
- Milestones
- Release increments
- Exit criteria
- Parallelizable work
- Risk-first implementation order
- Critical path
- Definition of ready
- Definition of done
