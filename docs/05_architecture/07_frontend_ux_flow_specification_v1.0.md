Document ID: ARC-008  
Title: Frontend Architecture, Screen Catalogue and UX Flow Specification  
Version: 1.0  
Status: IN_REVIEW  
Owner: Frontend / UX Architect  
Last reviewed: 2026-07-11  
Depends on: ARC-001–007, REQ-001, REQ-002, DOM-001, DOM-002, NOT-001  
Authoritative for: Angular application structure, client state, routing, screens, navigation, responsive behaviour, accessibility, localization and browser UX flows  

# Frontend Architecture, Screen Catalogue and UX Flow Specification v1.0

## 1. Purpose

This document defines:

- Angular application architecture
- BFF and API integration
- Client-side routing and route protection
- State-management boundaries
- Screen and navigation catalogue
- Driver, operator and platform UX flows
- Availability and workflow-status presentation
- Responsive map/list behaviour
- Forms and validation
- Accessibility requirements
- Greek and English localization
- Security-sensitive interaction patterns
- Frontend observability and testing

It does not select final component, charting, map-provider or end-to-end testing libraries. Those choices belong to the technology-selection phase.

---

## 2. Frontend objectives

The application must:

1. Provide one responsive web application for all human roles.
2. Keep role-specific areas visibly and structurally separated.
3. Use the BFF as the only browser-facing application backend.
4. Never store OAuth access or refresh tokens in browser JavaScript.
5. Treat the server as authoritative for permissions and allowed actions.
6. Preserve booking and lifecycle semantics accurately.
7. Distinguish administrative, device-reported and derived states.
8. Expose freshness and uncertainty honestly.
9. Meet the WCAG 2.2 AA target.
10. Support Greek and English.
11. Remain usable when search, analytics or email delivery is degraded.
12. Avoid excessive frontend complexity for an individual project.

---

## 3. Selected application profile

### 3.1 Rendering model

Use an Angular client-rendered single-page application.

Initial release:

- Client-side routing
- Same-origin BFF
- No server-side rendering
- No service worker or offline mutation queue
- No micro-frontends
- No separate applications per role

Public URLs remain stable and deep-linkable.

Server-side rendering may be reconsidered if search-engine discoverability becomes a measured requirement. Offline booking, check-in or charging actions are prohibited because authoritative server validation is required.

### 3.2 Angular composition

Use:

- Standalone components
- Route-based lazy loading
- Feature-level providers
- Signals for local synchronous UI state
- RxJS for HTTP, timing and multi-event asynchronous flows
- Strict TypeScript
- Strictly typed reactive forms
- `OnPush`-compatible rendering patterns

