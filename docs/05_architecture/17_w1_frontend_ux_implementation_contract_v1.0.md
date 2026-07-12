# W1 Frontend and UX Implementation Contract v1.0

## Document metadata

- **Document ID:** ARC-023
- **Version:** 1.0
- **Status:** APPROVED
- **Owner:** Frontend / UX Architect
- **Authoritative for:** W1 frontend routes, screens, API mappings, UI state models, polling, map/geocoding adapters, notification UX, localization, responsive behavior and frontend acceptance criteria
- **Refines:** ARC-003, ARC-008, ARC-009
- **Depends on:** GOV-007, ARC-022, SEC-001, REQ-001, REQ-002
- **Does not supersede:** backend lifecycle, authorization, API, security or accessibility authority
- **Release applicability:** W1-S1 and W1-S2

---

# 1. Purpose

This document converts the logical frontend design into an implementation contract.

It defines:

1. Exact W1 routes and screens.
2. Screen-to-API operation mappings.
3. Loading, empty, stale, degraded, error and offline states.
4. Booking, check-in and charging presentation rules.
5. The W1 browser polling model.
6. Replaceable map, geocoding and email-provider boundaries.
7. Greek and English localization behavior.
8. Accessibility and responsive acceptance criteria.
9. Frontend tests and implementation work packages.

The server remains authoritative for:

- identity and authorization;
- availability and allocation;
- allowed actions;
- booking and session state;
- workflow completion;
- tariff and policy decisions;
- conflict and error reasons.

---

# 2. Approved W1 frontend decisions

| Topic | W1 decision |
|---|---|
| Application | One responsive Angular SPA |
| Rendering | Client-side rendering |
| Browser backend | Same-origin BFF only |
| Authentication | Opaque BFF cookie; no browser OAuth tokens |
| State | Signals, RxJS and feature-scoped stores |
| Forms | Strictly typed reactive forms |
| Routing | Locale-prefixed Angular routes |
| Locales | Greek and English |
| Default locale | `el-GR` |
| Map renderer | MapLibre GL JS |
| Tile provider | Replaceable, environment-configured adapter |
| Geocoding | Replaceable server-side adapter |
| Browser live updates | HTTP polling |
| Browser WebSockets | Not used in W1 |
| Browser SSE | Deferred pending measurements |
| Offline mutations | Prohibited |
| UI library | Angular Material/CDK plus custom theme |
| Accessibility | WCAG 2.2 AA target |
| API clients | OpenAPI-generated clients behind adapters |
| Email delivery | Provider-independent; never called by browser |
| Frontend persistence | Non-sensitive preferences only |

The simulator-to-Device Integration WebSocket is unaffected by the browser polling decision.

---

# 3. Required corrections to current contracts

Before ARC-023 approval:

1. Public discovery must be anonymous and rate-limited without a fictional anonymous bearer token.
2. Browser APIs must use `__Host-evsession`, not a JavaScript-readable token.
3. Browser mutations must include the SEC-001 CSRF contract.
4. Driver BFF OpenAPI must add:
   - session metadata and CSRF;
   - vehicles;
   - booking hold and confirmation;
   - upcoming booking and history;
   - booking cancellation;
   - check-in abandonment;
   - charging status and summary.
5. Charging start and stop must return `202 Accepted`.
6. Command acceptance must not be represented as successful charging start.
7. API lifecycle values must match the lifecycle registry.
8. Every operation must include:
   - `x-release-wave`;
   - `x-slice-applicability`;
   - `x-authorization-policy`;
   - `x-idempotency-required`;
   - `x-ui-surfaces`;
   - documented Problem Details responses.
9. API DTOs must include:
   - server time;
   - resource version;
   - freshness;
   - allowed actions;
   - safe workflow or command references where applicable.

---

# 4. Application shells

## 4.1 Public shell

Contains:

- discovery;
- station and EVSE details;
- authentication entry;
- service status;
- help;
- locale selection.

## 4.2 Driver shell

Contains:

- dashboard;
- booking creation;
- upcoming bookings;
- booking details;
- check-in;
- live charging;
- history;
- vehicles;
- account/security.

## 4.3 Operator shell

W1-S2 contains:

- organization dashboard;
- station and EVSE management;
- tariffs and booking policies;
- operational state;
- maintenance and faults;
- booking intervention;
- simulator assignment.

## 4.4 Platform shell

Platform administration screens are outside W1-S1. Their route namespace remains reserved.

---

# 5. Canonical route rules

All user-facing routes begin with a locale:

```text
/el/...
/en/...
```

Technical route segments remain in stable English.

Rules:

1. `/` redirects to the preferred supported locale.
2. Unsupported locale prefixes return the localized not-found screen.
3. Locale switching preserves route parameters and safe query parameters.
4. Authentication return locations must be same-origin allowlisted routes.
5. Route guards improve UX but do not authorize operations.
6. Sensitive route state is not stored in query parameters.
7. Public discovery criteria may be represented in query parameters.
8. Internal database identifiers never appear in routes.
9. Public references remain opaque.
10. An inaccessible protected resource receives a generic not-found or access-denied presentation according to the security policy.

