Document ID: REQ-002
Title: Non-Functional Requirements v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA/SA/PA/QA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: SCP-001
Authoritative for: Performance, Reliability, Security, and Quality Targets

---

## Non-Functional Requirements v1.0

Targets apply under a reference load of **500 concurrent users, 2,000 simulated EVSEs, 50 API requests/second and 100 charger events/second**.

### Performance

- Search and station APIs: p95 ≤ 1.5 seconds.
- Availability calculation: p95 ≤ 1 second.
- Booking/check-in commands: p95 ≤ 2 seconds.
- 99% of charger events reflected within 10 seconds.
- Web Core Vitals meet “Good” thresholds at the 75th percentile.
- External map/email-provider latency is measured separately.

### Reliability and availability

- Core platform monthly availability target: **99.5%**, excluding announced maintenance.
- No acknowledged booking may be lost.
- Double-booking prevention is a correctness invariant, regardless of load.
- Search, analytics and email failures must not prevent existing booking management.
- Uncertain charger outcomes remain visible until reconciled.
- Capacity tests must validate at least twice the reference load.

### Recovery and durability

- Recovery Point Objective: ≤ 5 minutes.
- Recovery Time Objective: ≤ 60 minutes.
- Encrypted backups and PostgreSQL point-in-time recovery.
- Automated backup checks and quarterly restoration tests.
- Outbox events remain recoverable after service or broker failure.

### Security

- OWASP ASVS Level 2 used as the application-security baseline.
- TLS for all network communication and encryption at rest.
- MFA mandatory for privileged users.
- Default-deny authorization on every protected request.
- Central secrets management; no secrets in code, images or logs.
- Rate limiting, account-abuse controls and dependency/container scanning.
- Critical vulnerabilities mitigated within 24 hours; high-risk findings within 7 days.

### Privacy and accessibility

- Data minimization, configurable retention and auditable export/deletion.
- Personal data excluded from telemetry and ordinary application logs.
- WCAG 2.2 AA target, including keyboard-only operation.
- Greek and English interfaces.
- UTC storage with `Europe/Athens` presentation.

### Operability and maintainability

- Structured logs, metrics and distributed traces with correlation IDs.
- Alerts for booking failures, stale chargers, queue backlog and reconciliation failures.
- Health/readiness endpoints for every service.
- Versioned APIs/events and backward-compatible migrations.
- Automated unit, integration, contract, security, accessibility and end-to-end tests.
- Critical booking and authorization rules require complete scenario coverage.
- Deployments must support rollback without corrupting committed data.