Angular Signals support granular reactive state tracking, and Angular Router supports nested and lazy-loaded route structures. ([angular.dev](https://angular.dev/guide/signals?utm_source=openai))

### 3.3 State-library decision

Do not introduce a global Redux-style store initially.

Use:

- Feature stores built from Angular services and Signals
- Generated API clients behind repository/facade classes
- URL query parameters for restorable discovery state
- Component state for transient interaction
- Server responses for authoritative business state

A third-party global state library requires evidence of state complexity that cannot be managed safely through these patterns.

---

## 4. Application shells

The application contains four shells.

### 4.1 Public shell

For:

- Discovery
- Station details
- Authentication entry
- Public reference data

Navigation:

- Find charging
- Sign in
- Language
- Accessibility/help

### 4.2 Driver shell

For:

- Upcoming bookings
- Check-in and charging
- History
- Vehicles
- Preferences
- Privacy
- Support

### 4.3 Operator shell

For:

- Organization management
- Infrastructure
- Operations
- Bookings
- Simulator
- Analytics
- Staff
- Organization audit

### 4.4 Platform shell

For:

- Operator applications
- Support cases
- Investigations
- Emergency workflows
- Audit/security review
- Reference data
- Platform analytics

A user with multiple areas selects an active context explicitly. Entering Operator or Platform areas must not silently change the meaning of Driver navigation.

---

## 5. Recommended workspace structure

```text
src/app/
├── app.config.ts
├── app.routes.ts
├── core/
│   ├── auth/
│   ├── http/
│   ├── routing/
│   ├── errors/
│   ├── observability/
│   ├── localization/
│   └── session/
├── layout/
│   ├── public-shell/
│   ├── driver-shell/
│   ├── operator-shell/
│   └── platform-shell/
├── shared/
│   ├── ui/
│   ├── forms/
│   ├── accessibility/
│   ├── formatting/
│   └── testing/
├── features/
│   ├── discovery/
│   ├── station-details/
│   ├── account/
│   ├── vehicles/
│   ├── bookings/
│   ├── check-in/
│   ├── charging-session/
│   ├── history/
│   ├── privacy/
│   ├── support/
│   ├── operator-organization/
│   ├── operator-infrastructure/
│   ├── operator-operations/
│   ├── operator-bookings/
│   ├── simulator/
│   ├── analytics/
│   ├── platform-governance/
│   └── audit/
├── api/
│   ├── generated/
│   └── adapters/
└── assets/
    ├── i18n/
    └── icons/
```

---

## 6. Dependency rules

1. Feature components may depend on `shared` and `core`.
2. Features must not import another feature’s internal components or stores.
3. Cross-feature navigation uses routes and public facade interfaces.
4. Generated API clients are accessed only through adapters/repositories.
5. Shared UI contains no Booking, Station or Session business rules.
6. `core` contains singleton technical infrastructure only.
7. Domain decisions remain on the server.
8. Components do not construct REST URLs manually.
9. API DTOs are mapped to feature view models where presentation differs.
10. Browser code never imports secrets or environment credentials.

---

## 7. BFF integration

### 7.1 Same-origin communication

The Angular application calls relative paths such as:

```text
/api/v1/bookings
/api/v1/stations
/api/v1/operator/organizations
```

The browser sends the opaque session cookie automatically.

Angular must not:

- Read the session cookie
- Add bearer tokens
- Call internal service APIs
- Call the Identity Provider token endpoint
- Call the simulator
- Call databases or the broker

### 7.2 HTTP interceptors

Use functional interceptors for:

- CSRF header
- Correlation ID
- Locale header
- Safe request timeout
- Problem Details normalization
- Retry of explicitly safe reads
- Client-side timing metrics

Angular recommends functional HTTP interceptors because their ordering is more predictable in complex setups. ([angular.dev](https://angular.dev/guide/http/interceptors?utm_source=openai))

Interceptors must not retry state-changing requests automatically unless the original idempotency key is preserved and the operation explicitly permits retry.

### 7.3 CSRF

The BFF issues a non-secret session-bound CSRF token.

Angular sends it in a custom header for every mutation.

Failure handling:

- `401`: session-expired flow
- `403`: access-denied flow
- CSRF-specific rejection: clear unsafe pending state and reload session metadata
- Never silently resubmit a mutation after authentication

---

## 8. API clients

OpenAPI-generated clients are organized by API ownership:

- Account
- Discovery
- Booking and Session
- Station Operations
- Device Integration controls
- Governance and Support

Rules:

1. Generated code is not edited manually.
2. Feature repositories wrap generated clients.
3. Components consume feature-specific models.
4. Generated clients expose typed Problem Details.
5. Contract changes run compatibility checks.
6. Internal APIs are never generated into the browser application.
7. Unknown response fields are tolerated.
8. Unknown lifecycle values produce a safe unsupported-state presentation.

---

## 9. Client state model

### 9.1 Server-authoritative state

Never optimistically invent:

- Booking confirmation
- Allocation success
- Check-in success
- Charging start
- Charging stop
- Maintenance activation
- Account deletion completion
- Emergency intervention outcome

The UI may show a pending operation after durable server acceptance.

### 9.2 Feature store pattern

Each complex feature may expose:

- Read-only state Signals
- Computed presentation state
- Command methods
- Loading/error status
- Last successful refresh
- Server resource version
- Current workflow reference

### 9.3 State categories

| Category | Location |
|---|---|
| Authentication/session summary | Core session store |
| Active role/context | Core session/context store |
| Discovery filters | URL query parameters |
| Search results | Discovery feature store |
| Booking detail | Booking feature store |
| Form drafts | Component/feature state |
| Operator organization context | URL plus context store |
| Workflow polling state | Feature workflow store |
| Notifications/toasts | Shared transient store |
| Sensitive personal data | Memory only |

### 9.4 Browser persistence

Permitted:

- Language preference
- Non-sensitive display preferences
- Last selected public map/list mode

Prohibited:

- OAuth tokens
- Session identifiers readable by JavaScript
- Privacy export data
- Support-case personal data
- Start Authorization secrets
- Booking or session histories in local storage
- Privileged access grants

---

## 10. Routing strategy

Use Angular Router with lazy-loaded feature routes.

Canonical route prefix:

```text
/{locale}/...
```

Supported locale prefixes:

- `/el`
- `/en`

Technical route segments remain stable English terms.

Examples:

```text
/el/stations
/en/bookings/BKG-7K4M2P
/el/operator/organizations/ORG-123/stations
```

Angular Router is the official framework routing library and supports nested routes, route parameters, guards and lazy loading. ([angular.dev](https://angular.dev/guide/routing?utm_source=openai))

### 10.1 Route guards

Guards improve navigation UX for:

- Authentication
- Role-area selection
- Recent authentication
- Unsaved changes
- Active organization context

They are not security boundaries. Angular explicitly warns that client-side guards must never be the sole access control. ([angular.dev](https://angular.dev/guide/routing/route-guards?utm_source=openai))

### 10.2 Deep links

After authentication:

- Return only to an allowlisted internal path.
- Preserve safe query parameters.
- Never use an arbitrary external return URL.
- Revalidate route access after session restoration.

### 10.3 Route data loading

Use route resolvers selectively for:

- Booking detail
- Station management detail
- Support case
- Privacy workflow status

Do not block navigation on optional analytics or secondary panels.

---

# 11. Public screen catalogue

| ID | Route | Screen |
|---|---|---|
| PUB-01 | `/{locale}` | Landing and discovery entry |
| PUB-02 | `/{locale}/stations` | Station map/list search |
| PUB-03 | `/{locale}/stations/{stationRef}` | Station details |
| PUB-04 | `/{locale}/stations/{stationRef}/evses/{evseRef}` | EVSE details |
| PUB-05 | `/{locale}/sign-in` | BFF authentication entry |
| PUB-06 | `/{locale}/auth/result` | Authentication result/error |
| PUB-07 | `/{locale}/access-denied` | Safe access-denied screen |
| PUB-08 | `/{locale}/service-status` | User-safe degradation information |
| PUB-09 | `/{locale}/help` | Help and emergency guidance |

---

# 12. Driver screen catalogue

| ID | Route | Screen |
|---|---|---|
| DRV-01 | `/{locale}/account` | Driver dashboard |
| DRV-02 | `/{locale}/bookings/new` | Booking creation flow |
| DRV-03 | `/{locale}/bookings/upcoming` | Upcoming bookings |
| DRV-04 | `/{locale}/bookings/{bookingRef}` | Booking details |
| DRV-05 | `/{locale}/bookings/{bookingRef}/reschedule` | Reschedule flow |
| DRV-06 | `/{locale}/bookings/{bookingRef}/check-in` | Check-in flow |
| DRV-07 | `/{locale}/bookings/{bookingRef}/charging` | Live Session screen |
| DRV-08 | `/{locale}/history` | Booking and Session history |
| DRV-09 | `/{locale}/vehicles` | Saved vehicles |
| DRV-10 | `/{locale}/vehicles/{vehicleRef}` | Vehicle editor |
| DRV-11 | `/{locale}/notification-preferences` | Notification preferences |
| DRV-12 | `/{locale}/fault-reports/new` | Fault report |
| DRV-13 | `/{locale}/support-cases` | Driver support cases |
| DRV-14 | `/{locale}/support-cases/{caseRef}` | Support-case details |
| DRV-15 | `/{locale}/privacy` | Privacy centre |
| DRV-16 | `/{locale}/privacy/exports/{exportRef}` | Export status/download |
| DRV-17 | `/{locale}/privacy/deletion` | Account deletion workflow |
| DRV-18 | `/{locale}/account/security` | Session/security management |

---

# 13. Operator screen catalogue

Base:

```text
/{locale}/operator/organizations/{organizationRef}
```

| ID | Relative route | Screen |
|---|---|---|
| OPR-01 | `/dashboard` | Organization dashboard |
| OPR-02 | `/profile` | Organization profile |
| OPR-03 | `/staff` | Staff and invitations |
| OPR-04 | `/ownership-transfer` | Ownership transfer |
| OPR-05 | `/stations` | Station list |
| OPR-06 | `/stations/new` | Create Station |
| OPR-07 | `/stations/{stationRef}` | Station detail |
| OPR-08 | `/stations/{stationRef}/edit` | Station configuration |
| OPR-09 | `/stations/{stationRef}/evses` | EVSE management |
| OPR-10 | `/stations/{stationRef}/tariffs` | Tariff versions |
| OPR-11 | `/stations/{stationRef}/booking-policies` | Policy versions |
| OPR-12 | `/operations` | Operational EVSE dashboard |
| OPR-13 | `/maintenance` | Maintenance list/calendar |
| OPR-14 | `/maintenance/{maintenanceRef}` | Maintenance workflow |
| OPR-15 | `/faults` | Fault Incident queue |
| OPR-16 | `/faults/{faultRef}` | Fault Incident detail |
| OPR-17 | `/bookings` | Owned-station bookings |
| OPR-18 | `/bookings/{bookingRef}` | Minimized operator booking view |
| OPR-19 | `/sessions` | Active Sessions |
| OPR-20 | `/simulators` | Simulator assignments |
| OPR-21 | `/simulators/{stationRef}` | Simulator control/detail |
| OPR-22 | `/analytics` | Organization analytics |
| OPR-23 | `/report-exports` | Aggregated report exports |
| OPR-24 | `/audit` | Organization audit history |

Additional routes:

| ID | Route | Screen |
|---|---|---|
| OPR-25 | `/{locale}/operator/apply` | Operator application |
| OPR-26 | `/{locale}/operator/applications/{applicationRef}` | Application status |
| OPR-27 | `/{locale}/operator/select-organization` | Organization selector |

---

# 14. Platform screen catalogue

Base:

```text
/{locale}/platform
```

| ID | Relative route | Screen |
|---|---|---|
| PLT-01 | `/dashboard` | Platform operational dashboard |
| PLT-02 | `/operator-applications` | Application review queue |
| PLT-03 | `/operator-applications/{applicationRef}` | Application review |
| PLT-04 | `/support-cases` | Support-case queues |
| PLT-05 | `/support-cases/{caseRef}` | Case workspace |
| PLT-06 | `/investigations` | Investigation list |
| PLT-07 | `/investigations/{investigationRef}` | Investigation workspace |
| PLT-08 | `/accounts/{accountRef}` | Restricted account administration |
| PLT-09 | `/organizations/{organizationRef}` | Organization administration |
| PLT-10 | `/stations/{stationRef}/moderation` | Station moderation |
| PLT-11 | `/emergency-interventions` | Emergency workflow list |
| PLT-12 | `/emergency-interventions/{interventionRef}` | Emergency intervention |
| PLT-13 | `/break-glass` | Break-glass request/review |
| PLT-14 | `/privacy-requests` | Privacy review queue |
| PLT-15 | `/privacy-requests/{requestRef}` | Privacy review |
| PLT-16 | `/audit` | Central audit search |
| PLT-17 | `/security-events` | Security-event review |
| PLT-18 | `/reference-data` | Reference-data versions |
| PLT-19 | `/analytics` | Platform analytics |

---

## 15. Role-area visibility

| Role | Driver | Operator | Platform |
|---|---:|---:|---:|
| `DRIVER` | Yes | No | No |
| `OPERATOR_OWNER` | Yes | Yes | No |
| `OPERATOR_MANAGER` | Yes | Yes | No |
| `OPERATOR_TECHNICIAN` | Yes | Restricted | No |
| `OPERATOR_SUPPORT` | Yes | Restricted | No |
| `PLATFORM_ADMINISTRATOR` | Optional driver | No | Yes |
| `PLATFORM_SUPPORT` | Optional driver | No | Case-scoped |
| `AUDITOR_SECURITY_REVIEWER` | Optional driver | No | Read-only |

Navigation hiding is a usability feature only. Server authorization remains authoritative.

---

# 16. Discovery UX

## 16.1 Desktop

Use a split layout:

- Filter/search region
- Station result list
- Map
- Selected Station summary

The result list and map share one result set and selection state.

## 16.2 Mobile

Use:

- List as the default primary view
- Explicit map/list switch
- Bottom sheet or full-screen map detail
- Persistent filter summary
- No essential action available only through map markers

## 16.3 Accessible alternative

The map is supplementary.

Every map result must be available through a semantic list containing:

- Station name
- Address
- Distance where available
- Compatible EVSE count
- Availability result
- Freshness
- Price estimate summary
- Details action

WCAG 2.2 AA applies to every responsive variation of a page. Maps may require two-dimensional interaction, but equivalent information and functionality must remain available. ([w3.org](https://www.w3.org/TR/WCAG22/?utm_source=openai))

## 16.4 Map behaviour

Map technology remains provisional, with MapLibre preferred.

MapLibre GL JS is a TypeScript browser mapping library and provides keyboard pan, zoom and rotation handlers. ([maplibre.org](https://maplibre.org/maplibre-gl-js/docs/?utm_source=openai))

Requirements:

- Keyboard-accessible controls
- Visible focus
- Zoom controls
- “Search this area” action
- No automatic continuous search while panning
- Marker clustering
- List selection synchronized with map selection
- Map failure falls back to the list
- Reduced-motion support
- No hidden geolocation request

---

# 17. Booking creation UX

Use a guided sequence:

1. Select Station or begin from Station details.
2. Select date, start time and duration.
3. Select saved Vehicle or manual connector requirement.
4. Select automatic assignment or exact EVSE.
5. Review advisory availability.
6. Request Hold.
7. Display Hold countdown using server expiry.
8. Review assigned EVSE, tariff estimate and policy.
9. Confirm Booking.
10. Display authoritative confirmation.

Rules:

- The countdown is informational; the server expiry is final.
- Search results never imply capacity is reserved.
- Confirmation remains enabled only while the client believes the Hold is valid.
- Server expiry errors replace the review screen with recovery choices.
- Duplicate submission is prevented in the UI and protected by idempotency.
- Browser back navigation must not duplicate a Hold.
- Unsaved entered criteria may be restored, but expired authority may not.

---

# 18. Booking details UX

Display:

- Public Booking reference
- Lifecycle state
- Station and EVSE
- Charging interval
- Check-in window
- Tariff/policy snapshot
- Estimated cost label
- Operational warnings
- Session outcome
- Timeline
- Allowed actions returned by the server

The frontend must not independently infer that cancellation, check-in, start or stop is allowed.

Actions not returned by the server are:

- Hidden when irrelevant
- Disabled with explanation when understanding is useful
- Rechecked when invoked

---

# 19. Check-in and charging UX

## 19.1 Check-in

Flow:

1. Open Booking details or scan EVSE QR.
2. Authenticate if required.
3. Confirm Booking and EVSE.
4. Submit check-in.
5. Display `CHECKED_IN` and Start availability.

QR mismatch must show:

- Expected EVSE
- Scanned EVSE
- Safe corrective action
- No authorization secret

## 19.2 Start charging

After Start request:

- Display Session `STARTING`.
- Explain that the simulator is being contacted.
- Do not show energy flow yet.
- Poll authoritative status.
- Change to `CHARGING` only after physical start evidence.
- Show rejection or uncertainty distinctly.

## 19.3 Live Session

Display:

- Session state
- Elapsed duration
- Latest accepted energy
- Current power
- Last update time
- Freshness
- Estimated cost
- Stop action
- Fault/interruption warning

Telemetry is not authoritative final billing evidence.

## 19.4 Stop charging

After Stop request:

- Display `STOPPING`.
- Keep capacity/Session shown as active.
- Do not display completion until final evidence.
- If uncertain, show reconciliation status and support route.

Polling is the initial browser update mechanism.

Provisional intervals:

- `STARTING`/`STOPPING`: every 2 seconds for 30 seconds, then every 5 seconds
- `CHARGING`: every 5 seconds
- Background tab: reduced frequency
- Terminal state: stop polling

Server-provided `retryAfterSeconds` takes precedence.

---

# 20. Operator operational UX

The operational dashboard must display separately:

- Administrative state
- Device-reported state
- Derived availability
- Status freshness
- Maintenance
- Fault Incident
- Status Override
- Active Session
- Reconciliation state

Do not combine them into one colour or badge.

Suggested layout:

- Primary derived availability
- Secondary device state
- Administrative restriction
- Freshness timestamp
- Structured reason panel

Technician views omit unnecessary driver identity.

---

# 21. Maintenance UX

Flow:

1. Select Station/EVSE scope.
2. Enter interval and reason.
3. Request non-binding impact preview.
4. Display affected Bookings and Sessions.
5. Resolve required impacts.
6. Submit scheduling workflow.
7. Display capacity-block installation progress.
8. Confirm `SCHEDULED` only after workflow completion.
9. Track activation and completion.

The preview must be labelled:

> “Preview only — impacts may change before scheduling.”

Emergency maintenance uses a separate elevated workflow and cannot be disguised as ordinary scheduling.

---

# 22. Platform Support UX

The Support Case is the workspace boundary.

The case screen displays:

- Case summary
- Assignment
- Related resource references
- Masked user/operator data
- Permitted actions
- Timeline
- Escalation controls
- Temporary access expiry

Reveal flow:

1. Select masked field.
2. Provide structured reason.
3. Confirm reveal.
4. Show field for the permitted period.
5. Record visible access-expiry information.

Support must never have a global user-search screen that enables unrestricted browsing.

---

# 23. Workflow-status UX

Long-running workflows use a consistent component.

States:

- Requested
- In progress
- Waiting for action
- Completed
- Failed
- Requires review
- Cancelled

Component displays:

- Current step
- Submitted/updated time
- User action required
- Retry availability
- Safe failure reason
- Correlation reference
- Result link

A workflow timing out in the browser does not mean it failed on the server.

---

# 24. Error handling

## 24.1 Problem Details mapping

Map stable problem codes to:

- Inline field error
- Form-level error
- Conflict panel
- Access-denied page
- Not-found page
- Dependency warning
- Retryable operation banner
- Uncertain-outcome panel

Do not display raw backend `detail` without controlled presentation review.

## 24.2 Common conflict handling

| Problem | UX |
|---|---|
| `EVSE_ALLOCATION_CONFLICT` | Return to EVSE/time selection |
| `BOOKING_HOLD_EXPIRED` | Explain expiry and offer a new search |
| `VERSION_CONFLICT` | Reload current data and compare changes |
| `STATUS_UNKNOWN` | Explain why a positive decision is unsafe |
| `COMMAND_OUTCOME_UNCERTAIN` | Show reconciliation, not failure |
| `CASE_SCOPE_REQUIRED` | Remove sensitive view and return to case queue |
| `ACCOUNT_NOT_ACTIVE` | Explain restriction and available support route |

## 24.3 Correlation reference

Unexpected errors display a safe support reference, never a stack trace.

---

# 25. Forms

Use strictly typed reactive forms for business forms.

Angular describes reactive forms as explicit, scalable and testable, with strictly typed forms supported by default. ([angular.dev](https://angular.dev/guide/forms/reactive-forms?utm_source=openai))

Requirements:

- Visible labels
- Help text associated programmatically
- Inline and summary validation
- Server validation mapped to fields
- Errors not conveyed by colour alone
- Focus moved to error summary after failed submission
- Unsaved-change protection
- Loading and disabled states remain distinguishable
- Destructive actions require explicit confirmation
- Date/time entry shows Station timezone
- UTC is never shown to ordinary users unless diagnostically necessary

Client validation improves UX but never replaces server validation.

---

# 26. Localization

Use Angular internationalization support with `@angular/localize`.

Angular supports extraction of translatable text and locale-specific formatting. ([angular.dev](https://angular.dev/guide/i18n/add-package?utm_source=openai))

Initial locales:

- `el-GR`
- `en`

Rules:

1. Greek is the default deployment locale.
2. Locale prefix is present in routes.
3. Language switching preserves the equivalent route.
4. Stable custom translation IDs are used.
5. Reason codes are translated separately from logic.
6. API enum values are never displayed directly.
7. Dates use Station timezone.
8. Currency uses EUR locale formatting.
9. Distances use kilometres.
10. Translation files are reviewed as controlled artifacts.
11. Missing translation fails CI for production builds.
12. User-generated text is not automatically translated.

---

# 27. Accessibility

Target: WCAG 2.2 AA. W3C recommends WCAG 2.2 for current accessibility work and requires Level A and AA criteria for AA conformance. ([w3.org](https://www.w3.org/TR/WCAG22/?utm_source=openai))

Required:

- Semantic HTML
- Logical heading order
- Skip links
- Keyboard-complete navigation
- Visible focus
- Accessible names
- Sufficient text and non-text contrast
- Reflow at narrow widths and high zoom
- Target-size compliance
- Status not communicated by colour alone
- Screen-reader announcements for meaningful async changes
- Reduced-motion support
- Form error summaries
- Accessible modal focus management
- Table alternatives where card layouts obscure relationships
- No keyboard traps
- No time limit based solely on client countdown
- Session/Booking state text accompanying icons

Automated checks do not replace manual keyboard and assistive-technology testing.

---

# 28. Design system

Create a small application design system containing:

- Typography
- Spacing
- Colour and semantic status tokens
- Buttons
- Links
- Inputs
- Selects
- Date/time controls
- Dialogs
- Drawers
- Banners
- Toasts
- Tabs
- Tables
- Pagination
- Status badges
- Timeline
- Workflow progress
- Empty/loading/error states

Rules:

- Domain-specific components stay in features.
- Shared components expose accessible behaviour.
- Visual status tokens map to semantic meaning.
- Component APIs avoid raw HTML injection.
- A third-party component library is selected only after accessibility, bundle and maintenance evaluation.

---

# 29. Responsive behaviour

Layout categories are design tokens rather than business logic.

Required layouts:

- Small phone
- Large phone/tablet
- Desktop
- Wide operational dashboard

Principles:

- Primary actions remain reachable without horizontal scrolling.
- Tables transform into cards only when relationships remain understandable.
- Operational dashboards allow horizontal table scrolling where semantically necessary.
- Map never displaces the list entirely.
- Dialogs become full-screen sheets on small devices where appropriate.
- Sticky actions must not hide focused content.
- All responsive variants meet accessibility requirements.

---

# 30. Security-sensitive UX

1. Do not display access tokens or secrets.
2. Do not expose internal IDs.
3. Confirmation dialogs name the target and consequence.
4. High-impact actions require recent authentication.
5. Privileged actions require a reason.
6. Emergency actions use distinct visual treatment.
7. Break-glass access displays remaining time and scope.
8. Another user’s protected resource returns a generic not-found experience.
9. Logout invalidates the BFF session.
10. Session expiry clears sensitive in-memory state.
11. Return URLs are internal and allowlisted.
12. Privacy downloads are never embedded in email.
13. Copy-to-clipboard is disabled for unnecessary sensitive values.
14. Browser autocomplete is configured according to field meaning.
15. Route guards never substitute for backend authorization.

Angular treats template-bound values as untrusted and recommends CSP and Trusted Types as additional XSS protections. Direct DOM manipulation and security-trust bypasses require explicit review. ([angular.dev](https://angular.dev/best-practices/security?utm_source=openai))

---

# 31. Performance

Required frontend strategies:

- Lazy-load role and feature areas
- Dynamically load map code only on map-capable screens
- Avoid loading Operator/Platform code for public users
- Use responsive images
- Paginate lists
- Virtualize only measured large collections
- Debounce text search
- Require explicit “Search this area” for map movement
- Cancel obsolete reads
- Avoid repeated polling after terminal state
- Use server caching headers for public projections
- Set bundle-size budgets in CI

Provisional budgets:

| Asset | Budget |
|---|---:|
| Initial application JavaScript, compressed | 300 KiB |
| Initial application CSS, compressed | 75 KiB |
| Ordinary lazy feature chunk, compressed | 200 KiB |
| Map feature chunk | Measured separately |

Budget exceptions require review.

---

# 32. Browser degradation

## Search unavailable

- Preserve navigation to known Booking/history routes.
- Display service warning.
- Do not fabricate cached availability.

## Analytics unavailable

- Operator configuration and operations remain usable.
- Show freshness/error state.

## Notification delivery problem

- Display in-app warning.
- Keep committed Booking valid.

## Device updates unavailable

- Show stale or unknown.
- Do not present charging as stopped or started without evidence.

## Network offline

- Display offline banner.
- Disable mutations.
- Do not queue Booking, check-in, start, stop or privileged actions locally.
- Restore safe read state after reconnection.

---

# 33. Frontend observability

Capture:

- Route navigation timing
- API timing by operation ID
- Problem code counts
- JavaScript errors
- Failed lazy-chunk loads
- Session-expiry events
- Workflow age
- Polling errors
- Map initialization failures
- Accessibility telemetry only where privacy-safe

Do not capture:

- Form contents
- Email addresses
- Booking location history beyond necessary operational context
- Tokens/cookies
- Privacy export content
- Support reveal values

Client telemetry includes correlation and trace references where provided.

---

# 34. Testing strategy

## 34.1 Unit tests

Test:

- Stores
- Computed state
- Formatters
- Validation
- Problem mapping
- Route utilities
- Permission-aware presentation

Angular’s current default testing setup uses Vitest, but the final test runner remains subject to technology selection. Angular also recommends testing component classes together with their rendered DOM behaviour. ([angular.dev](https://angular.dev/guide/testing/components-basics?utm_source=openai))

## 34.2 Component tests

Required for:

- Forms
- Status displays
- Workflow components
- Map/list synchronization
- Dialog focus
- Error summaries
- Responsive navigation
- Role-area menus

## 34.3 Contract tests

- Generated clients compile against approved OpenAPI.
- Example responses render safely.
- Unknown optional fields are tolerated.
- Unknown enum values produce safe fallback.
- Problem codes map correctly.

## 34.4 End-to-end tests

Critical journeys:

- Public search
- Registration/sign-in return
- Booking creation and expiry
- Rescheduling and cancellation
- Check-in and Start
- Start rejection and uncertainty
- Stop and Session completion
- Operator maintenance workflow
- Cross-organization denial
- Support case access/reveal
- Privacy export and deletion
- Session expiry and CSRF rejection
- Greek and English routes

## 34.5 Accessibility tests

- Automated rule checks
- Keyboard-only
- Screen reader
- 200% and 400% zoom
- Reflow
- Contrast
- Reduced motion
- Focus order
- Error recovery
- Map/list equivalence

---

# 35. Traceability

| Frontend area | Requirements |
|---|---|
| Public discovery | FR-DIS-01–03, FR-AVL-01/02 |
| Driver account and vehicles | FR-IAM-01, FR-IAM-04/05 |
| Booking | FR-BKG-01–07, FR-AVL-03 |
| Charging | FR-CHG-01–04 |
| History | FR-HIS-01 |
| Fault reporting | FR-FLT-01 |
| Operator | FR-OPS-01–05 |
| Platform | FR-ADM-01–03, FR-SUP-01/02 |
| Simulator control | FR-SIM-01–03 |
| Notifications | FR-NOT-02/03 |
| Privacy | FR-PRV-01–04 |
| Audit views | FR-AUD-01/02 |
| Accessibility/security | NFR accessibility/security, ARC-007 |

---

# 36. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-FE-01 | Use one responsive Angular application with role-specific shells. |
| ARC-FE-02 | Use client-side rendering for v1. |
| ARC-FE-03 | Do not implement micro-frontends. |
| ARC-FE-04 | Use standalone components and route-based lazy loading. |
| ARC-FE-05 | Use Signals for local synchronous state and RxJS for asynchronous streams. |
| ARC-FE-06 | Do not introduce a global Redux-style store initially. |
| ARC-FE-07 | Use feature stores and repository/facade boundaries. |
| ARC-FE-08 | Keep search state in URL query parameters. |
| ARC-FE-09 | Use OpenAPI-generated clients behind adapters. |
| ARC-FE-10 | Use the same-origin BFF exclusively for browser APIs. |
| ARC-FE-11 | Never store OAuth tokens in browser-accessible storage. |
| ARC-FE-12 | Use functional HTTP interceptors. |
| ARC-FE-13 | Treat route guards as UX controls, not authorization. |
| ARC-FE-14 | Use typed reactive forms for business forms. |
| ARC-FE-15 | Use Greek and English locale-prefixed routes. |
| ARC-FE-16 | Use Station timezone for booking displays and UTC for API instants. |
| ARC-FE-17 | Make list discovery fully equivalent to map discovery. |
| ARC-FE-18 | Use polling rather than browser push for live Session state in v1. |
| ARC-FE-19 | Do not queue state-changing actions offline. |
| ARC-FE-20 | Target WCAG 2.2 AA for every responsive variant. |
| ARC-FE-21 | Display separate administrative, device and derived states. |
| ARC-FE-22 | Render only server-returned permitted Booking actions. |
| ARC-FE-23 | Treat workflow timeout as pending/uncertain rather than failed. |
| ARC-FE-24 | Defer SSR and PWA functionality until justified by measured requirements. |
| ARC-FE-25 | Require accessibility and security review for shared UI components. |

---

# 37. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-FE-OQ-01 | Final Angular major version | Technology selection |
| ARC-FE-OQ-02 | Final component/design-system library | Technology selection |
| ARC-FE-OQ-03 | Final map and tile providers | Technology/cloud selection |
| ARC-FE-OQ-04 | Final charting library | Technology selection |
| ARC-FE-OQ-05 | Final BFF session-store implementation | Technology selection |
| ARC-FE-OQ-06 | Final generated-client tool | Technology selection |
| ARC-FE-OQ-07 | Final unit/component/E2E test tools | Testing strategy |
| ARC-FE-OQ-08 | Whether Session polling should later use SSE | Performance testing |
| ARC-FE-OQ-09 | Final breakpoint and spacing tokens | UX design |
| ARC-FE-OQ-10 | Final CSP requirements for map workers | Technology/security |
| ARC-FE-OQ-11 | Translation review ownership | Delivery planning |
| ARC-FE-OQ-12 | Whether public SSR is justified | Post-MVP evaluation |
| ARC-FE-OQ-13 | Final frontend performance budgets | Performance testing |
| ARC-FE-OQ-14 | Whether Operator dashboards need table virtualization | Load/UX testing |

---

# 38. Acceptance criteria

This specification is approved when:

1. Every human use case maps to a screen or explicit background interaction.
2. Public, Driver, Operator and Platform areas are structurally separated.
3. Browser code communicates only with the BFF.
4. OAuth tokens remain outside browser JavaScript.
5. Route protection does not replace server authorization.
6. Booking actions are server-derived.
7. Search and analytics are marked as projections.
8. Charging starts only after physical evidence.
9. Uncertain outcomes remain visibly uncertain.
10. Administrative, reported and derived states remain distinct.
11. Map functionality has a complete list equivalent.
12. Greek and English are supported consistently.
13. Business forms are typed and accessible.
14. Personal data is not persisted unnecessarily in the browser.
15. Offline mutations are prohibited.
16. Workflow and Problem Details handling is consistent.
17. Every responsive page targets WCAG 2.2 AA.
18. Feature boundaries prevent accidental frontend coupling.
19. Generated API clients remain behind adapters.
20. Critical journeys have planned automated and manual tests.

---

# 39. Consequences

## Positive

- One maintainable frontend deployment
- Strong browser-token protection
- Clear role separation
- Accessible map/list discovery
- Honest workflow and device-status presentation
- Reduced global-state complexity
- Strong REST contract alignment
- Feature-level lazy loading
- Consistent Greek/English UX

## Negative

- The BFF is required for every authenticated browser operation.
- Locale-prefixed builds/routes add deployment complexity.
- Polling adds repeated API traffic.
- Accessibility requirements increase component/testing effort.
- Role-aware navigation produces a large route catalogue.
- Adapters add code between generated clients and components.
- No offline mutation support limits disconnected usage.

These costs are accepted to preserve security, correctness and individual-project feasibility.

---

# 40. Next architecture artifact

The next document is:

**Final Technology Selection and Architecture Decision Record Set v1.0**

It must finalize:

- Angular and Node versions
- Java and Spring Boot versions
- Identity Provider
- PostgreSQL version
- Message broker
- Migration tooling
- BFF implementation
- Session store
- OpenAPI and AsyncAPI tooling
- Map and tile providers
- Component and charting libraries
- Testing tools
- Observability stack
- Container/runtime baseline
- Dependency-support and upgrade policy
