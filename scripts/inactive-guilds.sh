#!/usr/bin/env bash
# Collect IDs of active guilds inactive longer than a given interval — the
# de-register candidates fed into the nREPL "leave guild" flow.
#
# Read-only: runs a single SELECT, never writes.
#
# Connection details are taken from the environment so nothing about the
# deployment lives in this (open-source) repo. Set these first, e.g. in a local
# untracked .envrc:
#   PAWA_DB_SSH       SSH host running the database container
#   PAWA_DB_CONTAINER Postgres container name
#   PAWA_DB_ENV_FILE  path on that host to the env file exporting DB_USER/DB_NAME
#
# Usage: ./scripts/inactive-guilds.sh [interval]
#   interval: a Postgres interval, default '3 months' (pass trusted values only)
#
# Refresh the list:  ./scripts/inactive-guilds.sh > inactive-guilds.txt

set -euo pipefail

INTERVAL="${1:-3 months}"
: "${PAWA_DB_SSH:?set PAWA_DB_SSH (see header)}"
: "${PAWA_DB_CONTAINER:?set PAWA_DB_CONTAINER (see header)}"
: "${PAWA_DB_ENV_FILE:?set PAWA_DB_ENV_FILE (see header)}"

ssh "$PAWA_DB_SSH" \
  "set -a; . '$PAWA_DB_ENV_FILE'; docker exec -i '$PAWA_DB_CONTAINER' psql -U \"\$DB_USER\" -d \"\$DB_NAME\" -At" <<SQL
SELECT id
FROM guilds
WHERE active
  AND last_active_on < now() - interval '${INTERVAL}'
ORDER BY last_active_on;
SQL
