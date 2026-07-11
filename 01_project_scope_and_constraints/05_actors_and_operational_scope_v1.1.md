After reevaluation, the foundation is strong. I recommend **keeping it with the following additions and clarifications**:

1. **Formal lifecycle models**
   - Booking: `HELD → CONFIRMED → CHECKED_IN → ACTIVE → COMPLETED`
   - Alternative outcomes: `CANCELLED`, `EXPIRED`, `NO_SHOW`, `FAILED`
   - Separate charging-session and EVSE-status lifecycles
   - Rescheduling must be atomic so the original booking remains valid if the new time fails.

2. **Availability rules**
   Availability must combine:
   - Opening hours
   - Existing reservations and buffers
   - Active sessions
   - Maintenance
   - Fault, offline and stale-data conditions
   - Connector compatibility  
   
   `UNKNOWN` must be a real status; old status data must never appear as confidently “available.”

3. **Check-in and authorization**
   - Driver scans an EVSE QR code or enters its identifier.
   - Platform confirms ownership, time window and connector compatibility.
   - Simulator accepts or rejects the start command.
   - Anti-abuse rules limit overlapping and repeated abandoned bookings.

4. **Identity lifecycle**
   Add email verification, recovery, session revocation, operator invitations, suspension, account deletion and versioned acceptance of terms/privacy policies. MFA remains mandatory for operator and administrator accounts.

5. **Additional permission roles**
   - Operator owner
   - Operator manager/staff
   - Platform administrator
   - Restricted support/auditor  
   
   Support should not have unrestricted administrator access. All exceptional actions require justification and audit records.

6. **Transactional notifications become foundational**
   Email is required for verification, password recovery, booking confirmation, operator cancellation and major booking changes. SMS and general marketing notifications remain deferred.

7. **Stronger simulator behaviour**
   Include duplicate, delayed and out-of-order events, command rejection, disconnect/reconnect, stale heartbeats and queued event delivery—not only successful charging. OCPP supports transaction events, sequence-based reconstruction and offline behaviour, making these realistic test cases. We should now call it an **OCPP 2.1-inspired subset**, without claiming protocol compliance. ([openchargealliance.org](https://openchargealliance.org/wp-content/uploads/2023/07/new_in_ocpp_201-v10.pdf))

8. **Privacy, security and compliance**
   - Store timestamps in UTC; display using `Europe/Athens`.
   - Minimise and expire location/history data.
   - Use OWASP ASVS 5.0 as our security-requirements baseline. ([edpb.europa.eu](https://www.edpb.europa.eu/system/files/2026-05/edpb-summary-connected-vehicles-mobility-data_en_0.pdf))
   - Model transparent tariff components and tariff snapshots.
   - A real EU public-charging deployment would additionally require ad-hoc payments and publication of specified static/dynamic charging-point data through APIs. ([eur-lex.europa.eu](https://eur-lex.europa.eu/eli/reg/2023/1804/oj/eng?utm_source=openai))

The application will be **fully operational as a reservation and simulated-charging platform**, but not presented as production-ready for physical public chargers until real hardware, payments and regulatory integrations are added.

We can approve this as **Actors and Operational Scope v1.1**.