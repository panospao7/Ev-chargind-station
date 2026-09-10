# PostgreSQL initialization (I1-DAT-001 scope)

Files here mount as `/docker-entrypoint-initdb.d` (first-boot only).

Per ENG-001 doc §6.3, initialization may create databases and roles — the
per-service databases, owner/migrator/runtime roles and V1 Flyway baselines
are owned by delivery task **I1-DAT-001**. Intentionally empty on the runway.
