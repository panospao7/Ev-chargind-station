# Database scripts (I1-DAT-001)

## Migration pipeline

Local migrations run through Flyway, SQL-first, one location per service:

```text
services/<service>/src/main/resources/db/migration/V<N>__<name>.sql
```

Developer flow (ENG-001 doc §8.2 semantics):

1. `scripts/dev/bootstrap-dev.sh` — starts the core profile; the PostgreSQL
   container provisions databases and roles on FIRST init via
   `infra/local/postgres/01-provision.sh`.
2. Service migrations run with Flyway (programmatic in tests; Spring Boot
   integration arrives with each service's own delivery task) using the
   service's `<db>_migrator` role. The runtime role gets DML-only grants via
   default privileges in each V1.
3. `recreate` (destructive, local only): `docker compose -f
   infra/local/compose.yaml down -v && scripts/dev/bootstrap-dev.sh` — wipes
   the local volume and re-provisions. Never against shared environments
   (applied migrations are immutable; AGENTS.md §10).

## Retention-job framework (skeleton, W3 enforcement)

`retention-job-registry.template.sql` is the registration/execution
contract. It is deliberately NOT applied: retention enforcement is W3
(GOV-007 §5) and no deletion behavior exists. Owning services apply this
table via their own forward migration when they first schedule jobs.

## Backup compatibility

The local topology uses one named volume per stateful service
(`postgres-data`, `rabbitmq-data`) so `pg_dump`/`pg_dumpall` against
`127.0.0.1:5432` captures the full local cluster; no local state lives
outside volumes. Production backup policy is an operations-task concern
(ARC-015 EPIC-05/23) and is not defined here.
