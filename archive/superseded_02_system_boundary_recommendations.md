## Recommended system boundary

1. **Station data — simulated**
   - Seed realistic Greek stations, coordinates, EVSEs, connector types, power and tariffs.
   - Add a **charging-station simulator** that changes operational status and emits session/meter events.
   - Use a real map, but not scraped charger data.
   - Keep an integration boundary so real OCPP/OCPI data could replace the simulator later.

2. **Payments — exclude real money from MVP**
   - Include tariffs, estimated cost and payment status concepts.
   - Do not store cards or process real charges.
   - Later, add a payment provider in test/sandbox mode.
   - A real public-charging product in the EU would need to consider ad-hoc payment requirements. ([eur-lex.europa.eu](https://eur-lex.europa.eu/eli/reg/2023/1804/2026-01-08?utm_source=openai))

3. **Operator interface — yes**
   Operators manage their own:
   - Stations, EVSEs and connectors
   - Opening hours and tariffs
   - Maintenance and status overrides
   - Bookings and simulated hardware

4. **Administrator interface — yes**
   Admins manage users, operators, platform-wide stations, disputes, audit records and system configuration. Initially, driver, operator and admin areas can exist in **one Angular application** with role-protected routes—not three separate frontends.

5. **Reserve an EVSE, not an individual cable**
   Use this hierarchy:

   **Station → EVSE/recharging point → connector options**

   An EVSE can have multiple connector plugs, but only one may be used simultaneously. Therefore, the booking locks the EVSE while recording the required connector type. The UI should automatically assign a compatible EVSE, while optionally allowing exact selection. ([eur-lex.europa.eu](https://eur-lex.europa.eu/legal-content/EN/ALL/?uri=oj%3AL_202500655))

6. **Hybrid booking times**
   - Start times in 15-minute increments
   - Duration adjustable in 15-minute increments
   - Operator-configurable minimum and maximum duration
   - Grace period for late arrival
   - Optional turnaround buffer

   This is more flexible than rigid slots without allowing problematic minute-level intervals.

7. **Include simulated charging sessions**
   A reservation is planned access; a session is actual usage: started, charging, paused, completed, energy consumed and final duration. Lightweight simulated sessions are important for realistic availability and analytics. OCPP also treats EVSE reservations and charging transactions/meter readings as distinct capabilities. ([openchargealliance.org](https://openchargealliance.org/wp-content/uploads/2025/03/ocpp_2_x_minimal_footprint-v14.pdf))

8. **Availability**
   Combine bookings, active sessions, maintenance, operational status and status freshness.

9. **Notifications**
   Post-MVP: email and in-app. Exclude SMS initially.

10. **Geography**
   Greece-first, EUR, kilometres and `Europe/Athens`; architecture remains expandable internationally.

Unless you object, these become **System Scope v1**.
