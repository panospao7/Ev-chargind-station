Document ID: UC-DR-001
Title: Driver Use-Case Catalogue v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: SCP-003
Authoritative for: Driver Journey Use Cases and Priority Index

---

## Driver Use-Case Catalogue v1.0 — Draft

### Discovery

| ID | Use case | Priority |
|---|---|---|
| DR-01 | Browse station map/list without signing in | Core |
| DR-02 | Search by location and map area | Core |
| DR-03 | Filter by connector, power, availability and price | Core |
| DR-04 | View station, EVSE, tariff and operational details | Core |
| DR-05 | Inspect availability for a selected date/time | Core |

### Account and vehicles

| ID | Use case | Priority |
|---|---|---|
| DR-06 | Register and verify email | Core |
| DR-07 | Sign in, sign out and recover account | Core |
| DR-08 | Manage profile and saved vehicles | Core |
| DR-09 | Manage connector compatibility preferences | Core |
| DR-10 | Revoke active login sessions | Secondary |

### Reservations

| ID | Use case | Priority |
|---|---|---|
| DR-11 | Reserve an automatically assigned compatible EVSE | Core |
| DR-12 | Select and reserve a specific EVSE | Core |
| DR-13 | View upcoming booking details | Core |
| DR-14 | Atomically reschedule a booking | Core |
| DR-15 | Cancel an eligible booking | Core |
| DR-16 | Check in using QR code or EVSE identifier | Core |

### Charging sessions

| ID | Use case | Priority |
|---|---|---|
| DR-17 | Start an authorized simulated session | Core |
| DR-18 | Monitor status, duration, energy and estimated cost | Core |
| DR-19 | Stop a charging session | Core |
| DR-20 | View completed session summary | Core |

### Records, support and privacy

| ID | Use case | Priority |
|---|---|---|
| DR-21 | View booking and charging history | Core |
| DR-22 | Report a station or EVSE fault | Secondary |
| DR-23 | Manage notification preferences | Secondary |
| DR-24 | Export personal data | Secondary |
| DR-25 | Request account deletion | Secondary |

## Primary driver journey

**Discover station → inspect compatibility and availability → authenticate → reserve EVSE → receive confirmation → check in → start charging → monitor → stop → view session summary**

## Global driver rules

- Browsing is public; booking requires a verified account.
- Drivers may access only their own bookings, sessions and personal data.
- Drivers cannot hold overlapping bookings.
- Only one active charging session is permitted per driver.
- Availability returned by the server is authoritative.
- Booking requests must be idempotent.
- Concurrent requests for the same EVSE/time cannot both succeed.
- Faulted, offline, stale or maintained EVSEs cannot be booked.
- Rescheduling must preserve the original booking if the replacement fails.
- All times are stored in UTC and displayed in `Europe/Athens`.
- Security-sensitive and booking-changing actions are audited.

The first detailed specification should be **DR-11/DR-12: Create an EVSE reservation**, because concurrency, availability and double-booking prevention depend on it.