---

# 6. W1-S1 route and screen catalogue

## 6.1 System and session screens

| ID | Route | Screen | Main operations |
|---|---|---|---|
| SYS-01 | `/{locale}/sign-in` | Authentication entry | `beginLogin` |
| SYS-02 | `/{locale}/auth/result` | Login return/result | `getSession` |
| SYS-03 | `/{locale}/access-denied` | Access denied | None |
| SYS-04 | `/{locale}/not-found` | Not found | None |
| SYS-05 | `/{locale}/service-status` | Degradation status | `getServiceStatus` |
| SYS-06 | `/{locale}/session-expired` | Session expiry recovery | `beginLogin` |

Identity-provider registration, verification and recovery pages may be hosted by Keycloak, but must return to an allowlisted localized application route.

## 6.2 Public discovery screens

| ID | Route | Screen | Main operations |
|---|---|---|---|
| PUB-01 | `/{locale}` | Discovery entry | `listStations` |
| PUB-02 | `/{locale}/stations` | Map/list discovery | `listStations` |
| PUB-03 | `/{locale}/stations/{stationRef}` | Station details | `getStation`, `getStationAvailability` |
| PUB-04 | `/{locale}/stations/{stationRef}/evses/{evseRef}` | EVSE details | `getStation`, `getEvse`, `getStationAvailability` |
| PUB-05 | `/{locale}/help` | Help and charging guidance | None |

## 6.3 Driver screens

| ID | Route | Screen | Main operations |
|---|---|---|---|
| DRV-01 | `/{locale}/account` | Driver dashboard | `getSession`, `listUpcomingBookings` |
| DRV-02 | `/{locale}/bookings/new` | Booking wizard | `listVehicles`, `getStationAvailability`, `createBookingHold`, `confirmBooking` |
| DRV-03 | `/{locale}/bookings/upcoming` | Upcoming bookings | `listUpcomingBookings` |
| DRV-04 | `/{locale}/bookings/{bookingRef}` | Booking detail | `getBooking`, `cancelBooking` |
| DRV-05 | `/{locale}/bookings/{bookingRef}/check-in` | Check-in | `getBooking`, `checkInBooking`, `abandonCheckIn` |
| DRV-06 | `/{locale}/bookings/{bookingRef}/charging` | Live charging | `getChargingSession`, `startChargingSession`, `stopChargingSession` |
| DRV-07 | `/{locale}/bookings/{bookingRef}/summary` | Session summary | `getChargingSessionSummary` |
| DRV-08 | `/{locale}/history` | Booking/session history | `listBookingHistory` |
| DRV-09 | `/{locale}/vehicles` | Vehicle list | `listVehicles`, `createVehicle` |
| DRV-10 | `/{locale}/vehicles/{vehicleRef}` | Vehicle editor | `getVehicle`, `updateVehicle`, `deleteVehicle` |
| DRV-11 | `/{locale}/account/security` | Security/session summary | `getSession`, `logout` |

## 6.4 W1-S2 screens

| ID | Route | Screen |
|---|---|---|
| DRV-12 | `/{locale}/bookings/{bookingRef}/reschedule` | Atomic rescheduling |
| OPR-01 | `/{locale}/operator/organizations/{organizationRef}/dashboard` | Operator dashboard |
| OPR-02 | `.../stations` | Station list |
| OPR-03 | `.../stations/{stationRef}` | Station detail |
| OPR-04 | `.../stations/{stationRef}/edit` | Station configuration |
| OPR-05 | `.../stations/{stationRef}/evses` | EVSE management |
| OPR-06 | `.../stations/{stationRef}/tariffs` | Tariffs |
| OPR-07 | `.../stations/{stationRef}/booking-policies` | Policies |
| OPR-08 | `.../operations` | Operational dashboard |
| OPR-09 | `.../maintenance` | Maintenance list |
| OPR-10 | `.../maintenance/{maintenanceRef}` | Maintenance workflow |
| OPR-11 | `.../faults` | Fault queue |
| OPR-12 | `.../bookings` | Scoped bookings |
| OPR-13 | `.../simulators` | Simulator assignments |

---

# 7. Standard frontend resource states

Every remote-resource feature store must expose:

```text
UNINITIALIZED
LOADING
READY
EMPTY
REFRESHING
STALE
DEGRADED
ERROR
OFFLINE
```

## 7.1 State meanings

### `LOADING`

- First request is pending.
- Show a skeleton matching the final layout.
- Do not show a false empty state.
- Primary dependent actions are unavailable.

### `READY`

- A successful current response is present.
- Display the server-provided freshness and version where relevant.

### `EMPTY`

- Request succeeded and returned no applicable resources.
- Explain what is empty and provide a useful next action.
- Empty is not an error.

### `REFRESHING`

