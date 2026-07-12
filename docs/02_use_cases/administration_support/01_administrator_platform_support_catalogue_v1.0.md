Document ID: UC-AD-001
Title: Administrator and Platform Support Catalogue v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA/SA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: SCP-003
Authoritative for: Admin/Support Access Tiers and Role Boundaries

---

## Administrator and Platform Support Model v1.0

### Platform roles

**Platform Administrator**
- Approves, suspends and closes operator organizations
- Suspends/reactivates users
- Moderates station publication
- Manages platform-wide policies and reference data
- Handles serious incidents and exceptional booking actions
- Reviews security and operational dashboards

**Support Agent**
- Searches support cases and relevant bookings
- Views masked user contact information only when necessary
- Assists with cancellation, reassignment and fulfilment failures
- Escalates faults and disputes
- Cannot manage roles, operators, tariffs or platform configuration

**Auditor/Security Reviewer**
- Read-only access to audit trails, security events and reports
- Cannot alter business data or perform operational actions
- Audit-log administration remains separated from ordinary administration

### Use-case catalogue

- **AD-01:** Review operator applications
- **AD-02:** Suspend/reactivate users or organizations
- **AD-03:** Moderate stations and operators
- **AD-04:** Manage global connector types, cancellation reasons and policy limits
- **AD-05:** Investigate incidents and disputes
- **AD-06:** Perform emergency booking/session intervention
- **AD-07:** Review audit and security events
- **AD-08:** Process privacy export/deletion requests
- **AD-09:** View platform-wide operational analytics
- **SUP-01:** Create and manage support cases
- **SUP-02:** Assist with bookings and equipment failures
- **SUP-03:** Escalate technical or security incidents

Support case lifecycle:

`OPEN → IN_PROGRESS → WAITING_FOR_USER/OPERATOR → RESOLVED → CLOSED`

### Permission rules

- Authorization combines role, action, resource scope and case assignment.
- Access is denied by default and checked by every service—not only the API Gateway.
- Support agents receive temporary access only to data required for an assigned case.
- Sensitive fields are masked unless explicitly revealed with a recorded reason.
- Direct database access is never part of an application role.
- Administrators cannot silently impersonate users.
- Platform administration does not automatically grant cloud, database or identity-provider administration.
- Destructive actions use suspension/deactivation rather than hard deletion.
- Emergency “break-glass” access is time-limited, MFA-protected, justified, alerted and reviewed.
- Every privileged action records actor, target, reason, correlation ID and before/after values.
- Audit records cannot be edited through application APIs and must exclude passwords, tokens and unnecessary personal data.

These controls follow least privilege, default-deny, per-request authorization and separation-of-duty guidance. ([cheatsheetseries.owasp.org](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html))
