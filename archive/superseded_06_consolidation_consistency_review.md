## Consolidation & Consistency Review v1.0

No blocking contradictions were found. The design is coherent, with these clarifications:

### Confirmed baseline

- Angular + TypeScript frontend
- Java + Spring Boot microservices
- Documented but unimplemented modular-monolith alternative
- Greece-first platform using EUR and `Europe/Athens`
- Driver, operator, administrator, support and device actors
- Station → EVSE → connector hierarchy
- EVSE-level reservations
- Separate booking and charging-session lifecycles
- Simulated stations, chargers, telemetry and failures
- Real payments, real hardware control and OCPP compliance excluded

### Resolved inconsistencies

1. **Notifications:** transactional email is now core; in-app and SMS remain deferred.
2. **Operational claim:** the system will be operational for simulated charging—not production-ready for public physical chargers.
3. **Costs:** all tariff calculations remain labelled estimates; no successful-payment claims are made.
4. **Device protocol:** use a versioned, OCPP-inspired subset without claiming compliance. The exact OCPP reference version will be selected during contract design.
5. **Availability authority:** search is advisory; the Booking service performs the authoritative allocation check.
6. **Interrupted charging:**
   - Energy delivered → booking `COMPLETED` with interrupted outcome.
   - Charging never began → `FULFILMENT_FAILED`.
7. **Status separation:** administrative state, reported device state and derived availability remain distinct.
8. **Maintenance:** planned maintenance cannot activate until affected bookings are resolved. Audited emergency maintenance is the exception.
9. **Consistency:** booking allocation is strongly consistent; search, analytics, notifications and dashboards are eventually consistent.

### Open technical decisions

Recommended defaults:

- Identity provider: **Keycloak**
- Message broker: **RabbitMQ**, simpler than Kafka for this project
- Primary database: **PostgreSQL**
- Maps: **MapLibre with OpenStreetMap data**
- Cache/short-lived data: **Redis only where justified**
- Displayed tariff: gross EUR estimate with a versioned tax snapshot
- Retention periods: provisional until legal review

Microservice boundaries and database ownership remain intentionally undecided until the requirements are consolidated.

## Next task

Create the **Consolidated Functional Requirements Catalogue and Traceability Matrix**, followed by measurable non-functional requirements and then final microservice boundary design.