- Existing successful data remains visible.
- Show a non-blocking refresh indicator.
- Avoid replacing the whole page with a spinner.

### `STALE`

- Previously successful data is displayed beyond its freshness expectation.
- Show the last-update time.
- Do not visually claim real-time status.
- Correctness-sensitive actions remain governed by server-returned `allowedActions`.

### `DEGRADED`

- The primary function remains usable but an optional dependency failed.
- Examples:
  - map failed but list works;
  - notification delivery status unavailable;
  - analytics panel unavailable.

### `ERROR`

- The request failed and no safe usable result exists.
- Display a controlled message mapped from the problem-code registry.
- Show a retry only when safe.

### `OFFLINE`

- Browser network connectivity is unavailable.
- Mutations are disabled.
- No mutation is queued locally.
- Non-sensitive in-memory content may remain visible with an offline warning.

---

# 8. Standard mutation states

Every business mutation uses:

```text
IDLE
SUBMITTING
ACCEPTED
PENDING
SUCCEEDED
CONFLICT
UNCERTAIN
FAILED
```

Rules:

- `SUBMITTING`: local request has not yet received a durable server response.
- `ACCEPTED`: server returned `202`; intent is durable, outcome is not complete.
- `PENDING`: workflow/command polling is active.
- `SUCCEEDED`: authoritative success response or terminal workflow state received.
- `CONFLICT`: business state changed; user intervention is needed.
- `UNCERTAIN`: physical or distributed outcome remains unresolved.
- `FAILED`: definitive safe failure.
- Browser timeout never converts `PENDING` into `FAILED`.
- State-changing retries preserve the original idempotency key.
- Automatic mutation retry is prohibited unless explicitly approved.

---

# 9. Public discovery screen contract

## 9.1 Search inputs

W1 supports:

- map bounds;
- date;
- start time;
- duration;
- connector type;
- minimum power;
- availability;
- optional user position;
- map/list mode.

Later filters may include:

- price range;
- operator;
- access type;
- amenities.

## 9.2 URL query contract

Example:

```text
/{locale}/stations
  ?west=...
  &south=...
  &east=...
  &north=...
  &start=...
  &durationMinutes=...
  &connector=CCS2
  &minimumPowerKw=50
  &view=list
```

Rules:

- coordinates use bounded precision;
- user-location history is not stored;
- invalid values are removed and announced;
- the URL contains no account or vehicle identifier;
- filter state is restored after authentication;
- a shared URL restores criteria, not authoritative availability.

## 9.3 Desktop layout

```text
Header
Search/filter region
Freshness/degradation banner
--------------------------------
Result list       | Map
Selected summary  | Map controls
Pagination        | Attribution
```

## 9.4 Mobile layout

- List is the default.
- Explicit map/list switch.
- Filters open in an accessible full-screen sheet.
- Selected map result opens a bottom sheet or full-page detail.
- No essential operation exists only on a marker.

## 9.5 Screen states

| Condition | Presentation |
|---|---|
| Initial loading | Result-card skeletons; map placeholder |
| No stations | Suggestions to widen bounds/change filters |
| No compatible availability | Stations may remain visible, clearly non-bookable |
| Stale availability | Timestamp and warning; never green "available" |
| Map initialization failed | List remains fully operational |
| Tile provider unavailable | Map fallback panel; list retained |
| Search unavailable | Service warning and retry |
| Location denied | Continue without location |
| Offline | Existing in-memory results marked offline; search disabled |

The map and list must derive from the same result collection.

---

# 10. Station-detail contract

Display:

- station name and operator;
- address and coordinates;
- opening hours and exceptions;
- access instructions;
- connectors and power;
- tariff summary;
- operational warnings;
- requested-interval availability;
- freshness;
- accessible directions link;
- book action.

The screen must distinguish:

- administrative availability;
- device status;
- derived booking availability;
- data freshness.

`AVAILABLE`, `PLANNED_AVAILABLE`, `UNAVAILABLE`, `UNKNOWN`, and `INCOMPATIBLE` are presented as separate localized concepts.

The Book action carries only public station/EVSE references and safe search criteria into the wizard.

---

# 11. Booking-wizard contract

## 11.1 Steps

```text
1. Station
2. Date and duration
3. Vehicle or connector requirements
4. Automatic assignment or exact EVSE
5. Advisory availability
6. Create hold
7. Review held assignment
8. Confirm
9. Authoritative confirmation
```

## 11.2 Wizard states

```text
CRITERIA
AVAILABILITY_LOADING
ASSIGNMENT
HOLD_SUBMITTING
HELD_REVIEW
CONFIRM_SUBMITTING
CONFIRMED
RECOVERY_REQUIRED
```

## 11.3 Hold review

Display:

- assigned station and EVSE;
- connector;
- charging interval;
- hold expiry;
- tariff estimate;
- cancellation policy;
- booking-policy summary;
- confirmation action.

The countdown uses:

