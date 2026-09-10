# Local image manifest (runway core profile)

Pinned by digest per ENG-001 doc §3 (mutable `latest` tags prohibited).
Recorded at implementation time 2026-09-10; re-verify digests when bumping.

| Service | Reference (tag @ digest) | Purpose |
|---|---|---|
| PostgreSQL | `postgres:18@sha256:4ef4dbc939d61acea57712655ddb4b4ab27419c913f94cca0cd57cb3ea3c2280` | Local logical service databases |
| RabbitMQ | `rabbitmq:4.3-management@sha256:57bddb6fbc3498b5d8b5a14dc6f4506073ebcf94c66ba2a7678c335faa8dd631` | Local broker + management UI |
| Keycloak | `keycloak/keycloak:26.6@sha256:0aae0de7fca85525f727d3354df17896092de8bb26ae4c12d89c77e5df8cbce4` | Local identity provider (start-dev, H2) |
| Mailpit | `axllent/mailpit:latest@sha256:98b916bd3c8d61f7633a52d3ea2f58d00620cb01ca57ab59edde68c347a95365` | Local SMTP catcher (PROVISIONAL per ENG-001 doc §3) |

Digests were resolved from the Docker Hub registry API at implementation
time (manifest-list digests, multi-arch).
