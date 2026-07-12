Document ID: REQ-002
Title: Non-Functional Requirements v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA/SA/PA/QA
Last reviewed: 2026-07-12
Supersedes: None
Depends on: SCP-001
Authoritative for: Performance, Reliability, Security, and Quality Targets

---

## Non-Functional Requirements v1.0

Targets apply under a reference load of **500 concurrent users, 2,000 simulated EVSEs, 50 API requests/second and 100 charger events/second**.

### Performance

- Search and station APIs: p95 ≤ 1.5 seconds. (Release applicability: W1)
- Availability calculation: p95 ≤ 1 second. (Release applicability: W1)
- Booking/check-in commands: p95 ≤ 2 seconds. (Release applicability: W1)
- **Start Charging Latency (Asynchronous phase split):**
  1. *API intent accepted latency:* p95 ≤ 1 second (time to record transaction intent and enqueue command) (Release applicability: W1)
  2. *Command dispatched latency:* p95 ≤ 2 seconds (time from API acceptance to dispatch to device connection) (Release applicability: W1)
  3. *Physical/simulated charging confirmed latency:* p95 ≤ 10 seconds (time from dispatch to physical charging confirmation) (Release applicability: W1)
- 99% of charger events reflected within 10 seconds. (Release applicability: W1)
- Web Core Vitals meet “Good” thresholds at the 75th percentile. (Release applicability: W1)
- External map/email-provider latency is measured separately. (Release applicability: W1)

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

### Dependency-Outage Resilience Matrix
To satisfy reliability requirements, platform operations must behave as follows when downstream services are offline:

| Operational Request | Downstream Dependency | Fail-Safe Behavior |
|---|---|---|
| View/Cancel Booking | Discovery & Insights | **Succeeds**: Operates directly against the Booking database. |
| Create Booking Hold | Booking and Session | **Fails Closed**: Blocked if local config/enforcement projections are stale or unavailable. |
| Check-In | Booking and Session | **Succeeds**: Uses Booking-local cached configuration and device projections. |
| Start Charging | Booking and Session | **Succeeds**: Dispatches asynchronous command to Device Integration. |
| Station Operations Offline | Booking and Session | **Succeeds**: Existing booking and charging operations continue if local projections are valid. |
| Event Publishing | RabbitMQ Broker | **Transaction Succeeds**: Business commit completes; outbox event remains pending/retryable. |
| Send Notification | Email Provider | **Transaction Succeeds**: Business commit completes; notification delivery remains pending/retryable. |
| Simulator Status Update | Device Integration | **Transaction Succeeds**: Live status and heartbeats buffered; state reconciled on reconnect. |