- server `holdExpiresAt`;
- server `currentTime`;
- measured client/server clock offset.

The countdown is informational. Server time remains authoritative.

## 11.4 Recovery rules

| Problem | UX response |
|---|---|
| `EVSE_ALLOCATION_CONFLICT` | Return to assignment with alternatives |
| `NO_COMPATIBLE_EVSE` | Return to compatibility criteria |
| `BOOKING_HOLD_EXPIRED` | Clear held authority and offer new hold |
| `INVALID_INTERVAL` | Focus interval fields |
| `OUTSIDE_OPENING_HOURS` | Show opening-hours guidance |
| `EVSE_STALE_TELEMETRY` | Explain that status cannot be trusted |
| `STATUS_UNKNOWN` | Do not imply capacity was reserved |
| `ALLOCATION_BUSY` | Offer safe retry with same criteria |
| `IDEMPOTENCY_KEY_REUSED` | Stop and reload current operation state |

Browser back-navigation must not create a second hold.

---

# 12. Booking-detail contract

Display:

- public booking reference;
- state;
- station and assigned EVSE;
- interval;
- check-in window;
- hold/booking policy snapshot;
- tariff snapshot;
- estimated cost;
- warnings;
- timeline;
- session outcome;
- server-returned allowed actions.

Only actions returned by the server may be actionable.

Potential actions:

- confirm;
- cancel;
- reschedule;
- check in;
- abandon check-in;
- start charging;
- view charging;
- view summary.

The frontend must not derive lifecycle permissions independently.

---

# 13. Check-in contract

## 13.1 Page states

```text
BOOKING_LOADING
TOO_EARLY
READY_TO_CHECK_IN
SCANNING
MANUAL_ENTRY
CHECK_IN_SUBMITTING
CHECKED_IN
WRONG_EVSE
OPERATIONALLY_BLOCKED
WINDOW_CLOSED
REASSIGNMENT_REQUIRED
```

## 13.2 QR rules

QR input contains only:

- public application URL;
- public EVSE reference.

It never contains:

- authorization secret;
- account information;
- booking secret;
- internal identifier.

Manual entry follows the same validation.

## 13.3 Success presentation

After check-in:

- show `CHECKED_IN`;
- show assigned EVSE;
- show earliest start time;
- show grace deadline;
- show Start action only when server permits it;
- do not expose the start-authorization secret.

Equipment failure must never be presented as driver no-show.

---

# 14. Charging-session contract

## 14.1 User-facing states

| Backend condition | User-facing presentation |
|---|---|
| No session | Ready to start, if permitted |
| Session `STARTING` | Contacting charger / waiting for transaction evidence |
| Attempt `DEVICE_ACCEPTED` | Charger acknowledged; waiting for energy flow |
| Attempt `RECONCILING` | Start outcome uncertain; checking charger |
| Session `CHARGING` | Charging in progress |
| Session `SUSPENDED` | Temporarily paused, with reason |
| Session `STOPPING` | Stop requested; waiting for final evidence |
| Attempt unresolved | Manual review required; capacity may remain blocked |
| Session `START_REJECTED` | Charging did not start |
| Session `COMPLETED` | Completed normally |
| Session `INTERRUPTED` | Ended with interruption |

`DEVICE_ACCEPTED` must not use charging animation, energy-flow wording, or a green "charging" status.

## 14.2 Live-session display

Display:

- session state;
- elapsed duration;
- accepted energy;
- current power;
- estimated cost;
- last accepted update time;
- freshness;
- stop action;
- suspension/fault reason;
- reconciliation status.

Do not animate energy flow when telemetry is stale.

## 14.3 Stop behavior

After a stop request:

- show `STOPPING`;
- disable duplicate stop UI while preserving idempotent recovery;
- retain live session information;
- do not navigate to summary until terminal evidence exists;
- show an uncertainty panel if reconciliation is required.

---

# 15. W1 browser polling contract

## 15.1 Decision

W1 uses HTTP polling.

Browser SSE and WebSockets are not implemented in W1.

Reasons:

- simpler BFF and security model;
- adequate for the W1 reference load;
- straightforward recovery after refresh;
- no long-lived browser connection infrastructure;
- easy use of existing REST contracts.

## 15.2 Polling schedule

| Resource/state | Foreground interval | Hidden-tab interval |
|---|---|---:|---:|
| Session `STARTING`/`STOPPING`, first 30s | 2s | 10s |
| Session `STARTING`/`STOPPING`, 30–120s | 5s | 15s |
| Session pending after 120s | 15s | 30s |
| Session `CHARGING` | 5s | 30s |
| Workflow pending | 5s | 30s |
| Upcoming bookings | 60s | Paused |
| Near-term station detail | 30s | Paused |
| Search results | User/filter triggered | Paused |
| Terminal resource | Stop polling | Stop polling |

Server-provided `Retry-After` or `retryAfterSeconds` overrides these defaults.

## 15.3 Polling rules

