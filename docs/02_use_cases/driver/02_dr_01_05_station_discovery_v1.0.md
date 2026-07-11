Document ID: UC-DR-002
Title: DR-01-05 - Station Discovery v1.0
Version: 1.0
Status: APPROVED
Owner: PO/FA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: UC-DR-001
Authoritative for: Discovery and Map Search Rules

---

# DR-01-05 - Station Discovery v1.0

## DR-01–05 — Station Discovery v1.0

### Public capabilities

Drivers may browse without authentication:

- Map and list views
- Search by city, address, postcode, or map area
- View station and EVSE details
- Inspect indicative availability
- View tariffs and operating hours

Authentication is required only when creating a booking.

### Search inputs

- Geographic area or map bounds
- Requested date, start time, and duration
- Vehicle or connector type
- Minimum charging power
- Availability
- Price range
- Operator
- Access type: public, restricted, or private
- Operational status
- Optional amenities

### Search results

Each station summary displays:

- Name, operator, and approximate location
- Distance when location permission is granted
- Supported connector types
- Maximum charging power
- Number of compatible/bookable EVSEs
- Starting tariff
- Opening status
- Operational-status freshness
- Availability for the requested interval

Map markers should use clustering when many stations are visible.

### Station details

- Address, coordinates, directions, and opening hours
- Operator information
- Access instructions
- Amenities
- EVSE and connector inventory
- Charging power and compatibility
- Tariff components
- Maintenance or operational warnings
- Availability calendar
- Last status-update time

Internal hardware identifiers and operationally sensitive data must not be public.

### Behaviour and rules

- Availability is evaluated using the approved calculation model.
- Results are advisory and revalidated during booking.
- Search parameters remain encoded in the URL so results can be shared or restored.
- Pagination is used for list results.
- Map searches use the visible geographic bounds.
- User location requires explicit browser permission.
- Exact location history is not retained by default.
- Empty results provide suggestions rather than appearing as errors.
- Dates are displayed in `Europe/Athens`.
- Accessibility must be available through the list view; the map cannot be the only discovery mechanism.

### Acceptance criteria

- Public users can browse without account creation.
- Filters produce compatible results.
- Incompatible EVSEs are not presented as bookable.
- Stale status is clearly marked.
- Map and list represent the same search criteria.
- Failure of live status does not crash station browsing.
- Search remains usable without location permission.
- Search performs efficiently across the simulated Greek dataset.

