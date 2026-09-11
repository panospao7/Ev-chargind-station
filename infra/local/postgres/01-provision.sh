#!/usr/bin/env bash
# =============================================================================
# Local PostgreSQL provisioning (I1-DAT-001) — runs via docker-entrypoint
# initdb.d on FIRST volume initialization only.
#
# Topology per ARC-022 §4 and ENG-001 doc §6.3: nine logical databases, each
# with owner/migrator/runtime LOGIN roles. The local container is shared
# infrastructure, NOT shared ownership (§6.3); no business aggregates are
# inserted here.
#
# All passwords are DEVELOPMENT-ONLY (documented in .env.example) and are
# never replaced with real credentials locally (ENG-001 doc §3).
# =============================================================================
set -euo pipefail

# The entrypoint always exposes POSTGRES_DB (the database it initialized);
# compose pins it to the bootstrap name. Provisioning scripts must use the
# entrypoint-provided name so they behave identically under Docker Compose
# and Testcontainers (which manages POSTGRES_DB itself).
BOOT_DB="${POSTGRES_BOOTSTRAP_DB:-${POSTGRES_DB:?POSTGRES_DB must be set by the entrypoint}}"
ROLE_PW="${PLATFORM_ROLE_PASSWORD:-evplatform_dev_only}"

# No business data in psql output; keep logs terse.
PSQLOPTS=(-v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$BOOT_DB" -q)

DATABASES=(
  account_db
  station_operations_db
  booking_session_db
  device_integration_db
  discovery_insights_db
  notification_db
  governance_support_db
  bff_session_db
  keycloak_db
)

# --- 1. cluster-level LOGIN roles (idempotent) -------------------------------
for db in "${DATABASES[@]}"; do
  base="${db%_db}"
  for kind in owner migrator runtime; do
    role="${base}_${kind}"
    exists=$(psql "${PSQLOPTS[@]}" -tAc "SELECT 1 FROM pg_roles WHERE rolname='$role'")
    if [ "$exists" != "1" ]; then
      psql "${PSQLOPTS[@]}" -c "CREATE ROLE \"${role}\" LOGIN PASSWORD '${ROLE_PW}';"
      echo "provisioned role: ${role}"
    fi
  done
done

# --- 2. databases owned by their owner role ---------------------------------
for db in "${DATABASES[@]}"; do
  base="${db%_db}"
  exists=$(psql "${PSQLOPTS[@]}" -tAc "SELECT 1 FROM pg_database WHERE datname='$db'")
  if [ "$exists" != "1" ]; then
    psql "${PSQLOPTS[@]}" -c "CREATE DATABASE \"${db}\" OWNER \"${base}_owner\";"
    echo "provisioned database: ${db}"
  fi
done

# --- 3. per-database hardening + role grants --------------------------------
# CONNECT is revoked from PUBLIC so cross-service access actually fails and
# is testable (I1-DAT-001 AC-02). The migrator receives database-level CREATE
# so its Flyway runs can create the service schema (V1 baselines).
for db in "${DATABASES[@]}"; do
  base="${db%_db}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" -q <<SQL
REVOKE ALL ON DATABASE "${db}" FROM PUBLIC;
GRANT CONNECT ON DATABASE "${db}" TO "${base}_owner", "${base}_migrator", "${base}_runtime";
GRANT CREATE ON DATABASE "${db}" TO "${base}_migrator";
SQL
  echo "grants applied: ${db}"
done

echo "provisioning complete: ${#DATABASES[@]} databases"
