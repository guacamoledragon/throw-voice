#!/usr/bin/env bash
# Apply Postgres DB migrations
# Usage: ./scripts/migrate.sh [password] [port]
#   password: default 'password'
#   port:     default '5432'

set -euo pipefail

PASSWORD="${1:-password}"
PORT="${2:-5432}"

docker run --rm \
  --network host \
  -v "$PWD/sql:/flyway/sql" \
  -v "$PWD/conf:/flyway/conf" \
  -v "$PWD/data:/flyway/data" \
  flyway/flyway:10.11-alpine \
  -user=postgres -password="$PASSWORD" \
  -url="jdbc:postgresql://localhost:$PORT/settings" \
  migrate