1. Only one request per resource may be in flight.
2. Obsolete requests are cancelled.
3. Add up to 10% jitter.
4. Poll immediately when a hidden tab becomes visible.
5. Stop on terminal state.
6. Stop on `401` and invoke session-expiry handling.
7. Stop on permanent `403` or `404`.
8. Back off on `429`, `502`, `503`, and `504`.
9. Retain stale data during retryable failures.
10. Respect `ETag` and conditional GET where supported.
11. Polling errors are not announced repeatedly to screen readers.
12. A manual refresh remains available.

## 15.4 SSE reconsideration gate

SSE requires a new ADR only if measured evidence shows one or more of:

- update-age NFR cannot be met through polling;
- polling exceeds 30% of BFF request volume;
- polling consumes more than 20% of measured BFF CPU;
- more than 500 simultaneously observed live sessions are required;
- provider or network costs are materially reduced by push;
- users require sub-two-second browser updates.

The ADR must cover:

- BFF session authentication;
- reconnect behavior;
- `Last-Event-ID`;
- authorization revocation;
- per-user connection limits;
- proxy timeouts;
- load testing;
- fallback to polling.

---

# 16. Map-provider adapter contract

MapLibre is the selected rendering library. It must not own provider-specific business configuration. ARC-009 leaves tile and geocoding providers pending. ([raw.githubusercontent.com](https://raw.githubusercontent.com/panospao7/Ev-chargind-station/main/docs/05_architecture/08_technology_selection_adr_set_v1.0.md))

## 16.1 Client configuration

Add:

```text
GET /api/v1/client-configuration/map
```

Response:

- `enabled`;
- `renderer: MAPLIBRE`;
- `styleUrl` or approved style reference;
- `minimumZoom`;
- `maximumZoom`;
- `initialBounds`;
- `geocodingEnabled`;
- `attributions[]`;
- `reportMapIssueUrl`;
- `configurationVersion`.

Provider secrets must never appear in this response.

Public browser keys are allowed only when:

- the provider explicitly designs them as public;
- domain restrictions are configured;
- quota abuse is controlled;
- no privileged provider operation is possible.

## 16.2 Frontend boundary

Create a `MapPresentationAdapter` exposing:

- initialize;
- setResults;
- selectStation;
- fitBounds;
- setUserPosition;
- setLocale;
- displayProviderError;
- destroy.

Features must not call MapLibre directly outside the adapter/component boundary.

## 16.3 Attribution

Attribution must:

- remain visible;
- be keyboard reachable;
- use provider-supplied structured entries;
- never accept unsanitized provider HTML;
- remain available on mobile;
- not be hidden by sheets or sticky controls.

MapLibre provides a dedicated attribution control, and OSM-derived data requires attribution. ([maplibre.org](https://maplibre.org/maplibre-gl-js/docs/API/type-aliases/AttributionControlOptions/?utm_source=openai))

## 16.4 Provider rules

- Do not hard-code tile URLs in feature code.
- Do not implement tile prefetch or offline maps in W1.
- Honor provider caching rules.
- Do not send account, booking or vehicle data to the tile provider.
- Do not include precise user position in application telemetry.
- Map-provider failure must never remove list functionality.

The public OSM standard tile service is best-effort, has no SLA, prohibits bulk/offline prefetching, and recommends provider-switching capability. It must not silently become the production dependency. ([operations.osmfoundation.org](https://operations.osmfoundation.org/policies/tiles/?utm_source=openai))

## 16.5 W1 environment profiles

| Environment | Map behavior |
|---|---|
| Unit/component tests | Mock adapter; no external network |
| E2E CI | Deterministic local/static map fixture or mock |
| Local development | Environment-configured development style |
| Demonstrator cloud | Approved hosted/self-hosted provider configuration |
| Provider unavailable | Accessible list fallback |

---

# 17. Geocoding adapter contract

## 17.1 W1 decision

Geocoding is optional for W1-S1.

The first slice remains functional through:

- station list;
- map bounds;
- seeded station coordinates;
- optional one-shot browser geolocation.

No provider is required to complete a booking.

## 17.2 Browser API

If enabled:

```text
GET /api/v1/places/search
```

Parameters:

- `query`;
- `locale`;
- `countryCode=GR`;
- optional bounded map bias;
- `limit`.

Response:

- provider-neutral place reference;
- display label;
- approximate coordinates;
- bounding box;
- result type;
- attribution reference.

## 17.3 Provider boundary

The browser calls the BFF, not the external geocoder.

The backend `GeocodingPort` must support:

- explicit search;
- cancellation;
- timeout;
- caching;
- rate limiting;
- locale;
- country restriction;
- provider result normalization.

W1 prohibits:

- address autocomplete on every keystroke;
- background bulk geocoding;
- storage of raw search history;
- sending booking/account identifiers;
- silent provider fallback with different privacy terms.

If public Nominatim is used temporarily, its official policy limits use to one request per second and requires application identification and attribution. It is therefore unsuitable for unrestricted autocomplete. ([operations.osmfoundation.org](https://operations.osmfoundation.org/policies/nominatim/?utm_source=openai))

## 17.4 Geolocation

Browser geolocation must be:

- requested only after user action;
- optional;
- one-shot by default;
- used in memory;
- removable through a "Clear location" action;
- absent from ordinary logs and analytics.

Denial must not reduce access to station discovery.

---

# 18. Email-provider and notification UX contract

## 18.1 Provider boundary

The browser never calls an email provider.

Notification Service exposes an internal provider-independent port:

```text
EmailDeliveryPort.send(message)
```

Provider adapters may include:

- local SMTP/Mailpit;
- test fake;
- future hosted transactional-email provider.

## 18.2 Ownership

- Keycloak owns verification, recovery and identity-security email.
- Notification Service owns application transactional email.
- Booking and Session owns the booking transaction.
- Email failure never reverses a booking or session result.

## 18.3 Browser presentation

After confirmation:

> Booking confirmed. You can always view it in Upcoming bookings.

Optional secondary text:

> A confirmation email has been queued.

Do not imply that email delivery is required for booking validity.

After email-provider failure:

- keep the successful business result;
- do not convert the screen into an error;
- optionally show "Email delivery is delayed" if the application has authoritative delivery status;
- always provide an in-application route to the authoritative record.

## 18.4 Email safety

Email links must:

- use stable application routes;
- contain no access token or authorization secret;
- avoid unnecessary personal/location data;
- require authentication for protected records;
- use the recipient's selected locale;
- default to Greek when no preference exists.

Local and CI environments use a mail catcher and synthetic addresses only.

---

# 19. Problem-to-UX mapping

| Problem code | Presentation |
|---|---|
| `AUTHENTICATION_REQUIRED` | Begin login and preserve safe return route |
| `SESSION_EXPIRED` | Clear sensitive memory and show expiry screen |
| `CSRF_VALIDATION_FAILED` | Reload session metadata; never auto-resubmit |
| `ACCESS_DENIED` | Access-denied screen |
| `VERSION_CONFLICT` | Reload resource and explain it changed |
| `EVSE_ALLOCATION_CONFLICT` | Return to assignment alternatives |
| `NO_COMPATIBLE_EVSE` | Return to compatibility criteria |
| `BOOKING_HOLD_EXPIRED` | Clear held authority and restart hold |
| `BOOKING_STATE_CONFLICT` | Reload Booking and allowed actions |
| `CHECK_IN_WINDOW_CLOSED` | Show exact server-provided window |
| `WRONG_EVSE` | Show assigned EVSE and corrective action |
| `EVSE_STALE_TELEMETRY` | Stale warning; no positive availability claim |
| `EVSE_OFFLINE` | Offline warning and alternatives |
| `STATUS_UNKNOWN` | Explain why the operation fails closed |
| `ALLOCATION_BUSY` | Safe retry with original criteria/idempotency |
| `START_ATTEMPT_LIMIT_REACHED` | Show resolution/support path |
| `START_RETRY_NOT_ELIGIBLE` | Show current attempt/session status |
| `SESSION_OUTCOME_UNRESOLVED` | Reconciliation panel; never generic failure |
| `RATE_LIMIT_EXCEEDED` | Countdown from `Retry-After` |
| `DEPENDENCY_UNAVAILABLE` | Degradation or retry panel according to operation |

Raw backend details, stack traces, SQL messages, or identifiers must never be rendered.

---

# 20. Localization contract

Supported locales:

- `el-GR`
- `en`

Rules:

1. Stable custom translation IDs are mandatory.
2. API codes and enum values are never rendered directly.
3. Dates and booking times use `Europe/Athens` or the station timezone.
4. API instants remain UTC.
5. Currency uses EUR locale formatting.
6. Power uses kW; energy uses kWh.
7. Distance uses kilometres.
8. Hold and workflow countdowns use localized duration formatting.
9. Plural forms use Angular localization support.
10. Missing W1 translations fail production CI.
11. English text is not used as a translation key.
12. User-generated content is not automatically translated.
13. Language switching preserves the equivalent route and safe state.
14. Problem-code translations include actions, not only titles.

---

# 21. Accessibility contract

Every W1 screen must pass:

- semantic structure;
- keyboard-only use;
- visible focus;
- skip navigation;
- logical heading order;
- 200% and 400% zoom;
- responsive reflow;
- accessible form errors;
- screen-reader announcements;
- reduced motion;
- target-size checks;
- contrast checks;
- modal focus containment and restoration;
- no keyboard traps.

## 21.1 Map accessibility

- The list is fully equivalent to the map.
- Markers are not the only way to select a station.
- "Search this area" is a real button.
- Attribution is accessible.
- Map controls have accessible names.
- Map failure leaves the list usable.
- No automatic geolocation request.

## 21.2 Async accessibility

- Initial load uses `aria-busy`.
- Meaningful state completion is announced once.
- Polling updates do not repeatedly interrupt screen readers.
- Charging energy/power changes are not announced every poll.
- Start, stop, failure and uncertainty transitions are announced.
- Countdown expiry produces one announcement and moves focus to recovery guidance.

---

# 22. Responsive-layout tokens

Use semantic layout ranges:

| Token | Width |
|---|---:|
| `compact` | below 600px |
| `medium` | 600–1023px |
| `expanded` | 1024–1439px |
| `wide` | 1440px and above |

Rules:

- layouts respond to available space, not device identity;
- no horizontal scrolling for primary driver forms;
- operational tables may scroll when relationships require it;
- dialogs become full-screen sheets in compact layout;
- sticky actions must not cover focused content;
- map never permanently replaces the result list;
- all variants meet the same accessibility requirements.

---

# 23. Shared design-system components

Required W1 shared components:

- application shell;
- skip link;
- locale selector;
- service-degradation banner;
- offline banner;
- loading skeleton;
- empty state;
- error summary;
- Problem Details panel;
- freshness indicator;
- availability badge;
- booking-state badge;
- session-state badge;
- hold countdown;
- workflow progress;
- timeline;
- confirmation dialog;
- accessible filter sheet;
- map/list switch;
- station result card;
- price estimate;
- permitted-action panel;
- correlation-reference display.

Domain-specific behavior remains inside feature modules.

---

# 24. Frontend observability

Capture:

- route timing;
- operation-ID API timing;
- Web Core Vitals;
- problem-code counts;
- polling request/error counts;
- poll-to-visible-state latency;
- failed lazy chunks;
- map initialization/provider failures;
- session expiry;
- workflow age;
- JavaScript errors.

Never capture:

- OAuth/session values;
- CSRF token;
- email address;
- form contents;
- vehicle registration data;
- exact location history;
- authorization secrets;
- support reveal values;
- full API payloads.

Telemetry must use safe correlation references.

---

# 25. Frontend testing contract

## 25.1 Unit tests

Test:

- feature stores;
- state transitions;
- query serialization;
- server-clock offset;
- hold countdown;
- polling scheduler;
- backoff and cancellation;
- Problem Details mapping;
- route allowlisting;
- locale formatting;
- allowed-action presentation.

## 25.2 Component tests

Required for:

- discovery filters;
- map/list synchronization;
- station cards;
- booking wizard;
- hold review;
- booking action panel;
- check-in form;
- charging status;
- uncertainty panel;
- workflow progress;
- form error summary;
- responsive navigation.

## 25.3 Contract tests

- Generated clients compile.
- Every W1 example renders.
- Unknown optional fields are tolerated.
- Unknown lifecycle values use an unsupported-state fallback.
- Every problem code maps to a controlled presentation.
- No browser client is generated from an internal API.
- Start/stop `202` responses enter pending state.

## 25.4 E2E journeys

1. Public list discovery without geolocation.
2. Map/list equivalent discovery.
3. Location permission denied.
4. Map-provider failure with list fallback.
5. Login return to safe route.
6. Hold and confirmation.
7. Concurrent allocation conflict recovery.
8. Hold expiry.
9. View and cancel booking.
10. Check in through manual EVSE reference.
11. Wrong-EVSE recovery.
12. Start pending then charging.
13. Device rejection.
14. Start uncertainty and reconciliation.
15. Live charging then stop.
16. Interrupted charging.
17. Session summary.
18. Session expiry and CSRF rejection.
19. Offline mutation prevention.
20. Greek and English complete journey.

## 25.5 Accessibility tests

Use:

- automated axe checks;
- keyboard testing;
- screen-reader testing;
- 200% and 400% zoom;
- contrast validation;
- reduced-motion testing;
- focus-order review;
- map/list-equivalence review.

External map/geocoding/email providers must be mocked in CI.

---

# 26. Performance budgets

Initial compressed budgets:

| Asset | Budget |
|---|---:|
| Initial JavaScript | 300 KiB |
| Initial CSS | 75 KiB |
| Ordinary lazy feature | 200 KiB |
| Map feature | Measured separately and lazy-loaded |

Additional requirements:

- operator/platform code is not loaded for public discovery;
- MapLibre is loaded only on map-capable routes;
- obsolete reads are cancelled;
- search input is debounced;
- map movement does not automatically search;
- server caching headers are honored;
- no polling continues after terminal state.

---

# 27. Work packages

| ID | Deliverable |
|---|---|
| UX-001 | Patch ARC-008 and approve W1 decisions |
| UX-002 | Create route and screen registry |
| UX-003 | Create frontend state and Problem Details mapping |
| UX-004 | Complete Driver BFF OpenAPI mappings |
| UX-005 | Create Angular workspace shells and feature boundaries |
| UX-006 | Implement shared loading/error/stale components |
| UX-007 | Implement discovery map/list adapter |
| UX-008 | Implement booking wizard contract |
| UX-009 | Implement check-in and charging presentation |
| UX-010 | Implement polling scheduler |
| UX-011 | Implement localization |
| UX-012 | Implement accessibility test harness |
| UX-013 | Add frontend observability |
| UX-014 | Complete E2E and contract evidence |

---

# 28. Required repository artifacts

Create:

```text
contracts/registries/screens-v1.yaml
contracts/registries/routes-v1.yaml
contracts/registries/ui-problem-mapping-v1.yaml
contracts/registries/polling-policies-v1.yaml
contracts/registries/client-configuration-v1.yaml
contracts/examples/frontend/
docs/ux/wireframes/
docs/ux/accessibility/
docs/ux/evidence/
```

## `screens-v1.yaml`

Every screen entry contains:

- ID;
- route;
- shell;
- release wave;
- slice;
- required actor;
- API operation IDs;
- initial-loading state;
- empty state;
- stale state;
- error mappings;
- offline behavior;
- accessibility checks;
- analytics event allowlist.

## `routes-v1.yaml`

Every route contains:

- route pattern;
- locale behavior;
- feature module;
- authentication requirement;
- coarse role;
- recent-authentication requirement;
- safe return-route eligibility;
- page title translation ID;
- release applicability.

---

# 29. Required documentation patches

## ARC-008

- Mark polling as approved for W1.
- Add W1-S1/W1-S2 applicability.
- Reference ARC-023 for exact screens and state behavior.
- Close map-library selection as MapLibre.
- Keep tile and geocoding provider selection open.
- Replace stale "next artifact" wording.
- Reconcile retry-capable charging UX.

## ARC-003 and executable OpenAPI

- Add the complete Driver BFF surface.
- Correct session cookie and CSRF behavior.
- Correct asynchronous start/stop responses.
- Add client configuration and optional geocoding operations.
- Add frontend extensions and examples.

## ARC-009

- Keep MapLibre and Angular Material/CDK approved.
- State that tile/geocoding providers remain replaceable.
- Reference polling reconsideration criteria.

## SEC-001

- Reference frontend session-expiry, CSRF, safe return-route and telemetry rules.

## GOV-001

Add:

- `DEC-UX-01` — ARC-023 is the W1 frontend contract.
- `DEC-UX-02` — polling is the W1 browser-update mechanism.
- `DEC-UX-03` — MapLibre is the renderer; provider is replaceable.
- `DEC-UX-04` — geocoding is optional in W1-S1.
- `DEC-UX-05` — browser never contacts the email provider.
- `DEC-UX-06` — list discovery is functionally equivalent to map discovery.

---

# 30. Open, provisional and deferred items

## OPEN

- Demonstrator tile provider.
- Demonstrator geocoding provider.
- Production transactional-email provider.
- Final visual brand assets.
- Translation-review owner.

## PROVISIONAL

- Polling intervals.
- Frontend performance budgets.
- Exact provider configuration.
- Geocoding enabled only if provider review succeeds.
- Responsive layout thresholds, subject to usability validation.

## DEFERRED

- SSE.
- Browser WebSockets.
- PWA/service worker.
- Offline mutations.
- SSR.
- Routing/navigation service.
- Address autocomplete.
- Offline maps.
- Map tile prefetch.
- push/SMS notifications.

No open provider decision blocks feature implementation because all integrations use replaceable contracts and deterministic test adapters.

---

# 31. Definition of done

ARC-023 is complete when:

1. Every W1 use case maps to a screen or background interaction.
2. Every W1 screen maps to executable operation IDs.
3. Every screen defines loading, empty, stale, error and offline behavior.
4. Start and stop use asynchronous pending UX.
5. Device acceptance is not presented as charging.
6. Polling behavior is deterministic and tested.
7. The list remains equivalent to the map.
8. Provider failures do not block discovery or committed booking management.
9. Tile/geocoding/email providers are replaceable.
10. No provider secret appears in browser configuration.
11. Greek and English journeys pass.
12. All W1 screens meet accessibility acceptance criteria.
13. Browser storage contains no sensitive operational data.
14. Generated clients compile.
15. External providers are mocked in CI.
16. All critical E2E journeys pass.
17. Screen, route, polling and error registries validate.
18. Frontend telemetry passes privacy tests.
19. ARC-008 and OpenAPI contain no conflicting behavior.
20. Verification evidence records immutable commit and CI references.

---

# 32. Recommended commit sequence

1. `docs: add ARC-023 W1 frontend UX implementation contract`
2. `docs: patch ARC-008 route state and polling decisions`
3. `contracts: add screen route polling and UI-error registries`
4. `contracts: complete W1 Driver BFF and client-config APIs`
5. `frontend: create Angular shells routes and generated-client adapters`
6. `frontend: implement common UX state and polling infrastructure`
7. `frontend: implement map geocoding and notification adapters`
8. `test: add frontend contract accessibility and E2E suites`
9. `governance: approve ARC-023 after green evidence`

Frontend business screens may begin after commits 1–4 establish stable route, operation and state contracts.
