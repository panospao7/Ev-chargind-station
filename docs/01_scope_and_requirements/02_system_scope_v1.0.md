Document ID: SCP-002
Title: System Scope v1.0
Version: 1.0
Status: APPROVED
Owner: PO
Last reviewed: 2026-07-12
Supersedes: None
Depends on: SCP-001
Authoritative for: In-Scope, Out-of-Scope, and Deferred Capabilities

---

## System Scope v1

### Included

- Greece-first EV charging booking platform
- Responsive Angular web application
- Driver, operator and administrator interfaces
- Map-based station discovery
- Search and filtering by location, connector, power and availability
- Simulated station and charger data
- Station → EVSE → connector hierarchy
- Reservation of a compatible EVSE
- Optional selection of a specific EVSE
- 15-minute start-time and duration increments
- Configurable booking duration, grace period and turnaround buffer
- Planned availability combined with real-time simulated status
- Booking creation, viewing and cancellation
- Simulated charging-session lifecycle
- Estimated pricing and energy-consumption information
- Operator management of stations, EVSEs, connectors and maintenance
- Administrator platform management
- Authentication and role-based authorization
- Basic analytics and audit logging
- Essential transactional emails (booking holds, confirmations, cancellations, faults, and session issues)
- EUR, kilometres and `Europe/Athens` defaults
- Cloud-native microservices deployment

### Deferred

- SMS, push, in-app notification center, and marketing communications
- Optional reminder emails and campaigns
- Operator applications and organization ownership transfer
- Platform support cases and administrative audit projections
- Privacy export and coordinated deletion workers (local anonymization is included)
- Advanced charger command reconciliation (replayed offline events and automatic queue reconciliation)
- Payment-provider sandbox integration
- Real charging-network integrations
- Internationalization and expansion outside Greece
- Advanced pricing and demand forecasting

### Explicitly excluded from the initial version

- Real card transactions
- Storage of payment-card details
- SMS notifications
- Native Android/iOS applications
- Real charger hardware control
- Scraping station information
- Complete implementation of the alternative monolith
- International currencies and tax systems

## Important domain decision

A booking reserves an **EVSE**, not merely a connector plug. The booking records the connector type the vehicle requires. An EVSE may expose multiple connector options but generally supports only one active vehicle at a time.

## Next planning phase: actors and use cases

We should now define:

1. Driver capabilities
2. Station-operator capabilities
3. Administrator capabilities
4. Simulated charger responsibilities
5. Background-system processes
6. Authentication and account lifecycle
7. Primary, alternative and failure flows
8. Permission boundaries

We should begin with the **Driver actor**, following the complete journey:

**Account creation → station discovery → availability inspection → booking → arrival/check-in → charging session → completion/cancellation → booking history**

For every step, we’ll identify business rules, errors, edge cases and acceptance criteria.

## Canonical Transactional Email Catalogue
This catalogue defines the email categories. Actual template keys are maintained in a separate template registry.
### Template Categories
- **`DriverEmailVerification` (Wave 1):** Sent upon registration containing single-use account verification link. (Template Key: `driver-email-verification`)
- **`DriverPasswordRecovery` (Wave 1):** Sent upon request containing recovery link. (Template Key: `driver-password-recovery`)
- **`BookingConfirmed` (Wave 1):** Sent immediately upon successful `BookingConfirmed` event (not `BookingHeld`). (Template Key: `booking-confirmation`)
- **`BookingCancelled` (Wave 1):** Sent upon booking cancellation. (Template Key: `booking-cancellation`)
- **`SecurityAlertNotification` (Wave 1):** Sent upon password changes, MFA changes, or login locks. (Template Key: `security-alert`)
- **`SupportCaseOpened` (Wave 2):** Sent upon case submission. (Template Key: `support-case-opened`)
- **`DataExportCompleted` (Wave 3):** Sent containing secure download link. (Template Key: `data-export-completed`)
- **`AccountDeletionConfirmed` (Wave 3):** Sent upon coordinated deletion completion. (Template Key: `account-deletion-confirmed`)
