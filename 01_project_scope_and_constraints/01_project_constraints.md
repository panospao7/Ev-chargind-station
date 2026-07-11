Agreed. We’ll record these as **provisional architectural decisions**:

## Project Constraints v0.1

- **Team:** Individual project
- **Deadline:** No fixed deadline
- **Budget:** Prefer free or very-low-cost hosting
- **Primary goal:** Production-quality portfolio project using commercially relevant technologies
- **Frontend:** Angular + TypeScript
- **Backend:** Java + Spring Boot
- **Architecture:** Microservices
- **Alternative architecture:** Modular monolith designed and documented, but not fully implemented
- **Cloud:** Not selected yet
- **Diagrams:** Required
- **Source code and documentation:** Organized as production deliverables
- **Implementation:** Begins only after foundational planning passes a readiness review

## What “design both” will include

For the **modular monolith**, we will produce:

- Module boundaries
- Component and deployment diagrams
- Shared-database design
- Internal communication approach
- Expected advantages and limitations

For the **microservices system**, we will produce:

- Service boundaries and responsibilities
- Database ownership
- API and event contracts
- Deployment architecture
- Failure-handling and consistency strategies
- Security and observability architecture
- Full implementation plan

We’ll also create an Architecture Decision Record explaining why microservices were selected despite their greater cost and complexity.

We should **not decide the services yet**. Service boundaries must emerge from product capabilities and consistency rules—not from technical preferences.

## Next: establish the system boundary

We need to decide:

1. Will station and availability information be simulated, manually managed, or obtained from real external providers?
2. Are payments included or excluded?
3. Will station operators have their own management interface?
4. Will administrators have a separate administration interface?
5. Does a driver reserve an exact connector, a charger, or any compatible connector at a station?
6. Are bookings fixed-duration slots or arbitrary start/end times?
7. Do we model actual charging sessions, or only reservations?
8. Should availability include both planned bookings and real-time charger status?
9. Are email, SMS, or in-app notifications desired?
10. Is the initial geographical scope Greece, Europe, or location-independent?

Once these are answered, we can create **System Scope v1**, define what is explicitly excluded, and then identify actors and use cases.