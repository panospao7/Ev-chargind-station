For a realistic system, we should implement a complete **operational core**, without attempting every commercial feature.

## 1. Driver capabilities

- Browse/search stations without an account
- Register, verify email, sign in and reset password
- Manage profile and vehicle/connector compatibility
- View map, station details, tariffs and live/planned availability
- Reserve an automatically assigned or selected EVSE
- View, modify or cancel eligible bookings
- Check in and start/stop a simulated charging session
- View live duration, energy and estimated cost
- View booking/session history
- Report station faults
- Export or request deletion of personal data

## 2. Station operator

- Manage an operator organization and staff
- Create/edit stations, EVSEs and connectors
- Configure hours, tariffs, booking rules and buffers
- Schedule maintenance and mark equipment unavailable
- Monitor charger connectivity, faults and sessions
- View bookings for owned stations
- Perform audited operational overrides
- View utilization, energy and cancellation analytics

## 3. Platform administrator

- Approve and suspend operators
- Manage users, roles and platform configuration
- Investigate bookings, faults and disputes
- Moderate stations
- View audit logs and system health
- Use exceptional “break-glass” actions only with mandatory justification

## 4. Charger simulator

It should emulate an **OCPP 2.0.1-inspired subset**, without falsely claiming full protocol compliance:

- Boot/register and send heartbeats
- Become online, offline or stale
- Report EVSE status and faults
- Accept/reject reservation and start commands
- Emit transaction and meter-value events
- Simulate charging, interruption and completion
- Reconnect and resend queued events

OCPP defines communication between charging stations and central management systems, including transactions, status, remote control and security. ([openchargealliance.org](https://openchargealliance.org/protocols/open-charge-point-protocol/))

## 5. Background processes

- Expire booking holds
- Apply grace-period/no-show rules
- Release reserved capacity
- Detect stale charger status
- Retry events safely and reconcile inconsistencies
- Generate analytics
- Send notifications
- Apply retention and anonymization policies

## 6–8. Security, flows and permissions

- Account required for booking; public browsing allowed
- MFA required for operators/admins
- RBAC plus organization/resource ownership checks
- Drivers access only their records
- Operators access only their organization’s stations
- Simulator accesses only its assigned equipment
- Default-deny permissions and audited privileged actions
- Concurrent bookings: one request succeeds; others receive a conflict
- Offline/stale EVSEs cannot accept new immediate sessions
- All commands use idempotency and safe retries

For real EU operation, we must expose location, connector, power, operational status, availability and pricing data; public deployment would also require transparent ad-hoc payment support. We will model these now but defer real payments. ([eur-lex.europa.eu](https://eur-lex.europa.eu/eli/reg/2023/1804/oj/eng))