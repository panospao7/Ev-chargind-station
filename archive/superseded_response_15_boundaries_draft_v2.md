## Final Microservice Boundaries v1.0 — Draft

Recommended scope: **8 business services**, plus Keycloak and an API Gateway. This avoids excessive fragmentation while demonstrating realistic microservice architecture.

### Services and ownership

1. **Account Service**
   - Driver profiles, vehicles, preferences, consent records
   - Account deletion/export workflows
   - Keycloak owns credentials, MFA, login sessions and identity verification.

2. **Network Service**
   - Operator organizations and staff memberships
   - Stations, EVSEs, connectors, tariffs and booking policies
   - Maintenance, faults and operational overrides

3. **Booking Service**
   - Holds, reservations, allocations, check-in and start authorizations
   - Tariff/policy snapshots
   - No-show, cancellation and reassignment state
   - Sole authority for interval capacity and double-booking prevention

4. **Charging Service**
   - Charging sessions, meter readings, start/stop workflows
   - Energy and estimated-cost summaries
   - Command reconciliation and interruption outcomes

5. **Device Gateway**
   - Secure charger WebSockets and machine identities
   - Device registry, heartbeats, status freshness
   - Commands, responses, sequence tracking and offline event queues

6. **Notification Service**
   - Email templates, delivery attempts, retries and provider integration

7. **Query & Reporting Service**
   - Map/search read models
   - Advisory availability summaries
   - Operator and platform analytics
   - Rebuildable from domain events

8. **Platform Governance Service**
   - Support cases, privacy requests, global reference data
   - Security/business audit records and privileged-action review

### Supporting components

- **API Gateway/BFF:** routing, token validation and rate limiting; no domain data
- **Keycloak:** authentication and broad role claims; application services still enforce organization and resource ownership. Keycloak supports roles, organizations and organization membership. ([keycloak.org](https://www.keycloak.org/docs/latest/server_admin/index.html?utm_source=openai))
- **RabbitMQ:** asynchronous domain events with acknowledgements, publisher confirms, outbox/inbox and dead-letter queues. ([blog.rabbitmq.com](https://blog.rabbitmq.com/docs/next/confirms?utm_source=openai))

### Data rules

- Each service exclusively owns its data.
- One PostgreSQL cluster is acceptable initially, but services receive separate databases/schemas and credentials.
- No cross-service SQL joins or foreign keys.
- Booking allocations use PostgreSQL range/exclusion constraints for database-level overlap prevention. ([postgresql.org](https://www.postgresql.org/docs/current/ddl-constraints.html?utm_source=openai))
- Cross-service references use immutable IDs and snapshots.
- Immediate validation uses synchronous APIs; state propagation uses versioned events.
- Admin/support interfaces call the owning services rather than bypassing them.
- We will not create separate Availability, Tariff or Admin services initially.

**Next:** define service-to-service interactions, synchronous APIs, domain events and the major saga workflows.