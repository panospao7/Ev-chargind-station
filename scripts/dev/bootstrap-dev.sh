#!/usr/bin/env bash
# Start the local core profile and wait for health (ENG-001 doc §8.1).
set -euo pipefail
cd "$(dirname "$0")/../.."
docker compose -f infra/local/compose.yaml up -d --wait
docker compose -f infra/local/compose.yaml ps
echo
echo "Core profile is healthy:"
echo "  PostgreSQL  http://127.0.0.1:5432"
echo "  RabbitMQ    http://127.0.0.1:15672"
echo "  Keycloak    http://127.0.0.1:8180 (health: :9001/health)"
echo "  Mailpit     http://127.0.0.1:8025"
