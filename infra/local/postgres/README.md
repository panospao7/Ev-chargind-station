# PostgreSQL initialization (I1-DAT-001)

`01-provision.sh` runs via `/docker-entrypoint-initdb.d` on FIRST volume
initialization only. Per ENG-001 doc §6.3 and ARC-022 §4 it creates:

- nine logical databases (`account_db`, `station_operations_db`,
  `booking_session_db`, `device_integration_db`, `discovery_insights_db`,
  `notification_db`, `governance_support_db`, `bff_session_db`,
  `keycloak_db`);
- per-database `owner` / `migrator` / `runtime` LOGIN roles;
- hardened grants: `CONNECT` revoked from `PUBLIC` (cross-service access
  fails and is tested), migrator holds database-level `CREATE` for Flyway.

The service schemas themselves are created by each service's `V1__baseline.sql`
(Flyway, run by the migrator role). Keycloak's own wiring to `keycloak_db`
stays with the identity delivery task. Business tables are never created
here (ARC-022 §5–§9 belong to owning services).
