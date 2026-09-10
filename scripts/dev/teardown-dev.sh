#!/usr/bin/env bash
# Stop the local core profile. Volumes are preserved; add --volumes to purge.
set -euo pipefail
cd "$(dirname "$0")/../.."
docker compose -f infra/local/compose.yaml down "$@"
